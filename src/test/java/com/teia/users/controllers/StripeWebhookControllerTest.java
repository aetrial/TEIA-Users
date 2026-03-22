package com.teia.users.controllers;

import com.teia.users.services.BillingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("StripeWebhookController")
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BillingService billingService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private static final String VALID_PAYLOAD = """
            {
              "id": "evt_test_123",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "cs_test_session_123",
                  "object": "checkout.session",
                  "payment_status": "paid",
                  "metadata": {
                    "client_id": "1",
                    "bundle_id": "BUNDLE_100",
                    "credits": "100"
                  }
                }
              }
            }
            """;

    @Nested
    @DisplayName("POST /billing/webhook")
    class WebhookEndpoint {

        @Test
        @DisplayName("should process checkout.session.completed event and add credits")
        void shouldProcessCheckoutSessionCompletedEvent() throws Exception {
            // Create mock Event and Session
            Event mockEvent = mock(Event.class);
            Session mockSession = mock(Session.class);
            EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);

            when(mockEvent.getType()).thenReturn("checkout.session.completed");
            when(mockEvent.getId()).thenReturn("evt_test_123");
            when(mockEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);
            when(mockDeserializer.getObject()).thenReturn(Optional.of(mockSession));
            when(mockSession.getId()).thenReturn("cs_test_session_123");

            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                        .thenReturn(mockEvent);

                doNothing().when(billingService).fulfillPurchase("cs_test_session_123");

                mockMvc.perform(post("/billing/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_PAYLOAD)
                                .header("Stripe-Signature", "t=1234567890,v1=valid_signature"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("Received"));

                verify(billingService).fulfillPurchase("cs_test_session_123");
            }
        }

        @Test
        @DisplayName("should return 400 for invalid signature")
        void shouldReturn400ForInvalidSignature() throws Exception {
            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                        .thenThrow(new SignatureVerificationException("Invalid signature", "sig_header"));

                mockMvc.perform(post("/billing/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_PAYLOAD)
                                .header("Stripe-Signature", "invalid_signature"))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().string("Invalid signature"));

                verify(billingService, never()).fulfillPurchase(anyString());
            }
        }

        @Test
        @DisplayName("should return 200 for non-checkout events without processing")
        void shouldReturn200ForNonCheckoutEvents() throws Exception {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("payment_intent.created");
            when(mockEvent.getId()).thenReturn("evt_test_other");

            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                        .thenReturn(mockEvent);

                mockMvc.perform(post("/billing/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\": \"payment_intent.created\"}")
                                .header("Stripe-Signature", "t=1234567890,v1=valid_signature"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("Received"));

                verify(billingService, never()).fulfillPurchase(anyString());
            }
        }

        @Test
        @DisplayName("should not require authentication")
        void shouldNotRequireAuthentication() throws Exception {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("customer.created");
            when(mockEvent.getId()).thenReturn("evt_test_123");

            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                        .thenReturn(mockEvent);

                // No Authorization header - should still work
                mockMvc.perform(post("/billing/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\": \"customer.created\"}")
                                .header("Stripe-Signature", "t=1234567890,v1=valid_signature"))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("should handle missing session in event gracefully")
        void shouldHandleMissingSessionInEvent() throws Exception {
            Event mockEvent = mock(Event.class);
            EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);

            when(mockEvent.getType()).thenReturn("checkout.session.completed");
            when(mockEvent.getId()).thenReturn("evt_test_123");
            when(mockEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);
            when(mockDeserializer.getObject()).thenReturn(Optional.empty());

            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                        .thenReturn(mockEvent);

                mockMvc.perform(post("/billing/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_PAYLOAD)
                                .header("Stripe-Signature", "t=1234567890,v1=valid_signature"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(content().string("Failed to process event"));

                verify(billingService, never()).fulfillPurchase(anyString());
            }
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("should handle duplicate webhook calls gracefully")
        void shouldHandleDuplicateWebhookCalls() throws Exception {
            Event mockEvent = mock(Event.class);
            Session mockSession = mock(Session.class);
            EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);

            when(mockEvent.getType()).thenReturn("checkout.session.completed");
            when(mockEvent.getId()).thenReturn("evt_test_123");
            when(mockEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);
            when(mockDeserializer.getObject()).thenReturn(Optional.of(mockSession));
            when(mockSession.getId()).thenReturn("cs_test_session_123");

            // Service handles idempotency internally - should not throw
            doNothing().when(billingService).fulfillPurchase("cs_test_session_123");

            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                        .thenReturn(mockEvent);

                // First call
                mockMvc.perform(post("/billing/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_PAYLOAD)
                                .header("Stripe-Signature", "t=1234567890,v1=valid_signature"))
                        .andExpect(status().isOk());

                // Second call (duplicate)
                mockMvc.perform(post("/billing/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_PAYLOAD)
                                .header("Stripe-Signature", "t=1234567890,v1=valid_signature"))
                        .andExpect(status().isOk());

                // fulfillPurchase handles idempotency - called twice but no double-crediting
                verify(billingService, times(2)).fulfillPurchase("cs_test_session_123");
            }
        }
    }
}
