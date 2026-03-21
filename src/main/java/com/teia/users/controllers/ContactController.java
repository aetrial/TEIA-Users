package com.teia.users.controllers;

import com.teia.users.models.Contact;
import com.teia.users.services.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contacts")
@Tag(name = "Contacts", description = "Contact management")
@SecurityRequirement(name = "bearerAuth")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @PostMapping
    @Operation(summary = "Create a new contact")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Contact> create(@RequestBody Contact contact) {
        Contact created = contactService.create(contact);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing contact by ID")
    public ResponseEntity<Contact> update(
            @Parameter(description = "Contact ID") @PathVariable Long id,
            @RequestBody Contact contactDetails) {
        Contact updated = contactService.update(id, contactDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a contact by ID")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Contact ID") @PathVariable Long id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a contact by ID")
    public ResponseEntity<Contact> retrieve(
            @Parameter(description = "Contact ID") @PathVariable Long id) {
        Contact contact = contactService.retrieve(id);
        return ResponseEntity.ok(contact);
    }

    @GetMapping
    @Operation(summary = "Retrieve all contacts")
    public ResponseEntity<List<Contact>> retrieveAll() {
        List<Contact> contacts = contactService.retrieveAll();
        return ResponseEntity.ok(contacts);
    }
}
