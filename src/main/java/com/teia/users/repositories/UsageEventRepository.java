package com.teia.users.repositories;

import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.models.enums.UsageEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    Optional<UsageEvent> findBySessionId(String sessionId);

    List<UsageEvent> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<UsageEvent> findByClientIdAndStatus(Long clientId, UsageEventStatus status);

    boolean existsBySessionId(String sessionId);

    // Metrics: count by client and type
    long countByClientIdAndType(Long clientId, UsageEventType type);

    // Metrics: count active sessions (status = STARTED)
    long countByClientIdAndStatus(Long clientId, UsageEventStatus status);

    // Metrics: sum of creditsUsed for completed events
    @Query("SELECT COALESCE(SUM(u.creditsUsed), 0) FROM UsageEvent u WHERE u.clientId = :clientId AND u.status = 'COMPLETED'")
    BigDecimal sumCreditsUsedByClientId(@Param("clientId") Long clientId);

    // Metrics: calls within date range
    @Query("SELECT u FROM UsageEvent u WHERE u.clientId = :clientId AND u.type = 'CALL' " +
           "AND u.createdAt >= :fromDate AND u.createdAt < :toDate ORDER BY u.createdAt DESC")
    List<UsageEvent> findCallsByClientIdAndDateRange(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Metrics: messages within date range
    @Query("SELECT u FROM UsageEvent u WHERE u.clientId = :clientId AND u.type = 'MESSAGE' " +
           "AND u.createdAt >= :fromDate AND u.createdAt < :toDate ORDER BY u.createdAt DESC")
    List<UsageEvent> findMessagesByClientIdAndDateRange(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Metrics: all completed events within date range for aggregation
    @Query("SELECT u FROM UsageEvent u WHERE u.clientId = :clientId AND u.status = 'COMPLETED' " +
           "AND u.createdAt >= :fromDate AND u.createdAt < :toDate ORDER BY u.createdAt ASC")
    List<UsageEvent> findCompletedEventsByClientIdAndDateRange(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Provider metrics: all events within date range (for per-session detail)
    @Query("SELECT u FROM UsageEvent u WHERE u.clientId = :clientId " +
           "AND u.createdAt >= :fromDate AND u.createdAt < :toDate ORDER BY u.createdAt DESC")
    List<UsageEvent> findByClientIdAndDateRange(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Provider metrics: sum Twilio call seconds
    @Query("SELECT COALESCE(SUM(u.twilioCallDurationSeconds), 0) FROM UsageEvent u " +
           "WHERE u.clientId = :clientId AND u.createdAt >= :fromDate AND u.createdAt < :toDate")
    long sumTwilioCallDurationSeconds(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Provider metrics: sum Twilio SMS count
    @Query("SELECT COALESCE(SUM(u.twilioSmsCount), 0) FROM UsageEvent u " +
           "WHERE u.clientId = :clientId AND u.createdAt >= :fromDate AND u.createdAt < :toDate")
    long sumTwilioSmsCount(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Provider metrics: sum OpenAI input tokens
    @Query("SELECT COALESCE(SUM(u.openAiInputTokens), 0) FROM UsageEvent u " +
           "WHERE u.clientId = :clientId AND u.createdAt >= :fromDate AND u.createdAt < :toDate")
    long sumOpenAiInputTokens(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Provider metrics: sum OpenAI output tokens
    @Query("SELECT COALESCE(SUM(u.openAiOutputTokens), 0) FROM UsageEvent u " +
           "WHERE u.clientId = :clientId AND u.createdAt >= :fromDate AND u.createdAt < :toDate")
    long sumOpenAiOutputTokens(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Provider metrics: sum ElevenLabs characters
    @Query("SELECT COALESCE(SUM(u.elevenLabsCharacters), 0) FROM UsageEvent u " +
           "WHERE u.clientId = :clientId AND u.createdAt >= :fromDate AND u.createdAt < :toDate")
    long sumElevenLabsCharacters(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);
}
