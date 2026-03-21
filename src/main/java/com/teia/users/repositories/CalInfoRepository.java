package com.teia.users.repositories;

import com.teia.users.models.CalInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CalInfoRepository extends JpaRepository<CalInfo, Long> {
}
