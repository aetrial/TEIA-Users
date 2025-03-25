package com.teia.users.repositories;

import com.teia.users.models.ElevenLabsInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ElevenLabsInfoRepository extends JpaRepository<ElevenLabsInfo, Long> {
}