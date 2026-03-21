package com.teia.users.controllers;

import com.teia.users.models.OpenAIInfo;
import com.teia.users.services.OpenAIInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/openai-info")
@Tag(name = "OpenAI", description = "OpenAI assistant credentials")
public class OpenAIInfoController {

    @Autowired
    private OpenAIInfoService openAIInfoService;

    @PostMapping
    @Operation(summary = "Create OpenAI credentials")
    public ResponseEntity<OpenAIInfo> create(@RequestBody OpenAIInfo openAIInfo) {
        OpenAIInfo created = openAIInfoService.create(openAIInfo);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update OpenAI credentials by ID")
    public ResponseEntity<OpenAIInfo> update(
            @Parameter(description = "OpenAI info ID") @PathVariable Long id,
            @RequestBody OpenAIInfo openAIInfoDetails) {
        OpenAIInfo updated = openAIInfoService.update(id, openAIInfoDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete OpenAI credentials by ID")
    public ResponseEntity<Void> delete(
            @Parameter(description = "OpenAI info ID") @PathVariable Long id) {
        openAIInfoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve OpenAI credentials by ID")
    public ResponseEntity<OpenAIInfo> retrieve(
            @Parameter(description = "OpenAI info ID") @PathVariable Long id) {
        OpenAIInfo openAIInfo = openAIInfoService.retrieve(id);
        return ResponseEntity.ok(openAIInfo);
    }

    @GetMapping
    @Operation(summary = "Retrieve all OpenAI credentials")
    public ResponseEntity<List<OpenAIInfo>> retrieveAll() {
        List<OpenAIInfo> openAIInfos = openAIInfoService.retrieveAll();
        return ResponseEntity.ok(openAIInfos);
    }
}
