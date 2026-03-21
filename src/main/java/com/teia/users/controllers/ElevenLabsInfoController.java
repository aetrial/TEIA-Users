package com.teia.users.controllers;

import com.teia.users.models.ElevenLabsInfo;
import com.teia.users.services.ElevenLabsInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/eleven-labs-info")
@Tag(name = "ElevenLabs", description = "ElevenLabs voice AI credentials")
public class ElevenLabsInfoController {

    @Autowired
    private ElevenLabsInfoService elevenLabsInfoService;

    @PostMapping
    @Operation(summary = "Create ElevenLabs credentials")
    public ResponseEntity<ElevenLabsInfo> create(@RequestBody ElevenLabsInfo elevenLabsInfo) {
        ElevenLabsInfo created = elevenLabsInfoService.create(elevenLabsInfo);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ElevenLabs credentials by ID")
    public ResponseEntity<ElevenLabsInfo> update(
            @Parameter(description = "ElevenLabs info ID") @PathVariable Long id,
            @RequestBody ElevenLabsInfo elevenLabsInfoDetails) {
        ElevenLabsInfo updated = elevenLabsInfoService.update(id, elevenLabsInfoDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete ElevenLabs credentials by ID")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ElevenLabs info ID") @PathVariable Long id) {
        elevenLabsInfoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve ElevenLabs credentials by ID")
    public ResponseEntity<ElevenLabsInfo> retrieve(
            @Parameter(description = "ElevenLabs info ID") @PathVariable Long id) {
        ElevenLabsInfo elevenLabsInfo = elevenLabsInfoService.retrieve(id);
        return ResponseEntity.ok(elevenLabsInfo);
    }

    @GetMapping
    @Operation(summary = "Retrieve all ElevenLabs credentials")
    public ResponseEntity<List<ElevenLabsInfo>> retrieveAll() {
        List<ElevenLabsInfo> elevenLabsInfos = elevenLabsInfoService.retrieveAll();
        return ResponseEntity.ok(elevenLabsInfos);
    }
}
