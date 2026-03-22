package com.teia.users.repositories;

import com.teia.users.models.CreditTransaction;
import com.teia.users.models.enums.CreditTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<CreditTransaction> findByClientIdAndType(Long clientId, CreditTransactionType type);

    List<CreditTransaction> findByReferenceId(String referenceId);
}
