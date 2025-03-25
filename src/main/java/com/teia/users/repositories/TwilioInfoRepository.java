package com.teia.users.repositories;

import com.teia.users.models.TwilioInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TwilioInfoRepository extends JpaRepository<TwilioInfo, Long> {
}