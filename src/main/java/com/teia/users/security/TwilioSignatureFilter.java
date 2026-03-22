package com.teia.users.security;

import com.teia.users.models.TeiaUser;
import com.teia.users.services.TwilioWebhookService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Filter that validates Twilio webhook signatures on inbound endpoints.
 * Uses HMAC-SHA1 with the client's Twilio auth token as the key.
 *
 * Twilio signature validation algorithm:
 * 1. Take the full URL of the request (protocol + host + path)
 * 2. Sort all POST parameters alphabetically by name
 * 3. Append each parameter name and value to the URL (no separators)
 * 4. Sign the resulting string with HMAC-SHA1 using the auth token
 * 5. Base64 encode the signature
 * 6. Compare with X-Twilio-Signature header
 */
@Component
public class TwilioSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TwilioSignatureFilter.class);
    private static final String TWILIO_SIGNATURE_HEADER = "X-Twilio-Signature";
    private static final String HMAC_SHA1 = "HmacSHA1";

    private final TwilioWebhookService webhookService;

    @Value("${teia.twilio.signature-validation.enabled:true}")
    private boolean signatureValidationEnabled;

    public TwilioSignatureFilter(TwilioWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Only apply to /twilio/inbound/** paths
        // Do NOT apply to /twilio/outbound/** (those use internal HMAC auth)
        return !path.startsWith("/twilio/inbound");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // If validation is disabled, pass through
        if (!signatureValidationEnabled) {
            log.debug("[TwilioSignature] Signature validation disabled, passing through");
            filterChain.doFilter(request, response);
            return;
        }

        // Get the signature header
        String twilioSignature = request.getHeader(TWILIO_SIGNATURE_HEADER);
        if (twilioSignature == null || twilioSignature.isBlank()) {
            log.warn("[TwilioSignature] Missing X-Twilio-Signature header for {}", request.getRequestURI());
            sendUnauthorizedResponse(response, "Missing Twilio signature");
            return;
        }

        // Wrap request to allow reading body multiple times
        ContentCachingRequestWrapper wrappedRequest = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request);

        // Parse form parameters to find the "To" number and look up the client
        Map<String, String> params = parseFormParameters(wrappedRequest);
        String toNumber = params.get("To");

        if (toNumber == null || toNumber.isBlank()) {
            log.warn("[TwilioSignature] Missing 'To' parameter in Twilio webhook");
            sendUnauthorizedResponse(response, "Missing 'To' parameter");
            return;
        }

        // Normalize and look up client by phone number
        String normalizedTo = toNumber.replace("whatsapp:", "");
        Optional<TeiaUser> clientOpt = webhookService.findClientByCallNumber(normalizedTo);
        if (clientOpt.isEmpty()) {
            clientOpt = webhookService.findClientByWhatsappNumber(normalizedTo);
        }

        if (clientOpt.isEmpty()) {
            log.warn("[TwilioSignature] No client found for number: {}", normalizedTo);
            // Let the request through - controller will return appropriate error
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        TeiaUser client = clientOpt.get();
        String authToken = client.getTwilioInfo() != null
                ? client.getTwilioInfo().getTwilioAuthToken()
                : null;

        if (authToken == null || authToken.isBlank()) {
            log.warn("[TwilioSignature] Client {} has no Twilio auth token configured", client.getId());
            sendUnauthorizedResponse(response, "Client not configured for Twilio");
            return;
        }

        // Build the validation URL
        String validationUrl = buildValidationUrl(wrappedRequest, params);

        // Compute expected signature
        String expectedSignature;
        try {
            expectedSignature = computeTwilioSignature(validationUrl, authToken);
        } catch (Exception e) {
            log.error("[TwilioSignature] Failed to compute signature: {}", e.getMessage());
            sendUnauthorizedResponse(response, "Signature computation failed");
            return;
        }

        // Constant-time comparison
        if (!MessageDigest.isEqual(
                twilioSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[TwilioSignature] Invalid signature for client {} on {}",
                    client.getId(), request.getRequestURI());
            sendUnauthorizedResponse(response, "Invalid Twilio signature");
            return;
        }

        log.debug("[TwilioSignature] Valid signature for client {} on {}",
                client.getId(), request.getRequestURI());

        // Pass the wrapped request through so controller can read parameters
        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * Parses form-urlencoded parameters from the request.
     */
    private Map<String, String> parseFormParameters(HttpServletRequest request) throws IOException {
        Map<String, String> params = new TreeMap<>(); // TreeMap for alphabetical order

        // Read from request parameters (Spring already parses form data)
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            if (values != null && values.length > 0) {
                params.put(entry.getKey(), values[0]);
            }
        }

        return params;
    }

    /**
     * Builds the validation URL by appending sorted parameters.
     * Twilio signature = HMAC-SHA1(URL + sorted_params, auth_token)
     */
    private String buildValidationUrl(HttpServletRequest request, Map<String, String> params) {
        StringBuilder url = new StringBuilder();

        // Build full URL (use X-Forwarded headers if behind proxy)
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }

        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getHeader("Host");
        }
        if (host == null) {
            host = request.getServerName();
            int port = request.getServerPort();
            if ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)) {
                host += ":" + port;
            }
        }

        url.append(scheme).append("://").append(host);
        url.append(request.getRequestURI());

        // Append sorted parameters (already sorted by TreeMap)
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append(entry.getKey());
            url.append(entry.getValue());
        }

        return url.toString();
    }

    /**
     * Computes Twilio signature using HMAC-SHA1 and Base64 encoding.
     */
    private String computeTwilioSignature(String data, String authToken) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA1);
        SecretKeySpec keySpec = new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), HMAC_SHA1);
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * Sends a 401 Unauthorized response.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"error\":\"Unauthorized\",\"message\":\"%s\"}",
                message.replace("\"", "\\\"")
        ));
    }
}
