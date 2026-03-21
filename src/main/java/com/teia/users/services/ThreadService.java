package com.teia.users.services;

import com.teia.users.models.Thread;
import com.teia.users.models.ThreadId;
import com.teia.users.repositories.ThreadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ThreadService {

    @Autowired
    private ThreadRepository threadRepository;

    public Thread create(Thread thread) {
        return threadRepository.save(thread);
    }

    public void delete(String toPhoneNumber, String fromPhoneNumber, String threadId) {
        ThreadId id = new ThreadId(toPhoneNumber, fromPhoneNumber, threadId);
        threadRepository.deleteById(id);
    }

    public Thread retrieve(String toPhoneNumber, String fromPhoneNumber, String threadId) {
        ThreadId id = new ThreadId(toPhoneNumber, fromPhoneNumber, threadId);
        return threadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thread not found"));
    }

    public List<Thread> retrieveAll() {
        return threadRepository.findAll();
    }

    public List<Thread> findByToPhoneNumber(String toPhoneNumber) {
        return threadRepository.findByIdToPhoneNumber(toPhoneNumber);
    }

    public List<Thread> findByFromPhoneNumber(String fromPhoneNumber) {
        return threadRepository.findByIdFromPhoneNumber(fromPhoneNumber);
    }
}
