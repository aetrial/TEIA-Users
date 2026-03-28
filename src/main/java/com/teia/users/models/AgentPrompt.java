package com.teia.users.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a system prompt version for an AI agent.
 * Each client can have multiple versions, but only one active at a time.
 */
@Entity
@Table(name = "agent_prompt", indexes = {
    @Index(name = "idx_agent_prompt_client_id", columnList = "client_id"),
    @Index(name = "idx_agent_prompt_client_active", columnList = "client_id, is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false, length = 4000)
    private String content;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
