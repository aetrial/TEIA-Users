package com.teia.users.services;

import com.teia.users.models.CreditTransaction;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.enums.CreditBundle;
import com.teia.users.models.enums.CreditTransactionType;
import com.teia.users.repositories.CreditTransactionRepository;
import com.teia.users.repositories.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingService")
class BillingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(userRepository, creditTransactionRepository);
    }

    @Nested
    @DisplayName("getTransactionHistory")
    class GetTransactionHistory {

        @Test
        @DisplayName("should return transactions sorted by date descending")
        void shouldReturnTransactionsSortedByDateDescending() {
            Long clientId = 1L;
            CreditTransaction tx1 = new CreditTransaction(clientId, BigDecimal.valueOf(100), CreditTransactionType.PURCHASE, "ref1");
            CreditTransaction tx2 = new CreditTransaction(clientId, BigDecimal.valueOf(500), CreditTransactionType.PURCHASE, "ref2");
            List<CreditTransaction> expected = List.of(tx2, tx1);

            when(creditTransactionRepository.findByClientIdOrderByCreatedAtDesc(clientId))
                    .thenReturn(expected);

            List<CreditTransaction> result = billingService.getTransactionHistory(clientId);

            assertEquals(2, result.size());
            assertEquals(expected, result);
            verify(creditTransactionRepository).findByClientIdOrderByCreatedAtDesc(clientId);
        }

        @Test
        @DisplayName("should return empty list when no transactions exist")
        void shouldReturnEmptyListWhenNoTransactionsExist() {
            Long clientId = 999L;
            when(creditTransactionRepository.findByClientIdOrderByCreatedAtDesc(clientId))
                    .thenReturn(Collections.emptyList());

            List<CreditTransaction> result = billingService.getTransactionHistory(clientId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("createCheckoutSession")
    class CreateCheckoutSession {

        @Test
        @DisplayName("should create Stripe session for valid client and bundle")
        void shouldCreateStripeSessionForValidClientAndBundle() throws Exception {
            Long clientId = 1L;
            CreditBundle bundle = CreditBundle.BUNDLE_100;

            when(userRepository.existsById(clientId)).thenReturn(true);

            Session mockSession = mock(Session.class);
            when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_123");

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

                String checkoutUrl = billingService.createCheckoutSession(clientId, bundle);

                assertEquals("https://checkout.stripe.com/pay/cs_test_123", checkoutUrl);
                verify(userRepository).existsById(clientId);
            }
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when client not found")
        void shouldThrowWhenClientNotFound() {
            Long clientId = 999L;
            CreditBundle bundle = CreditBundle.BUNDLE_500;

            when(userRepository.existsById(clientId)).thenReturn(false);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> billingService.createCheckoutSession(clientId, bundle)
            );

            assertTrue(exception.getMessage().contains("Client not found"));
            verify(userRepository).existsById(clientId);
        }

        @Test
        @DisplayName("should propagate StripeException on API failure")
        void shouldPropagateStripeExceptionOnApiFailure() {
            Long clientId = 1L;
            CreditBundle bundle = CreditBundle.BUNDLE_1000;

            when(userRepository.existsById(clientId)).thenReturn(true);

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.create(any(SessionCreateParams.class)))
                        .thenThrow(new StripeException("API Error", "req_123", "code", 500) {});

                assertThrows(StripeException.class, () -> {
                    billingService.createCheckoutSession(clientId, bundle);
                });
            }
        }
    }

    @Nested
    @DisplayName("fulfillPurchase - Idempotency")
    class FulfillPurchase {

        @Test
        @DisplayName("should add credits and create transaction on first fulfillment")
        void shouldAddCreditsAndCreateTransactionOnFirstFulfillment() throws Exception {
            String sessionId = "cs_test_first_123";
            Long clientId = 1L;
            BigDecimal initialBalance = new BigDecimal("50.00");
            BigDecimal credits = new BigDecimal("100");

            TeiaUser user = new TeiaUser();
            user.setId(clientId);
            user.setCreditBalance(initialBalance);

            when(creditTransactionRepository.findByReferenceId(sessionId)).thenReturn(Collections.emptyList());
            when(userRepository.findById(clientId)).thenReturn(Optional.of(user));

            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("client_id", "1");
            metadata.put("bundle_id", "BUNDLE_100");
            metadata.put("credits", "100");

            when(mockSession.getMetadata()).thenReturn(metadata);

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

                billingService.fulfillPurchase(sessionId);

                ArgumentCaptor<TeiaUser> userCaptor = ArgumentCaptor.forClass(TeiaUser.class);
                verify(userRepository).save(userCaptor.capture());
                TeiaUser savedUser = userCaptor.getValue();
                assertEquals(new BigDecimal("150.00"), savedUser.getCreditBalance());

                ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
                verify(creditTransactionRepository).save(txCaptor.capture());
                CreditTransaction savedTx = txCaptor.getValue();
                assertEquals(clientId, savedTx.getClientId());
                assertEquals(credits, savedTx.getAmount());
                assertEquals(CreditTransactionType.PURCHASE, savedTx.getType());
                assertEquals(sessionId, savedTx.getReferenceId());
            }
        }

        @Test
        @DisplayName("should NOT double-credit on duplicate webhook (idempotent)")
        void shouldNotDoubleCreditOnDuplicateWebhook() throws Exception {
            String sessionId = "cs_test_duplicate_456";

            CreditTransaction existingTx = new CreditTransaction(1L, new BigDecimal("100"), CreditTransactionType.PURCHASE, sessionId);
            when(creditTransactionRepository.findByReferenceId(sessionId)).thenReturn(List.of(existingTx));

            billingService.fulfillPurchase(sessionId);

            verify(creditTransactionRepository).findByReferenceId(sessionId);
            verify(creditTransactionRepository, never()).save(any());
            verify(userRepository, never()).save(any());
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw exception when session metadata missing client_id")
        void shouldThrowWhenMetadataMissingClientId() throws Exception {
            String sessionId = "cs_test_no_client";

            when(creditTransactionRepository.findByReferenceId(sessionId)).thenReturn(Collections.emptyList());

            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("credits", "100");

            when(mockSession.getMetadata()).thenReturn(metadata);

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> billingService.fulfillPurchase(sessionId)
                );

                assertTrue(exception.getMessage().contains("Missing required metadata"));
            }
        }

        @Test
        @DisplayName("should throw exception when session metadata missing credits")
        void shouldThrowWhenMetadataMissingCredits() throws Exception {
            String sessionId = "cs_test_no_credits";

            when(creditTransactionRepository.findByReferenceId(sessionId)).thenReturn(Collections.emptyList());

            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("client_id", "1");

            when(mockSession.getMetadata()).thenReturn(metadata);

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> billingService.fulfillPurchase(sessionId)
                );

                assertTrue(exception.getMessage().contains("Missing required metadata"));
            }
        }

        @Test
        @DisplayName("should throw exception when client not found during fulfillment")
        void shouldThrowWhenClientNotFoundDuringFulfillment() throws Exception {
            String sessionId = "cs_test_no_user";
            Long clientId = 999L;

            when(creditTransactionRepository.findByReferenceId(sessionId)).thenReturn(Collections.emptyList());
            when(userRepository.findById(clientId)).thenReturn(Optional.empty());

            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("client_id", "999");
            metadata.put("credits", "100");

            when(mockSession.getMetadata()).thenReturn(metadata);

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> billingService.fulfillPurchase(sessionId)
                );

                assertTrue(exception.getMessage().contains("Client not found"));
            }
        }

        @Test
        @DisplayName("should handle user with null initial balance")
        void shouldHandleUserWithNullInitialBalance() throws Exception {
            String sessionId = "cs_test_null_balance";
            Long clientId = 1L;
            BigDecimal credits = new BigDecimal("500");

            TeiaUser user = new TeiaUser();
            user.setId(clientId);
            user.setCreditBalance(null);

            when(creditTransactionRepository.findByReferenceId(sessionId)).thenReturn(Collections.emptyList());
            when(userRepository.findById(clientId)).thenReturn(Optional.of(user));

            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("client_id", "1");
            metadata.put("bundle_id", "BUNDLE_500");
            metadata.put("credits", "500");

            when(mockSession.getMetadata()).thenReturn(metadata);

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

                billingService.fulfillPurchase(sessionId);

                ArgumentCaptor<TeiaUser> userCaptor = ArgumentCaptor.forClass(TeiaUser.class);
                verify(userRepository).save(userCaptor.capture());
                TeiaUser savedUser = userCaptor.getValue();
                assertEquals(new BigDecimal("500"), savedUser.getCreditBalance());
            }
        }

        @Test
        @DisplayName("should propagate StripeException when session retrieval fails")
        void shouldPropagateStripeExceptionWhenSessionRetrievalFails() throws Exception {
            String sessionId = "cs_test_stripe_error";

            when(creditTransactionRepository.findByReferenceId(sessionId)).thenReturn(Collections.emptyList());

            try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
                sessionMock.when(() -> Session.retrieve(sessionId))
                        .thenThrow(new StripeException("Session not found", "req_123", "code", 404) {});

                assertThrows(StripeException.class, () -> {
                    billingService.fulfillPurchase(sessionId);
                });
            }
        }
    }

    @Nested
    @DisplayName("CreditBundle")
    class CreditBundleTests {

        @Test
        @DisplayName("BUNDLE_100 should have correct values")
        void bundle100ShouldHaveCorrectValues() {
            CreditBundle bundle = CreditBundle.BUNDLE_100;
            assertEquals(100, bundle.getCredits());
            assertEquals(1000L, bundle.getPriceInCents());
            assertEquals("100 Credits", bundle.getDescription());
        }

        @Test
        @DisplayName("BUNDLE_500 should have correct values")
        void bundle500ShouldHaveCorrectValues() {
            CreditBundle bundle = CreditBundle.BUNDLE_500;
            assertEquals(500, bundle.getCredits());
            assertEquals(4000L, bundle.getPriceInCents());
            assertEquals("500 Credits", bundle.getDescription());
        }

        @Test
        @DisplayName("BUNDLE_1000 should have correct values")
        void bundle1000ShouldHaveCorrectValues() {
            CreditBundle bundle = CreditBundle.BUNDLE_1000;
            assertEquals(1000, bundle.getCredits());
            assertEquals(7000L, bundle.getPriceInCents());
            assertEquals("1000 Credits", bundle.getDescription());
        }

        @Test
        @DisplayName("fromId should return correct bundle")
        void fromIdShouldReturnCorrectBundle() {
            assertEquals(CreditBundle.BUNDLE_100, CreditBundle.fromId("BUNDLE_100"));
            assertEquals(CreditBundle.BUNDLE_500, CreditBundle.fromId("BUNDLE_500"));
            assertEquals(CreditBundle.BUNDLE_1000, CreditBundle.fromId("BUNDLE_1000"));
        }

        @Test
        @DisplayName("fromId should throw for invalid bundle ID")
        void fromIdShouldThrowForInvalidBundleId() {
            assertThrows(IllegalArgumentException.class, () -> CreditBundle.fromId("INVALID"));
            assertThrows(IllegalArgumentException.class, () -> CreditBundle.fromId(""));
            assertThrows(IllegalArgumentException.class, () -> CreditBundle.fromId("BUNDLE_999"));
        }
    }
}
