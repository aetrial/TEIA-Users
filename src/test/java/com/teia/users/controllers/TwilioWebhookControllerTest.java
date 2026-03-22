package com.teia.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.client.TeiaCoreClient;
import com.teia.users.models.*;
import com.teia.users.models.enums.Role;
import com.teia.users.models.enums.UsageEventType;
import com.teia.users.repositories.UserRepository;
import com.teia.users.services.SessionTokenService;
import com.teia.users.services.TwilioWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TwilioWebhookController.
 * Tests credit checking, session token generation, and forwarding to TEIA Core.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwilioWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TwilioWebhookService webhookService;

    @MockBean
    private SessionTokenService sessionTokenService;

    @MockBean
    private TeiaCoreClient teiaCoreClient;

    @MockBean
    private UserRepository userRepository;

    private TeiaUser testClient;
    private UsageEvent testSession;

    @BeforeEach
    void setUp() {
        // Set up test client with credentials
        testClient = new TeiaUser();
        testClient.setId(1L);
        testClient.setName("Test Client");
        testClient.setEmail("test@example.com");
        testClient.setRole(Role.CLIENT);
        testClient.setCreditBalance(new BigDecimal("100.00"));

        TwilioInfo twilioInfo = new TwilioInfo();
        twilioInfo.setTwilioAccSID("AC123456789");
        twilioInfo.setTwilioAuthToken("auth_token_123");
        twilioInfo.setTwilioPhoneNumberCalls("+15551234567");
        twilioInfo.setTwilioPhoneNumberWhatsapp("+15551234567");
        testClient.setTwilioInfo(twilioInfo);

        ElevenLabsInfo elevenLabsInfo = new ElevenLabsInfo();
        elevenLabsInfo.setElevenLabsApiKey("el_api_key");
        elevenLabsInfo.setElevenLabsAgentId("el_agent_id");
        testClient.setElevenLabsInfo(elevenLabsInfo);

        OpenAIInfo openAIInfo = new OpenAIInfo();
        openAIInfo.setOpenAIApiKey("sk-test");
        openAIInfo.setOpenAIAssistantKey("asst_test");
        testClient.setOpenAIInfo(openAIInfo);

        CalInfo calInfo = new CalInfo();
        calInfo.setApiKey("cal_api_key");
        calInfo.setUserId("cal_user_id");
        calInfo.setEventId("cal_event_id");
        testClient.setCalInfo(calInfo);

        // Set up test session
        testSession = new UsageEvent(1L, UsageEventType.CALL, "sess_test123", new BigDecimal("5.00"));
    }

    @Nested
    @DisplayName("Inbound Call Tests")
    class InboundCallTests {

        @Test
        @DisplayName("Should return TwiML error when client has zero credits")
        void shouldRejectZeroBalanceClient() throws Exception {
            // Given: Client with zero balance
            testClient.setCreditBalance(BigDecimal.ZERO);
            when(webhookService.findClientByCallNumber(anyString())).thenReturn(Optional.of(testClient));
            when(webhookService.hasCredits(any(), eq(UsageEventType.CALL))).thenReturn(false);

            // When: Inbound call webhook is received
            mockMvc.perform(post("/twilio/inbound/call")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("CallSid", "CA123456")
                    .param("From", "+15559876543")
                    .param("To", "+15551234567")
                    .param("Direction", "inbound"))
                    // Then: Service unavailable TwiML is returned
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                    .andExpect(content().string(containsString("<Say>Service unavailable")));

            // Verify no session was created
            verify(webhookService, never()).reserveCredits(any(), any());
        }

        @Test
        @DisplayName("Should return TwiML error for unknown phone number")
        void shouldRejectUnknownPhoneNumber() throws Exception {
            // Given: No client found for the phone number
            when(webhookService.findClientByCallNumber(anyString())).thenReturn(Optional.empty());

            // When: Inbound call webhook is received
            mockMvc.perform(post("/twilio/inbound/call")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("CallSid", "CA123456")
                    .param("From", "+15559876543")
                    .param("To", "+15559999999")
                    .param("Direction", "inbound"))
                    // Then: Unknown number TwiML is returned
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                    .andExpect(content().string(containsString("not configured")));

            verify(webhookService, never()).reserveCredits(any(), any());
        }

        @Test
        @DisplayName("Should return WebSocket TwiML for valid client with credits")
        void shouldReturnStreamTwimlForValidClient() throws Exception {
            // Given: Valid client with credits
            when(webhookService.findClientByCallNumber(anyString())).thenReturn(Optional.of(testClient));
            when(webhookService.hasCredits(any(), eq(UsageEventType.CALL))).thenReturn(true);
            when(webhookService.reserveCredits(any(), eq(UsageEventType.CALL))).thenReturn(testSession);
            when(sessionTokenService.generateToken(anyLong(), anyString())).thenReturn("test_session_token");

            // When: Inbound call webhook is received
            mockMvc.perform(post("/twilio/inbound/call")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("CallSid", "CA123456")
                    .param("From", "+15559876543")
                    .param("To", "+15551234567")
                    .param("Direction", "inbound"))
                    // Then: Stream TwiML with session token is returned
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                    .andExpect(content().string(containsString("<Stream")))
                    .andExpect(content().string(containsString("<Parameter name=\"session_token\"")))
                    .andExpect(content().string(containsString("<Parameter name=\"client_id\"")));

            // Verify credits were reserved
            verify(webhookService).reserveCredits(eq(testClient), eq(UsageEventType.CALL));
        }
    }

    @Nested
    @DisplayName("Inbound Message Tests")
    class InboundMessageTests {

        @Test
        @DisplayName("Should return empty TwiML for zero credit client")
        void shouldRejectZeroBalanceClientForMessage() throws Exception {
            // Given: Client with zero balance
            testClient.setCreditBalance(BigDecimal.ZERO);
            when(webhookService.findClientByWhatsappNumber(anyString())).thenReturn(Optional.of(testClient));
            when(webhookService.hasCredits(any(), eq(UsageEventType.MESSAGE))).thenReturn(false);

            // When: Inbound message webhook is received
            mockMvc.perform(post("/twilio/inbound/message")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("MessageSid", "SM123456")
                    .param("From", "whatsapp:+15559876543")
                    .param("To", "whatsapp:+15551234567")
                    .param("Body", "Hello"))
                    // Then: Empty response TwiML is returned
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<Response></Response>")));

            verify(webhookService, never()).reserveCredits(any(), any());
        }

        @Test
        @DisplayName("Should forward message to TEIA Core for valid client")
        void shouldForwardMessageForValidClient() throws Exception {
            // Given: Valid client with credits
            UsageEvent messageSession = new UsageEvent(1L, UsageEventType.MESSAGE, "sess_msg123", new BigDecimal("1.00"));

            when(webhookService.findClientByWhatsappNumber(anyString())).thenReturn(Optional.of(testClient));
            when(webhookService.hasCredits(any(), eq(UsageEventType.MESSAGE))).thenReturn(true);
            when(webhookService.reserveCredits(any(), eq(UsageEventType.MESSAGE))).thenReturn(messageSession);
            when(teiaCoreClient.post(anyString(), anyString(), any(), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>"));

            // When: Inbound message webhook is received
            mockMvc.perform(post("/twilio/inbound/message")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("MessageSid", "SM123456")
                    .param("From", "whatsapp:+15559876543")
                    .param("To", "whatsapp:+15551234567")
                    .param("Body", "Hello"))
                    // Then: Response from TEIA Core is returned
                    .andExpect(status().isOk());

            // Verify credits were reserved and message was forwarded
            verify(webhookService).reserveCredits(eq(testClient), eq(UsageEventType.MESSAGE));
            verify(teiaCoreClient).post(eq("/internal/message"), anyString(), any(), eq(String.class));
        }
    }
}
