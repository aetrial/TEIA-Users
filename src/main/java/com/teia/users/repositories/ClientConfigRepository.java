package com.teia.users.repositories;

import com.teia.users.models.ClientConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientConfigRepository extends JpaRepository<ClientConfig, Long> {

    Optional<ClientConfig> findByClientId(Long clientId);

    boolean existsByClientId(Long clientId);

    void deleteByClientId(Long clientId);
}
