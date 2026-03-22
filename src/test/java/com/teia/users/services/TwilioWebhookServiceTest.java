package com.teia.users.services;

import com.teia.users.models.*;
import com.teia.users.models.enums.CreditTransactionType;
import com.teia.users.models.enums.Role;
import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.models.enums.UsageEventType;
import com.teia.users.repositories.CreditTransactionRepository;
import com.teia.users.repositories.UsageEventRepository;
import com.teia.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TwilioWebhookService.
 * Tests client lookup, credit checking, reservation, and reconciliation.
 */
@ExtendWith(MockitoExtension.class)
class TwilioWebhookServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UsageEventRepository usageEventRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @InjectMocks
    private TwilioWebhookService webhookService;

    private TeiaUser testClient;

    @BeforeEach
    void setUp() {
        testClient = new TeiaUser();
        testClient.setId(1L);
        testClient.setName("Test Client");
        testClient.setEmail("test@example.com");
        testClient.setRole(Role.CLIENT);
        testClient.setCreditBalance(new BigDecimal("100.00"));

        TwilioInfo twilioInfo = new TwilioInfo();
        twilioInfo.setTwilioPhoneNumberCalls("+15551234567");
        twilioInfo.setTwilioPhoneNumberWhatsapp("+15551234567");
        testClient.setTwilioInfo(twilioInfo);
    }

    @Nested
    @DisplayName("Client Lookup Tests")
    class ClientLookupTests {

        @Test
        @DisplayName("Should find client by call number")
        void shouldFindClientByCallNumber() {
            when(userRepository.findAll()).thenReturn(Arrays.asList(testClient));

            Optional<TeiaUser> result = webhookService.findClientByCallNumber("+15551234567");

            assertTrue(result.isPresent());
            assertEquals(testClient.getId(), result.get().getId());
        }

        @Test
        @DisplayName("Should find client by WhatsApp number")
        void shouldFindClientByWhatsappNumber() {
            when(userRepository.findAll()).thenReturn(Arrays.asList(testClient));

            Optional<TeiaUser> result = webhookService.findClientByWhatsappNumber("+15551234567");

            assertTrue(result.isPresent());
            assertEquals(testClient.getId(), result.get().getId());
        }

        @Test
        @DisplayName("Should return empty for unknown number")
        void shouldReturnEmptyForUnknownNumber() {
            when(userRepository.findAll()).thenReturn(Arrays.asList(testClient));

            Optional<TeiaUser> result = webhookService.findClientByCallNumber("+19999999999");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should normalize phone numbers for comparison")
        void shouldNormalizePhoneNumbers() {
            when(userRepository.findAll()).thenReturn(Arrays.asList(testClient));

            // With whatsapp: prefix
            Optional<TeiaUser> result1 = webhookService.findClientByWhatsappNumber("whatsapp:+15551234567");
            assertTrue(result1.isPresent());

            // With extra formatting
            Optional<TeiaUser> result2 = webhookService.findClientByCallNumber("+1 (555) 123-4567");
            assertTrue(result2.isPresent());
        }
    }

    @Nested
    @DisplayName("Credit Check Tests")
    class CreditCheckTests {

        @Test
        @DisplayName("Should return true when client has sufficient credits for call")
        void shouldReturnTrueForSufficientCallCredits() {
            testClient.setCreditBalance(new BigDecimal("10.00"));

            boolean result = webhookService.hasCredits(testClient, UsageEventType.CALL);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when client has insufficient credits for call")
        void shouldReturnFalseForInsufficientCallCredits() {
            testClient.setCreditBalance(new BigDecimal("2.00")); // Less than 5

            boolean result = webhookService.hasCredits(testClient, UsageEventType.CALL);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return true when client has sufficient credits for message")
        void shouldReturnTrueForSufficientMessageCredits() {
            testClient.setCreditBalance(new BigDecimal("1.00"));

            boolean result = webhookService.hasCredits(testClient, UsageEventType.MESSAGE);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when client has zero credits")
        void shouldReturnFalseForZeroCredits() {
            testClient.setCreditBalance(BigDecimal.ZERO);

            boolean callResult = webhookService.hasCredits(testClient, UsageEventType.CALL);
            boolean messageResult = webhookService.hasCredits(testClient, UsageEventType.MESSAGE);

            assertFalse(callResult);
            assertFalse(messageResult);
        }

        @Test
        @DisplayName("Should return false when credit balance is null")
        void shouldReturnFalseForNullBalance() {
            testClient.setCreditBalance(null);

            boolean result = webhookService.hasCredits(testClient, UsageEventType.CALL);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Credit Reservation Tests")
    class CreditReservationTests {

        @Test
        @DisplayName("Should reserve 5 credits for a call")
        void shouldReserve5CreditsForCall() {
            when(usageEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(creditTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UsageEvent result = webhookService.reserveCredits(testClient, UsageEventType.CALL);

            assertNotNull(result);
            assertEquals(new BigDecimal("5.00"), result.getCreditsReserved());
            assertEquals(UsageEventType.CALL, result.getType());
            assertEquals(UsageEventStatus.STARTED, result.getStatus());
            assertEquals(new BigDecimal("95.00"), testClient.getCreditBalance());
        }

        @Test
        @DisplayName("Should reserve 1 credit for a message")
        void shouldReserve1CreditForMessage() {
            when(usageEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(creditTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UsageEvent result = webhookService.reserveCredits(testClient, UsageEventType.MESSAGE);

            assertNotNull(result);
            assertEquals(new BigDecimal("1.00"), result.getCreditsReserved());
            assertEquals(UsageEventType.MESSAGE, result.getType());
            assertEquals(new BigDecimal("99.00"), testClient.getCreditBalance());
        }

        @Test
        @DisplayName("Should create credit transaction for reservation")
        void shouldCreateCreditTransaction() {
            when(usageEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(creditTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            webhookService.reserveCredits(testClient, UsageEventType.CALL);

            ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
            verify(creditTransactionRepository).save(captor.capture());

            CreditTransaction transaction = captor.getValue();
            assertEquals(CreditTransactionType.RESERVATION, transaction.getType());
            assertEquals(new BigDecimal("-5.00"), transaction.getAmount()); // Negative for reservation
            assertEquals(testClient.getId(), transaction.getClientId());
        }

        @Test
        @DisplayName("Should generate unique session ID")
        void shouldGenerateUniqueSessionId() {
            when(usageEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(creditTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UsageEvent result1 = webhookService.reserveCredits(testClient, UsageEventType.CALL);
            UsageEvent result2 = webhookService.reserveCredits(testClient, UsageEventType.CALL);

            assertNotNull(result1.getSessionId());
            assertNotNull(result2.getSessionId());
            assertNotEquals(result1.getSessionId(), result2.getSessionId());
            assertTrue(result1.getSessionId().startsWith("sess_"));
        }
    }

    @Nested
    @DisplayName("Credit Reconciliation Tests")
    class CreditReconciliationTests {

        @Test
        @DisplayName("Should refund credits when actual usage is less than reserved")
        void shouldRefundCreditsForShortCall() {
            UsageEvent session = new UsageEvent(1L, UsageEventType.CALL, "sess_test", new BigDecimal("5.00"));
            testClient.setCreditBalance(new BigDecimal("95.00")); // After 5 credit reservation

            when(usageEventRepository.findBySessionId("sess_test")).thenReturn(Optional.of(session));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));
            when(usageEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(creditTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // 2 minutes = 2 credits, reserved 5, refund 3
            UsageEvent result = webhookService.reconcileSession("sess_test", 120, UsageEventStatus.COMPLETED);

            assertEquals(UsageEventStatus.COMPLETED, result.getStatus());
            assertEquals(new BigDecimal("2.00"), result.getCreditsUsed());
            assertEquals(new BigDecimal("98.00"), testClient.getCreditBalance()); // 95 + 3 refund

            ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
            verify(creditTransactionRepository).save(captor.capture());
            CreditTransaction refund = captor.getValue();
            assertEquals(CreditTransactionType.REFUND, refund.getType());
            assertEquals(new BigDecimal("3.00"), refund.getAmount());
        }

        @Test
        @DisplayName("Should deduct additional credits when actual usage exceeds reserved")
        void shouldDeductAdditionalCreditsForLongCall() {
            UsageEvent session = new UsageEvent(1L, UsageEventType.CALL, "sess_test", new BigDecimal("5.00"));
            testClient.setCreditBalance(new BigDecimal("95.00"));

            when(usageEventRepository.findBySessionId("sess_test")).thenReturn(Optional.of(session));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));
            when(usageEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(creditTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // 10 minutes = 10 credits, reserved 5, need additional 5
            UsageEvent result = webhookService.reconcileSession("sess_test", 600, UsageEventStatus.COMPLETED);

            assertEquals(new BigDecimal("10.00"), result.getCreditsUsed());
            assertEquals(new BigDecimal("90.00"), testClient.getCreditBalance()); // 95 - 5 additional

            ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
            verify(creditTransactionRepository).save(captor.capture());
            CreditTransaction deduction = captor.getValue();
            assertEquals(CreditTransactionType.DEDUCTION, deduction.getType());
            assertEquals(new BigDecimal("-5.00"), deduction.getAmount());
        }

        @Test
        @DisplayName("Should refund all credits for failed session")
        void shouldRefundAllCreditsForFailedSession() {
            UsageEvent session = new UsageEvent(1L, UsageEventType.CALL, "sess_test", new BigDecimal("5.00"));
            testClient.setCreditBalance(new BigDecimal("95.00"));

            when(usageEventRepository.findBySessionId("sess_test")).thenReturn(Optional.of(session));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));
            when(usageEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(creditTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UsageEvent result = webhookService.reconcileSession("sess_test", null, UsageEventStatus.FAILED);

            assertEquals(UsageEventStatus.FAILED, result.getStatus());
            assertEquals(BigDecimal.ZERO, result.getCreditsUsed());
            assertEquals(new BigDecimal("100.00"), testClient.getCreditBalance()); // Full refund
        }

        @Test
        @DisplayName("Should throw exception for unknown session")
        void shouldThrowForUnknownSession() {
            when(usageEventRepository.findBySessionId("sess_unknown")).thenReturn(Optional.empty());

            assertThrows(TwilioWebhookService.SessionNotFoundException.class, () -> {
                webhookService.reconcileSession("sess_unknown", 60, UsageEventStatus.COMPLETED);
            });
        }

        @Test
        @DisplayName("Should not reconcile already completed session")
        void shouldNotReconcileAlreadyCompletedSession() {
            UsageEvent session = new UsageEvent(1L, UsageEventType.CALL, "sess_test", new BigDecimal("5.00"));
            session.complete(60, new BigDecimal("1.00")); // Already completed

            when(usageEventRepository.findBySessionId("sess_test")).thenReturn(Optional.of(session));

            UsageEvent result = webhookService.reconcileSession("sess_test", 120, UsageEventStatus.COMPLETED);

            // Should return existing state without modification
            assertEquals(UsageEventStatus.COMPLETED, result.getStatus());
            verify(creditTransactionRepository, never()).save(any());
        }
    }
}
