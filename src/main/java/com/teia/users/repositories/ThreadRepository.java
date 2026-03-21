package com.teia.users.repositories;

import com.teia.users.models.Thread;
import com.teia.users.models.ThreadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThreadRepository extends JpaRepository<Thread, ThreadId> {

    List<Thread> findByIdToPhoneNumber(String toPhoneNumber);

    List<Thread> findByIdFromPhoneNumber(String fromPhoneNumber);
}
