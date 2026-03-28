package com.teia.users.services;

import com.teia.users.models.*;
import com.teia.users.models.enums.CreditTransactionType;
import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.models.enums.UsageEventType;
import com.teia.users.repositories.CreditTransactionRepository;
import com.teia.users.repositories.UsageEventRepository;
import com.teia.users.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling Twilio webhook requests.
 * Responsible for client lookup, credit checking, and credit reservation.
 */
@Service
public class TwilioWebhookService {

    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookService.class);

    // Default credit costs for reservation
    private static final BigDecimal DEFAULT_CALL_CREDITS = new BigDecimal("5.00");
    private static final BigDecimal DEFAULT_MESSAGE_CREDITS = new BigDecimal("1.00");

    // Credit cost per minute for actual usage calculation
    private static final BigDecimal CREDITS_PER_MINUTE = new BigDecimal("1.00");

    private final UserRepository userRepository;
    private final UsageEventRepository usageEventRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Value("${teia.credits.call-reservation:5.00}")
    private BigDecimal callReservationCredits;

    @Value("${teia.credits.message-reservation:1.00}")
    private BigDecimal messageReservationCredits;

    @Value("${teia.credits.per-minute:1.00}")
    private BigDecimal creditsPerMinute;

    public TwilioWebhookService(
            UserRepository userRepository,
            UsageEventRepository usageEventRepository,
            CreditTransactionRepository creditTransactionRepository) {
        this.userRepository = userRepository;
        this.usageEventRepository = usageEventRepository;
        this.creditTransactionRepository = creditTransactionRepository;
    }

    /**
     * Finds a client by their Twilio call phone number.
     *
     * @param phoneNumber The Twilio phone number (To field from webhook)
     * @return Optional containing the TeiaUser if found
     */
    public Optional<TeiaUser> findClientByCallNumber(String phoneNumber) {
        String normalizedNumber = normalizePhoneNumber(phoneNumber);
        log.debug("[TwilioWebhookService] Looking up client by call number: {}", normalizedNumber);

        return userRepository.findAll().stream()
                .filter(user -> user.getTwilioInfo() != null)
                .filter(user -> normalizedNumber.equals(
                        normalizePhoneNumber(user.getTwilioInfo().getTwilioPhoneNumberCalls())))
                .findFirst();
    }

    /**
     * Finds a client by their Twilio WhatsApp phone number.
     *
     * @param phoneNumber The Twilio WhatsApp number (To field from webhook, without whatsapp: prefix)
     * @return Optional containing the TeiaUser if found
     */
    public Optional<TeiaUser> findClientByWhatsappNumber(String phoneNumber) {
        String normalizedNumber = normalizePhoneNumber(phoneNumber);
        log.debug("[TwilioWebhookService] Looking up client by WhatsApp number: {}", normalizedNumber);

        return userRepository.findAll().stream()
                .filter(user -> user.getTwilioInfo() != null)
                .filter(user -> normalizedNumber.equals(
                        normalizePhoneNumber(user.getTwilioInfo().getTwilioPhoneNumberWhatsapp())))
                .findFirst();
    }

    /**
     * Checks if a client has sufficient credits for a session.
     *
     * @param client The client to check
     * @param type   The type of usage event
     * @return true if the client has sufficient credits
     */
    public boolean hasCredits(TeiaUser client, UsageEventType type) {
        if (client.getCreditBalance() == null) {
            return false;
        }

        BigDecimal requiredCredits = type == UsageEventType.CALL
                ? getCallReservationCredits()
                : getMessageReservationCredits();

        boolean hasEnough = client.getCreditBalance().compareTo(requiredCredits) >= 0;
        log.debug("[TwilioWebhookService] Client {} credit check: balance={}, required={}, hasEnough={}",
                client.getId(), client.getCreditBalance(), requiredCredits, hasEnough);

        return hasEnough;
    }

    /**
     * Reserves credits for a session. Creates a UsageEvent and CreditTransaction.
     * Deducts credits immediately from the client's balance.
     *
     * @param client The client to reserve credits for
     * @param type   The type of usage event
     * @return The created UsageEvent with the session ID
     */
    @Transactional
    public UsageEvent reserveCredits(TeiaUser client, UsageEventType type) {
        BigDecimal reserveAmount = type == UsageEventType.CALL
                ? getCallReservationCredits()
                : getMessageReservationCredits();

        String sessionId = generateSessionId();

        log.info("[TwilioWebhookService] Reserving {} credits for client {} (session: {}, type: {})",
                reserveAmount, client.getId(), sessionId, type);

        // Create usage event
        UsageEvent usageEvent = new UsageEvent(client.getId(), type, sessionId, reserveAmount);
        usageEventRepository.save(usageEvent);

        // Create credit transaction (reservation = negative amount)
        CreditTransaction transaction = CreditTransaction.reservation(client.getId(), reserveAmount, sessionId);
        creditTransactionRepository.save(transaction);

        // Deduct from client balance
        BigDecimal newBalance = client.getCreditBalance().subtract(reserveAmount);
        client.setCreditBalance(newBalance);
        userRepository.save(client);

        log.info("[TwilioWebhookService] Reserved {} credits. Client {} new balance: {}",
                reserveAmount, client.getId(), newBalance);

        return usageEvent;
    }

    /**
     * Reports usage directly (without prior reservation).
     * Used by TEIA Core to report usage after a call/message ends.
     * Creates UsageEvent with COMPLETED status and deducts credits immediately.
     *
     * @param clientId        The client ID
     * @param type            The type of usage event (CALL or MESSAGE)
     * @param durationSeconds Duration in seconds (for CALL, null for MESSAGE)
     * @param sessionId       The session ID from TEIA Core
     * @return The created UsageEvent
     * @throws InsufficientCreditsException if the client has insufficient credits
     * @throws ClientNotFoundException      if the client is not found
     */
    @Transactional
    public UsageEvent reportUsage(Long clientId, UsageEventType type, Integer durationSeconds, String sessionId) {
        TeiaUser client = userRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException("Client not found: " + clientId));

        // Calculate credits to deduct
        BigDecimal creditsToDeduct;
        if (type == UsageEventType.MESSAGE) {
            creditsToDeduct = getMessageReservationCredits();
        } else {
            creditsToDeduct = calculateCallCredits(durationSeconds);
        }

        // Check if client has sufficient credits
        BigDecimal currentBalance = client.getCreditBalance() != null ? client.getCreditBalance() : BigDecimal.ZERO;
        if (currentBalance.compareTo(creditsToDeduct) < 0) {
            log.warn("[TwilioWebhookService] Insufficient credits for client {}: balance={}, required={}",
                    clientId, currentBalance, creditsToDeduct);
            throw new InsufficientCreditsException(
                    String.format("Insufficient credits. Balance: %s, Required: %s", currentBalance, creditsToDeduct));
        }

        log.info("[TwilioWebhookService] Reporting usage for client {}: session={}, type={}, duration={}s, credits={}",
                clientId, sessionId, type, durationSeconds, creditsToDeduct);

        // Create usage event (already completed)
        UsageEvent usageEvent = new UsageEvent(clientId, type, sessionId, creditsToDeduct);
        usageEvent.complete(durationSeconds, creditsToDeduct);
        usageEventRepository.save(usageEvent);

        // Create credit transaction (deduction)
        CreditTransaction transaction = CreditTransaction.deduction(clientId, creditsToDeduct, sessionId);
        creditTransactionRepository.save(transaction);

        // Deduct from client balance
        BigDecimal newBalance = currentBalance.subtract(creditsToDeduct);
        client.setCreditBalance(newBalance);
        userRepository.save(client);

        log.info("[TwilioWebhookService] Usage reported. Client {} new balance: {}", clientId, newBalance);

        return usageEvent;
    }

    /**
     * Reconciles credits after a session completes.
     * Refunds unused credits or charges additional credits based on actual usage.
     * Also persists provider-level telemetry (Twilio, OpenAI, ElevenLabs) when supplied.
     *
     * @param sessionId                  The session ID
     * @param actualDurationSeconds      The actual duration in seconds (null for messages)
     * @param status                     The final status (COMPLETED or FAILED)
     * @param twilioCallDurationSeconds  Twilio call duration reported by TEIA Core (nullable)
     * @param twilioSmsCount             Twilio SMS count (nullable)
     * @param openAiInputTokens          OpenAI input token count (nullable)
     * @param openAiOutputTokens         OpenAI output token count (nullable)
     * @param elevenLabsCharacters       ElevenLabs character count (nullable)
     * @return The updated UsageEvent
     */
    @Transactional
    public UsageEvent reconcileSession(String sessionId, Integer actualDurationSeconds, UsageEventStatus status,
                                       Integer twilioCallDurationSeconds, Integer twilioSmsCount,
                                       Integer openAiInputTokens, Integer openAiOutputTokens,
                                       Integer elevenLabsCharacters) {
        UsageEvent usageEvent = usageEventRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

        if (usageEvent.getStatus() != UsageEventStatus.STARTED) {
            log.warn("[TwilioWebhookService] Session {} already reconciled with status {}",
                    sessionId, usageEvent.getStatus());
            return usageEvent;
        }

        TeiaUser client = userRepository.findById(usageEvent.getClientId())
                .orElseThrow(() -> new ClientNotFoundException("Client not found: " + usageEvent.getClientId()));

        BigDecimal actualCreditsUsed;
        if (status == UsageEventStatus.FAILED) {
            // Failed sessions: refund entire reservation
            actualCreditsUsed = BigDecimal.ZERO;
        } else if (usageEvent.getType() == UsageEventType.MESSAGE) {
            // Messages: use fixed credit amount
            actualCreditsUsed = getMessageReservationCredits();
        } else {
            // Calls: calculate based on duration
            actualCreditsUsed = calculateCallCredits(actualDurationSeconds);
        }

        BigDecimal reserved = usageEvent.getCreditsReserved();
        BigDecimal difference = reserved.subtract(actualCreditsUsed);

        log.info("[TwilioWebhookService] Reconciling session {}: reserved={}, actual={}, difference={}",
                sessionId, reserved, actualCreditsUsed, difference);

        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // Refund unused credits
            CreditTransaction refund = CreditTransaction.refund(client.getId(), difference, sessionId);
            creditTransactionRepository.save(refund);
            client.setCreditBalance(client.getCreditBalance().add(difference));
            userRepository.save(client);
            log.info("[TwilioWebhookService] Refunded {} credits to client {}", difference, client.getId());
        } else if (difference.compareTo(BigDecimal.ZERO) < 0) {
            // Charge additional credits
            BigDecimal additionalCharge = difference.abs();
            CreditTransaction deduction = CreditTransaction.deduction(client.getId(), additionalCharge, sessionId);
            creditTransactionRepository.save(deduction);
            client.setCreditBalance(client.getCreditBalance().subtract(additionalCharge));
            userRepository.save(client);
            log.info("[TwilioWebhookService] Charged additional {} credits to client {}", additionalCharge, client.getId());
        }

        // Persist provider telemetry
        if (twilioCallDurationSeconds != null) usageEvent.setTwilioCallDurationSeconds(twilioCallDurationSeconds);
        if (twilioSmsCount != null) usageEvent.setTwilioSmsCount(twilioSmsCount);
        if (openAiInputTokens != null) usageEvent.setOpenAiInputTokens(openAiInputTokens);
        if (openAiOutputTokens != null) usageEvent.setOpenAiOutputTokens(openAiOutputTokens);
        if (elevenLabsCharacters != null) usageEvent.setElevenLabsCharacters(elevenLabsCharacters);

        // Update usage event
        if (status == UsageEventStatus.COMPLETED) {
            usageEvent.complete(actualDurationSeconds, actualCreditsUsed);
        } else {
            usageEvent.fail();
            usageEvent.setCreditsUsed(actualCreditsUsed);
        }
        usageEventRepository.save(usageEvent);

        return usageEvent;
    }

    /**
     * Calculates credit cost for a call based on duration.
     */
    private BigDecimal calculateCallCredits(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return BigDecimal.ZERO;
        }

        // Round up to the nearest minute
        int minutes = (durationSeconds + 59) / 60;
        return getCreditsPerMinute().multiply(new BigDecimal(minutes));
    }

    /**
     * Normalizes a phone number by removing non-digit characters except leading +.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        // Remove whatsapp: prefix if present
        String number = phoneNumber.replace("whatsapp:", "");
        // Keep only digits and leading +
        if (number.startsWith("+")) {
            return "+" + number.substring(1).replaceAll("[^0-9]", "");
        }
        return number.replaceAll("[^0-9]", "");
    }

    /**
     * Generates a unique session ID.
     */
    private String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "");
    }

    private BigDecimal getCallReservationCredits() {
        return callReservationCredits != null ? callReservationCredits : DEFAULT_CALL_CREDITS;
    }

    private BigDecimal getMessageReservationCredits() {
        return messageReservationCredits != null ? messageReservationCredits : DEFAULT_MESSAGE_CREDITS;
    }

    private BigDecimal getCreditsPerMinute() {
        return creditsPerMinute != null ? creditsPerMinute : CREDITS_PER_MINUTE;
    }

    // Exception classes
    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) {
            super(message);
        }
    }

    public static class ClientNotFoundException extends RuntimeException {
        public ClientNotFoundException(String message) {
            super(message);
        }
    }

    public static class InsufficientCreditsException extends RuntimeException {
        public InsufficientCreditsException(String message) {
            super(message);
        }
    }
}
