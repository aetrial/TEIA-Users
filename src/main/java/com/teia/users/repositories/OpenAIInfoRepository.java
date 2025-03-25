package com.teia.users.repositories;

import com.teia.users.models.OpenAIInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpenAIInfoRepository extends JpaRepository<OpenAIInfo, Long> {
}