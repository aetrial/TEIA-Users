package com.teia.users.controllers;

import com.teia.users.models.CalInfo;
import com.teia.users.services.CalInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cal-info")
@Tag(name = "Calendar", description = "Cal.com calendar integration credentials")
public class CalInfoController {

    @Autowired
    private CalInfoService calInfoService;

    @PostMapping
    @Operation(summary = "Create Cal.com credentials")
    public ResponseEntity<CalInfo> create(@RequestBody CalInfo calInfo) {
        CalInfo created = calInfoService.create(calInfo);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Cal.com credentials by ID")
    public ResponseEntity<CalInfo> update(
            @Parameter(description = "Cal info ID") @PathVariable Long id,
            @RequestBody CalInfo calInfoDetails) {
        CalInfo updated = calInfoService.update(id, calInfoDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Cal.com credentials by ID")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Cal info ID") @PathVariable Long id) {
        calInfoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve Cal.com credentials by ID")
    public ResponseEntity<CalInfo> retrieve(
            @Parameter(description = "Cal info ID") @PathVariable Long id) {
        CalInfo calInfo = calInfoService.retrieve(id);
        return ResponseEntity.ok(calInfo);
    }

    @GetMapping
    @Operation(summary = "Retrieve all Cal.com credentials")
    public ResponseEntity<List<CalInfo>> retrieveAll() {
        List<CalInfo> calInfos = calInfoService.retrieveAll();
        return ResponseEntity.ok(calInfos);
    }
}
