package com.teia.users.services;

import com.teia.users.dto.*;
import com.teia.users.exception.ForbiddenException;
import com.teia.users.models.CreditTransaction;
import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.CreditTransactionType;
import com.teia.users.models.enums.Role;
import com.teia.users.repositories.CreditTransactionRepository;
import com.teia.users.repositories.UsageEventRepository;
import com.teia.users.repositories.UserRepository;
import com.teia.users.security.JwtUserPrincipal;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentCollection;
import com.stripe.param.PaymentIntentListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProviderMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ProviderMetricsService.class);

    private final UsageEventRepository usageEventRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final UserRepository userRepository;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    public ProviderMetricsService(UsageEventRepository usageEventRepository,
                                   CreditTransactionRepository creditTransactionRepository,
                                   UserRepository userRepository) {
        this.usageEventRepository = usageEventRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Resolves the effective clientId with strict CLIENT enforcement.
     * - CLIENT: must be own userId (throws ForbiddenException if requestedClientId differs)
     * - OPERATOR/ADMIN: uses requestedClientId if provided, else own userId
     */
    public Long resolveClientIdStrict(Long requestedClientId) {
        JwtUserPrincipal principal = (JwtUserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        if (principal.getRole() == Role.CLIENT) {
            if (requestedClientId == null || requestedClientId.equals(principal.getUserId())) {
                return principal.getUserId();
            }
            throw new ForbiddenException("Clients may only access their own metrics.");
        }

        return requestedClientId != null ? requestedClientId : principal.getUserId();
    }

    // -------------------------------------------------------------------------
    // Summary
    // -------------------------------------------------------------------------

    public ProviderSummaryResponse getSummary(Long clientId,
                                               LocalDateTime fromDateTime,
                                               LocalDateTime toDateTime) {
        Long effectiveClientId = resolveClientIdStrict(clientId);

        long callSeconds = usageEventRepository.sumTwilioCallDurationSeconds(effectiveClientId, fromDateTime, toDateTime);
        long smsCount    = usageEventRepository.sumTwilioSmsCount(effectiveClientId, fromDateTime, toDateTime);
        long inputTokens = usageEventRepository.sumOpenAiInputTokens(effectiveClientId, fromDateTime, toDateTime);
        long outputTokens = usageEventRepository.sumOpenAiOutputTokens(effectiveClientId, fromDateTime, toDateTime);
        long characters  = usageEventRepository.sumElevenLabsCharacters(effectiveClientId, fromDateTime, toDateTime);

        // Stripe summary from CreditTransactions of type PURCHASE
        List<CreditTransaction> purchases = creditTransactionRepository
                .findByClientIdAndType(effectiveClientId, CreditTransactionType.PURCHASE);

        BigDecimal totalPaid = purchases.stream()
                .map(CreditTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime lastPaymentAt = purchases.stream()
                .map(CreditTransaction::getCreatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return ProviderSummaryResponse.builder()
                .twilio(ProviderSummaryResponse.TwilioSummary.builder()
                        .totalCallMinutes(secondsToMinutes(callSeconds))
                        .totalSmsSent(smsCount)
                        .build())
                .openAi(ProviderSummaryResponse.OpenAiSummary.builder()
                        .totalInputTokens(inputTokens)
                        .totalOutputTokens(outputTokens)
                        .build())
                .elevenLabs(ProviderSummaryResponse.ElevenLabsSummary.builder()
                        .totalCharacters(characters)
                        .build())
                .stripe(ProviderSummaryResponse.StripeSummary.builder()
                        .totalPaid(totalPaid)
                        .currency("usd")
                        .lastPaymentAt(lastPaymentAt)
                        .build())
                .build();
    }

    // -------------------------------------------------------------------------
    // Twilio
    // -------------------------------------------------------------------------

    public TwilioMetricsResponse getTwilioMetrics(Long clientId,
                                                   LocalDateTime fromDateTime,
                                                   LocalDateTime toDateTime) {
        Long effectiveClientId = resolveClientIdStrict(clientId);

        List<UsageEvent> events = usageEventRepository.findByClientIdAndDateRange(
                effectiveClientId, fromDateTime, toDateTime);

        List<TwilioMetricsResponse.TwilioSessionMetric> sessions = events.stream()
                .map(e -> TwilioMetricsResponse.TwilioSessionMetric.builder()
                        .sessionId(e.getSessionId())
                        .callDurationSeconds(e.getTwilioCallDurationSeconds())
                        .smsCount(e.getTwilioSmsCount())
                        .timestamp(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        long totalCallSeconds = events.stream()
                .mapToLong(e -> e.getTwilioCallDurationSeconds() != null ? e.getTwilioCallDurationSeconds() : 0L)
                .sum();
        long totalSms = events.stream()
                .mapToLong(e -> e.getTwilioSmsCount() != null ? e.getTwilioSmsCount() : 0L)
                .sum();

        return TwilioMetricsResponse.builder()
                .sessions(sessions)
                .aggregate(TwilioMetricsResponse.TwilioAggregate.builder()
                        .totalCallMinutes(secondsToMinutes(totalCallSeconds))
                        .totalSmsSent(totalSms)
                        .build())
                .build();
    }

    // -------------------------------------------------------------------------
    // OpenAI
    // -------------------------------------------------------------------------

    public OpenAiMetricsResponse getOpenAiMetrics(Long clientId,
                                                   LocalDateTime fromDateTime,
                                                   LocalDateTime toDateTime) {
        Long effectiveClientId = resolveClientIdStrict(clientId);

        List<UsageEvent> events = usageEventRepository.findByClientIdAndDateRange(
                effectiveClientId, fromDateTime, toDateTime);

        List<OpenAiMetricsResponse.OpenAiSessionMetric> sessions = events.stream()
                .map(e -> OpenAiMetricsResponse.OpenAiSessionMetric.builder()
                        .sessionId(e.getSessionId())
                        .inputTokens(e.getOpenAiInputTokens())
                        .outputTokens(e.getOpenAiOutputTokens())
                        .timestamp(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        long totalInput = events.stream()
                .mapToLong(e -> e.getOpenAiInputTokens() != null ? e.getOpenAiInputTokens() : 0L)
                .sum();
        long totalOutput = events.stream()
                .mapToLong(e -> e.getOpenAiOutputTokens() != null ? e.getOpenAiOutputTokens() : 0L)
                .sum();

        return OpenAiMetricsResponse.builder()
                .sessions(sessions)
                .aggregate(OpenAiMetricsResponse.OpenAiAggregate.builder()
                        .totalInputTokens(totalInput)
                        .totalOutputTokens(totalOutput)
                        .build())
                .build();
    }

    // -------------------------------------------------------------------------
    // ElevenLabs
    // -------------------------------------------------------------------------

    public ElevenLabsMetricsResponse getElevenLabsMetrics(Long clientId,
                                                           LocalDateTime fromDateTime,
                                                           LocalDateTime toDateTime) {
        Long effectiveClientId = resolveClientIdStrict(clientId);

        List<UsageEvent> events = usageEventRepository.findByClientIdAndDateRange(
                effectiveClientId, fromDateTime, toDateTime);

        List<ElevenLabsMetricsResponse.ElevenLabsSessionMetric> sessions = events.stream()
                .map(e -> ElevenLabsMetricsResponse.ElevenLabsSessionMetric.builder()
                        .sessionId(e.getSessionId())
                        .characters(e.getElevenLabsCharacters())
                        .timestamp(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        long totalChars = events.stream()
                .mapToLong(e -> e.getElevenLabsCharacters() != null ? e.getElevenLabsCharacters() : 0L)
                .sum();

        return ElevenLabsMetricsResponse.builder()
                .sessions(sessions)
                .aggregate(ElevenLabsMetricsResponse.ElevenLabsAggregate.builder()
                        .totalCharacters(totalChars)
                        .build())
                .build();
    }

    // -------------------------------------------------------------------------
    // Stripe
    // -------------------------------------------------------------------------

    public List<StripePaymentResponse> getStripePayments(Long clientId) {
        Long effectiveClientId = resolveClientIdStrict(clientId);

        // Local purchase transactions (source of truth for creditsPurchased)
        List<CreditTransaction> purchases = creditTransactionRepository
                .findByClientIdAndTypeOrderByCreatedAtDesc(
                        effectiveClientId, CreditTransactionType.PURCHASE, PageRequest.of(0, 20));

        Map<String, StripePaymentResponse> resultMap = new LinkedHashMap<>();

        for (CreditTransaction tx : purchases) {
            StripePaymentResponse entry = StripePaymentResponse.builder()
                    .paymentIntentId(tx.getReferenceId())
                    .amount(tx.getAmount())
                    .currency("usd")
                    .status("succeeded")
                    .creditsPurchased(tx.getAmount())
                    .createdAt(tx.getCreatedAt())
                    .build();
            if (tx.getReferenceId() != null) {
                resultMap.put(tx.getReferenceId(), entry);
            } else {
                // No referenceId — add with synthetic key to preserve order
                resultMap.put("local_" + tx.getId(), entry);
            }
        }

        // Enrich with live Stripe data if stripeCustomerId is present
        String stripeCustomerId = userRepository.findById(effectiveClientId)
                .map(u -> u.getStripeCustomerId())
                .orElse(null);

        if (stripeCustomerId != null && !stripeCustomerId.isBlank() && !stripeSecretKey.isBlank()) {
            try {
                Stripe.apiKey = stripeSecretKey;
                PaymentIntentListParams params = PaymentIntentListParams.builder()
                        .setCustomer(stripeCustomerId)
                        .setLimit(10L)
                        .build();
                PaymentIntentCollection piList = PaymentIntent.list(params);

                for (PaymentIntent pi : piList.getData()) {
                    LocalDateTime piCreatedAt = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(pi.getCreated()), ZoneOffset.UTC);
                    BigDecimal piAmount = BigDecimal.valueOf(pi.getAmount()).movePointLeft(2);

                    if (resultMap.containsKey(pi.getId())) {
                        // Merge: update status from live Stripe data
                        StripePaymentResponse existing = resultMap.get(pi.getId());
                        existing.setStatus(pi.getStatus());
                        existing.setCurrency(pi.getCurrency());
                    } else {
                        // Stripe-only entry (no matching local transaction)
                        resultMap.put(pi.getId(), StripePaymentResponse.builder()
                                .paymentIntentId(pi.getId())
                                .amount(piAmount)
                                .currency(pi.getCurrency())
                                .status(pi.getStatus())
                                .creditsPurchased(null)
                                .createdAt(piCreatedAt)
                                .build());
                    }
                }
            } catch (StripeException e) {
                log.warn("[ProviderMetricsService] Stripe API error for customer {}: {}", stripeCustomerId, e.getMessage());
                // Return partial data — local transactions are already in resultMap
            }
        }

        // Sort by createdAt DESC, return last 20
        return resultMap.values().stream()
                .sorted(Comparator.comparing(StripePaymentResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private long secondsToMinutes(long seconds) {
        return (seconds + 59) / 60;
    }
}
