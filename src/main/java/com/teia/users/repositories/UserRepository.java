package com.teia.users.repositories;

import com.teia.users.models.TeiaUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<TeiaUser, Long> {
}