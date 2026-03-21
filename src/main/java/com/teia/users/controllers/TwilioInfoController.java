package com.teia.users.controllers;

import com.teia.users.models.TwilioInfo;
import com.teia.users.services.TwilioInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/twilio-info")
@Tag(name = "Twilio", description = "Twilio telephony credentials")
public class TwilioInfoController {

    @Autowired
    private TwilioInfoService twilioInfoService;

    @PostMapping
    @Operation(summary = "Create Twilio credentials")
    public ResponseEntity<TwilioInfo> create(@RequestBody TwilioInfo twilioInfo) {
        TwilioInfo created = twilioInfoService.create(twilioInfo);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Twilio credentials by ID")
    public ResponseEntity<TwilioInfo> update(
            @Parameter(description = "Twilio info ID") @PathVariable Long id,
            @RequestBody TwilioInfo twilioInfoDetails) {
        TwilioInfo updated = twilioInfoService.update(id, twilioInfoDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Twilio credentials by ID")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Twilio info ID") @PathVariable Long id) {
        twilioInfoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve Twilio credentials by ID")
    public ResponseEntity<TwilioInfo> retrieve(
            @Parameter(description = "Twilio info ID") @PathVariable Long id) {
        TwilioInfo twilioInfo = twilioInfoService.retrieve(id);
        return ResponseEntity.ok(twilioInfo);
    }

    @GetMapping
    @Operation(summary = "Retrieve all Twilio credentials")
    public ResponseEntity<List<TwilioInfo>> retrieveAll() {
        List<TwilioInfo> twilioInfos = twilioInfoService.retrieveAll();
        return ResponseEntity.ok(twilioInfos);
    }
}
