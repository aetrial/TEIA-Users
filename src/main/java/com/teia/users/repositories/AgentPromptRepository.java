package com.teia.users.repositories;

import com.teia.users.models.AgentPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AgentPrompt entity.
 */
@Repository
public interface AgentPromptRepository extends JpaRepository<AgentPrompt, Long> {

    /**
     * Find the currently active prompt for a client.
     */
    @Query("SELECT ap FROM AgentPrompt ap WHERE ap.clientId = :clientId AND ap.isActive = true")
    Optional<AgentPrompt> findActiveByClientId(@Param("clientId") Long clientId);

    /**
     * Find all prompts for a client, ordered by version descending.
     */
    @Query("SELECT ap FROM AgentPrompt ap WHERE ap.clientId = :clientId ORDER BY ap.version DESC")
    List<AgentPrompt> findAllByClientIdOrderByVersionDesc(@Param("clientId") Long clientId);

    /**
     * Get the maximum version number for a client.
     */
    @Query("SELECT COALESCE(MAX(ap.version), 0) FROM AgentPrompt ap WHERE ap.clientId = :clientId")
    Integer findMaxVersionByClientId(@Param("clientId") Long clientId);

    /**
     * Deactivate all prompts for a client.
     */
    @Modifying
    @Query("UPDATE AgentPrompt ap SET ap.isActive = false WHERE ap.clientId = :clientId")
    void deactivateAllByClientId(@Param("clientId") Long clientId);

    /**
     * Find a prompt by ID and client ID.
     */
    @Query("SELECT ap FROM AgentPrompt ap WHERE ap.id = :id AND ap.clientId = :clientId")
    Optional<AgentPrompt> findByIdAndClientId(@Param("id") Long id, @Param("clientId") Long clientId);
}
