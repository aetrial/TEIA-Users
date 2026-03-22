package com.teia.users.models;

import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.models.enums.UsageEventType;
import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks usage events (calls and messages) for billing purposes.
 * Each event starts with a credit reservation and is reconciled upon completion.
 */
@Entity
@Table(name = "usage_event", indexes = {
    @Index(name = "idx_usage_event_client_id", columnList = "client_id"),
    @Index(name = "idx_usage_event_session_id", columnList = "session_id", unique = true),
    @Index(name = "idx_usage_event_status", columnList = "status"),
    @Index(name = "idx_usage_event_created_at", columnList = "created_at")
})
@Data
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private UsageEventType type;

    @Column(name = "session_id", length = 100, nullable = false, unique = true)
    private String sessionId;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "credits_reserved", precision = 12, scale = 2, nullable = false)
    private BigDecimal creditsReserved;

    @Column(name = "credits_used", precision = 12, scale = 2)
    private BigDecimal creditsUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UsageEventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public UsageEvent() {
        this.createdAt = LocalDateTime.now();
        this.status = UsageEventStatus.STARTED;
    }

    /**
     * Creates a new usage event with initial credit reservation.
     */
    public UsageEvent(Long clientId, UsageEventType type, String sessionId, BigDecimal creditsReserved) {
        this.clientId = clientId;
        this.type = type;
        this.sessionId = sessionId;
        this.creditsReserved = creditsReserved;
        this.status = UsageEventStatus.STARTED;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Marks the event as completed with actual usage.
     */
    public void complete(Integer durationSeconds, BigDecimal creditsUsed) {
        this.durationSeconds = durationSeconds;
        this.creditsUsed = creditsUsed;
        this.status = UsageEventStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks the event as failed.
     */
    public void fail() {
        this.status = UsageEventStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
