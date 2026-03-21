package com.teia.users.controllers;

import com.teia.users.models.Thread;
import com.teia.users.services.ThreadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/threads")
@Tag(name = "Threads", description = "OpenAI conversation thread tracking")
public class ThreadController {

    @Autowired
    private ThreadService threadService;

    @PostMapping
    @Operation(summary = "Create a new conversation thread")
    public ResponseEntity<Thread> create(@RequestBody Thread thread) {
        Thread created = threadService.create(thread);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping
    @Operation(summary = "Delete a thread by phone numbers and thread ID")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Destination phone number") @RequestParam String toPhoneNumber,
            @Parameter(description = "Source phone number") @RequestParam String fromPhoneNumber,
            @Parameter(description = "OpenAI thread ID") @RequestParam String threadId) {
        threadService.delete(toPhoneNumber, fromPhoneNumber, threadId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Retrieve all conversation threads")
    public ResponseEntity<List<Thread>> retrieveAll() {
        List<Thread> threads = threadService.retrieveAll();
        return ResponseEntity.ok(threads);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Look up a specific thread by phone numbers and thread ID")
    public ResponseEntity<Thread> retrieve(
            @Parameter(description = "Destination phone number") @RequestParam String toPhoneNumber,
            @Parameter(description = "Source phone number") @RequestParam String fromPhoneNumber,
            @Parameter(description = "OpenAI thread ID") @RequestParam String threadId) {
        Thread thread = threadService.retrieve(toPhoneNumber, fromPhoneNumber, threadId);
        return ResponseEntity.ok(thread);
    }

    @GetMapping("/by-to-phone")
    @Operation(summary = "Find threads by destination phone number")
    public ResponseEntity<List<Thread>> findByToPhoneNumber(
            @Parameter(description = "Destination phone number") @RequestParam String toPhoneNumber) {
        List<Thread> threads = threadService.findByToPhoneNumber(toPhoneNumber);
        return ResponseEntity.ok(threads);
    }

    @GetMapping("/by-from-phone")
    @Operation(summary = "Find threads by source phone number")
    public ResponseEntity<List<Thread>> findByFromPhoneNumber(
            @Parameter(description = "Source phone number") @RequestParam String fromPhoneNumber) {
        List<Thread> threads = threadService.findByFromPhoneNumber(fromPhoneNumber);
        return ResponseEntity.ok(threads);
    }
}
