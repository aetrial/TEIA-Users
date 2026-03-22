package com.teia.users.repositories;

import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.UsageEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    Optional<UsageEvent> findBySessionId(String sessionId);

    List<UsageEvent> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<UsageEvent> findByClientIdAndStatus(Long clientId, UsageEventStatus status);

    boolean existsBySessionId(String sessionId);
}
