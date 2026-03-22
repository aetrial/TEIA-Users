package com.teia.users.controllers;

import com.teia.users.client.TeiaCoreClient;
import com.teia.users.dto.OutboundCallRequest;
import com.teia.users.dto.TeiaCoreForwardRequest;
import com.teia.users.dto.TwilioCallWebhookRequest;
import com.teia.users.dto.TwilioMessageWebhookRequest;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.UsageEventType;
import com.teia.users.repositories.UserRepository;
import com.teia.users.services.SessionTokenService;
import com.teia.users.services.TwilioWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for receiving Twilio webhooks.
 * All Twilio webhooks are routed through TEIA-users for credit checking and authorization.
 */
@RestController
@RequestMapping("/twilio")
@Tag(name = "Twilio Webhooks", description = "Endpoints for receiving Twilio call and message webhooks")
public class TwilioWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookController.class);

    // TwiML response for service unavailable (zero credits)
    private static final String TWIML_SERVICE_UNAVAILABLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say>Service unavailable. Please contact support.</Say>
            </Response>
            """;

    // TwiML response for unknown number
    private static final String TWIML_UNKNOWN_NUMBER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say>This number is not configured. Please contact support.</Say>
            </Response>
            """;

    private final TwilioWebhookService webhookService;
    private final SessionTokenService sessionTokenService;
    private final TeiaCoreClient teiaCoreClient;
    private final UserRepository userRepository;

    @Value("${teia.core.ws-base-url:wss://localhost:8000}")
    private String teiaCoreWsBaseUrl;

    @Value("${teia.core.base-url:http://localhost:8000}")
    private String teiaCoreBaseUrl;

    public TwilioWebhookController(
            TwilioWebhookService webhookService,
            SessionTokenService sessionTokenService,
            TeiaCoreClient teiaCoreClient,
            UserRepository userRepository) {
        this.webhookService = webhookService;
        this.sessionTokenService = sessionTokenService;
        this.teiaCoreClient = teiaCoreClient;
        this.userRepository = userRepository;
    }

    /**
     * Receives inbound call webhooks from Twilio.
     * Returns TwiML that connects to TEIA Core WebSocket for audio streaming.
     */
    @PostMapping(value = "/inbound/call", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(
            summary = "Receive inbound call webhook from Twilio",
            description = "Twilio sends this webhook when an inbound call is received. " +
                    "Returns TwiML that connects the call to TEIA Core WebSocket for AI processing. " +
                    "Requires the client (identified by the 'To' number) to have sufficient credit balance. " +
                    "When signature validation is enabled, requires valid X-Twilio-Signature header."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "TwiML response - either stream connection or error message",
                    content = @Content(
                            mediaType = "application/xml",
                            examples = {
                                    @ExampleObject(
                                            name = "Success",
                                            value = """
                                                    <?xml version="1.0" encoding="UTF-8"?>
                                                    <Response>
                                                        <Connect>
                                                            <Stream url="wss://teia-core.example.com/media-stream">
                                                                <Parameter name="session_token" value="..."/>
                                                                <Parameter name="client_id" value="123"/>
                                                                <Parameter name="session_id" value="sess_abc123"/>
                                                            </Stream>
                                                        </Connect>
                                                    </Response>"""
                                    ),
                                    @ExampleObject(
                                            name = "Insufficient Credits",
                                            value = """
                                                    <?xml version="1.0" encoding="UTF-8"?>
                                                    <Response>
                                                        <Say>Service unavailable. Please contact support.</Say>
                                                    </Response>"""
                                    ),
                                    @ExampleObject(
                                            name = "Unknown Number",
                                            value = """
                                                    <?xml version="1.0" encoding="UTF-8"?>
                                                    <Response>
                                                        <Say>This number is not configured. Please contact support.</Say>
                                                    </Response>"""
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or missing Twilio signature (when validation enabled)",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<String> handleInboundCall(
            @Parameter(description = "Twilio call webhook parameters", hidden = true)
            @ModelAttribute TwilioCallWebhookRequest request) {
        log.info("[TwilioWebhook] Inbound call received: from={}, to={}, callSid={}",
                request.getFrom(), request.getTo(), request.getCallSid());

        // Look up client by the "To" number (the Twilio number receiving the call)
        Optional<TeiaUser> clientOpt = webhookService.findClientByCallNumber(request.getTo());

        if (clientOpt.isEmpty()) {
            log.warn("[TwilioWebhook] No client found for number: {}", request.getTo());
            return ResponseEntity.ok(TWIML_UNKNOWN_NUMBER);
        }

        TeiaUser client = clientOpt.get();

        // Check credit balance
        if (!webhookService.hasCredits(client, UsageEventType.CALL)) {
            log.warn("[TwilioWebhook] Client {} has insufficient credits", client.getId());
            return ResponseEntity.ok(TWIML_SERVICE_UNAVAILABLE);
        }

        // Reserve credits and create session
        UsageEvent session = webhookService.reserveCredits(client, UsageEventType.CALL);
        String sessionToken = sessionTokenService.generateToken(client.getId(), session.getSessionId());

        log.info("[TwilioWebhook] Session created: sessionId={}, clientId={}", session.getSessionId(), client.getId());

        // Generate TwiML with WebSocket stream pointing to TEIA Core
        String twiml = generateStreamTwiml(client.getId(), session.getSessionId(), sessionToken);
        return ResponseEntity.ok(twiml);
    }

    /**
     * Receives inbound message webhooks from Twilio (SMS/WhatsApp).
     * Forwards to TEIA Core via HTTP with signed headers.
     */
    @PostMapping(value = "/inbound/message", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(
            summary = "Receive inbound message webhook from Twilio",
            description = "Twilio sends this webhook when an inbound SMS or WhatsApp message is received. " +
                    "The message is forwarded to TEIA Core for AI processing. " +
                    "Supports both SMS and WhatsApp (with 'whatsapp:' prefix on numbers). " +
                    "Requires sufficient credit balance. Returns TwiML response from TEIA Core."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "TwiML response from TEIA Core or empty response",
                    content = @Content(
                            mediaType = "application/xml",
                            examples = @ExampleObject(
                                    value = """
                                            <?xml version="1.0" encoding="UTF-8"?>
                                            <Response>
                                                <Message>Thank you for your message. We will respond shortly.</Message>
                                            </Response>"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or missing Twilio signature (when validation enabled)",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<String> handleInboundMessage(
            @Parameter(description = "Twilio message webhook parameters", hidden = true)
            @ModelAttribute TwilioMessageWebhookRequest request) {
        String to = request.getTo();
        // Remove whatsapp: prefix if present
        String normalizedTo = to != null ? to.replace("whatsapp:", "") : null;

        log.info("[TwilioWebhook] Inbound message received: from={}, to={}, messageSid={}",
                request.getFrom(), to, request.getMessageSid());

        // Look up client - check both WhatsApp and call numbers
        Optional<TeiaUser> clientOpt = webhookService.findClientByWhatsappNumber(normalizedTo);
        if (clientOpt.isEmpty()) {
            clientOpt = webhookService.findClientByCallNumber(normalizedTo);
        }

        if (clientOpt.isEmpty()) {
            log.warn("[TwilioWebhook] No client found for number: {}", to);
            // Return empty TwiML for messages (Twilio doesn't expect a response)
            return ResponseEntity.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");
        }

        TeiaUser client = clientOpt.get();

        // Check credit balance
        if (!webhookService.hasCredits(client, UsageEventType.MESSAGE)) {
            log.warn("[TwilioWebhook] Client {} has insufficient credits for message", client.getId());
            return ResponseEntity.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");
        }

        // Reserve credits and create session
        UsageEvent session = webhookService.reserveCredits(client, UsageEventType.MESSAGE);

        // Build forward request with decrypted credentials
        TeiaCoreForwardRequest forwardRequest = buildForwardRequest(client, session, request);

        try {
            // Forward to TEIA Core
            ResponseEntity<String> response = teiaCoreClient.post(
                    "/internal/message",
                    client.getId().toString(),
                    forwardRequest,
                    String.class
            );

            log.info("[TwilioWebhook] Message forwarded to TEIA Core: sessionId={}, status={}",
                    session.getSessionId(), response.getStatusCode());

            // Return TEIA Core's TwiML response
            return ResponseEntity.ok(response.getBody() != null ? response.getBody()
                    : "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");

        } catch (Exception e) {
            log.error("[TwilioWebhook] Failed to forward message to TEIA Core: {}", e.getMessage());
            return ResponseEntity.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");
        }
    }

    /**
     * Initiates an outbound call through TEIA Core.
     * Called by operators/system, not by Twilio.
     */
    @PostMapping(value = "/outbound/call", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Initiate an outbound call",
            description = "Initiates an outbound call through TEIA Core. " +
                    "This endpoint is called by operators or internal systems, not by Twilio. " +
                    "Reserves credits, generates a session token, and forwards the request to TEIA Core " +
                    "which will use the client's Twilio credentials to place the call."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Outbound call initiated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "sessionId": "sess_abc123def456",
                                                "clientId": 123,
                                                "to": "+15551234567",
                                                "status": "initiated",
                                                "teiaCoreResponse": ""
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request (bad client ID format or missing Twilio config)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Client has no Twilio configuration\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "402",
                    description = "Insufficient credits",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Insufficient credits\", \"creditBalance\": 0.00}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Client not found\", \"clientId\": 123}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Failed to initiate call via TEIA Core",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"error\": \"Failed to initiate outbound call\", \"message\": \"Connection refused\", \"sessionId\": \"sess_abc123\"}"
                            )
                    )
            )
    })
    public ResponseEntity<?> initiateOutboundCall(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Outbound call request",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = OutboundCallRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "clientId": "123",
                                                "to": "+15551234567",
                                                "customPrompt": "You are calling to confirm an appointment.",
                                                "statusCallbackUrl": "https://example.com/callback"
                                            }"""
                            )
                    )
            )
            @Valid @RequestBody OutboundCallRequest request) {
        Long clientId;
        try {
            clientId = Long.parseLong(request.getClientId());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid client ID format"));
        }

        log.info("[TwilioWebhook] Outbound call requested: clientId={}, to={}", clientId, request.getTo());

        // Look up client by ID
        Optional<TeiaUser> clientOpt = userRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            log.warn("[TwilioWebhook] Client not found: {}", clientId);
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Client not found",
                    "clientId", clientId
            ));
        }

        TeiaUser client = clientOpt.get();

        // Validate client has required credentials
        if (client.getTwilioInfo() == null) {
            log.warn("[TwilioWebhook] Client {} has no Twilio configuration", clientId);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Client has no Twilio configuration"
            ));
        }

        // Check credit balance
        if (!webhookService.hasCredits(client, UsageEventType.CALL)) {
            log.warn("[TwilioWebhook] Client {} has insufficient credits for outbound call", clientId);
            return ResponseEntity.status(402).body(Map.of(
                    "error", "Insufficient credits",
                    "creditBalance", client.getCreditBalance()
            ));
        }

        // Reserve credits and create session
        UsageEvent session = webhookService.reserveCredits(client, UsageEventType.CALL);
        String sessionToken = sessionTokenService.generateToken(client.getId(), session.getSessionId());

        log.info("[TwilioWebhook] Outbound session created: sessionId={}, clientId={}",
                session.getSessionId(), client.getId());

        // Build forward request with decrypted credentials
        TeiaCoreForwardRequest forwardRequest = buildOutboundForwardRequest(client, session, request, sessionToken);

        try {
            // Forward to TEIA Core to initiate the outbound call
            ResponseEntity<String> response = teiaCoreClient.post(
                    "/internal/outbound-call",
                    client.getId().toString(),
                    forwardRequest,
                    String.class
            );

            log.info("[TwilioWebhook] Outbound call forwarded to TEIA Core: sessionId={}, status={}",
                    session.getSessionId(), response.getStatusCode());

            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getSessionId(),
                    "clientId", clientId,
                    "to", request.getTo(),
                    "status", "initiated",
                    "teiaCoreResponse", response.getBody() != null ? response.getBody() : ""
            ));

        } catch (Exception e) {
            log.error("[TwilioWebhook] Failed to forward outbound call to TEIA Core: {}", e.getMessage());
            // Note: Credits are already reserved. TEIA Core should call session/complete on failure.
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to initiate outbound call",
                    "message", e.getMessage(),
                    "sessionId", session.getSessionId()
            ));
        }
    }

    /**
     * Builds the forward request for outbound calls with decrypted client credentials.
     */
    private TeiaCoreForwardRequest buildOutboundForwardRequest(TeiaUser client, UsageEvent session,
                                                                OutboundCallRequest request, String sessionToken) {
        TeiaCoreForwardRequest.TeiaCoreForwardRequestBuilder builder = TeiaCoreForwardRequest.builder()
                .sessionId(session.getSessionId())
                .clientId(client.getId())
                .sessionToken(sessionToken)
                .to(request.getTo())
                .direction("outbound")
                .customPrompt(request.getCustomPrompt());

        // Add Twilio credentials
        if (client.getTwilioInfo() != null) {
            builder.twilioAccountSid(client.getTwilioInfo().getTwilioAccSID())
                    .twilioAuthToken(client.getTwilioInfo().getTwilioAuthToken())
                    .twilioCallNumber(client.getTwilioInfo().getTwilioPhoneNumberCalls())
                    .twilioWhatsappNumber(client.getTwilioInfo().getTwilioPhoneNumberWhatsapp());
        }

        // Add ElevenLabs credentials
        if (client.getElevenLabsInfo() != null) {
            builder.elevenLabsApiKey(client.getElevenLabsInfo().getElevenLabsApiKey())
                    .elevenLabsAgentId(client.getElevenLabsInfo().getElevenLabsAgentId());
        }

        // Add OpenAI credentials
        if (client.getOpenAIInfo() != null) {
            builder.openAiApiKey(client.getOpenAIInfo().getOpenAIApiKey())
                    .openAiAssistantId(client.getOpenAIInfo().getOpenAIAssistantKey());
        }

        // Add Cal.com credentials
        if (client.getCalInfo() != null) {
            builder.calApiKey(client.getCalInfo().getApiKey())
                    .calUserId(client.getCalInfo().getUserId())
                    .calEventId(client.getCalInfo().getEventId());
        }

        return builder.build();
    }

    /**
     * Generates TwiML that connects Twilio to TEIA Core WebSocket.
     * Uses <Parameter> elements to pass session token and client ID.
     */
    private String generateStreamTwiml(Long clientId, String sessionId, String sessionToken) {
        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Connect>
                        <Stream url="%s/media-stream">
                            <Parameter name="session_token" value="%s"/>
                            <Parameter name="client_id" value="%s"/>
                            <Parameter name="session_id" value="%s"/>
                        </Stream>
                    </Connect>
                </Response>
                """, teiaCoreWsBaseUrl, sessionToken, clientId, sessionId);
    }

    /**
     * Builds the forward request with decrypted client credentials.
     */
    private TeiaCoreForwardRequest buildForwardRequest(TeiaUser client, UsageEvent session,
                                                        TwilioMessageWebhookRequest request) {
        return TeiaCoreForwardRequest.builder()
                .sessionId(session.getSessionId())
                .clientId(client.getId())
                .messageSid(request.getMessageSid())
                .from(request.getFrom())
                .to(request.getTo())
                .body(request.getBody())
                // Twilio credentials (already decrypted by JPA converter)
                .twilioAccountSid(client.getTwilioInfo().getTwilioAccSID())
                .twilioAuthToken(client.getTwilioInfo().getTwilioAuthToken())
                .twilioCallNumber(client.getTwilioInfo().getTwilioPhoneNumberCalls())
                .twilioWhatsappNumber(client.getTwilioInfo().getTwilioPhoneNumberWhatsapp())
                // ElevenLabs credentials
                .elevenLabsApiKey(client.getElevenLabsInfo().getElevenLabsApiKey())
                .elevenLabsAgentId(client.getElevenLabsInfo().getElevenLabsAgentId())
                // OpenAI credentials
                .openAiApiKey(client.getOpenAIInfo().getOpenAIApiKey())
                .openAiAssistantId(client.getOpenAIInfo().getOpenAIAssistantKey())
                // Cal.com credentials
                .calApiKey(client.getCalInfo().getApiKey())
                .calUserId(client.getCalInfo().getUserId())
                .calEventId(client.getCalInfo().getEventId())
                .build();
    }
}
