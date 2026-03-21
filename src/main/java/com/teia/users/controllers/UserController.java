package com.teia.users.controllers;

import com.teia.users.models.Contact;
import com.teia.users.models.TeiaUser;
import com.teia.users.services.UserService;
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
@RequestMapping("/users")
@Tag(name = "Users", description = "User management and credential association")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    @Operation(summary = "Create a new user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeiaUser> createUser(@RequestBody TeiaUser teiaUser) {
        TeiaUser createdTeiaUser = userService.createUser(teiaUser);
        return ResponseEntity.ok(createdTeiaUser);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user by ID")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeiaUser> updateUser(
            @Parameter(description = "User ID") @PathVariable Long id,
            @RequestBody TeiaUser teiaUserDetails) {
        TeiaUser updatedTeiaUser = userService.updateUser(id, teiaUserDetails);
        return ResponseEntity.ok(updatedTeiaUser);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user by ID")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID") @PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a user by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<TeiaUser> retrieveUser(
            @Parameter(description = "User ID") @PathVariable Long id) {
        TeiaUser teiaUser = userService.retrieveUser(id);
        return ResponseEntity.ok(teiaUser);
    }

    @GetMapping
    @Operation(summary = "Retrieve all users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TeiaUser>> retrieveAllUsers() {
        List<TeiaUser> teiaUsers = userService.retrieveAllUsers();
        return ResponseEntity.ok(teiaUsers);
    }

    @GetMapping("/{id}/contacts")
    @Operation(summary = "Retrieve all contacts for a user")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<List<Contact>> retrieveUserContacts(
            @Parameter(description = "User ID") @PathVariable Long id) {
        List<Contact> contacts = userService.retrieveUserContacts(id);
        return ResponseEntity.ok(contacts);
    }

    @PostMapping("/{id}/contacts")
    @Operation(summary = "Add a contact to a user")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<TeiaUser> addContactToUser(
            @Parameter(description = "User ID") @PathVariable Long id,
            @RequestBody Contact contact) {
        TeiaUser teiaUser = userService.addContactToUser(id, contact);
        return ResponseEntity.ok(teiaUser);
    }

    @DeleteMapping("/{userId}/contacts/{contactId}")
    @Operation(summary = "Remove a contact from a user")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<TeiaUser> deleteContactFromUser(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Contact ID") @PathVariable Long contactId) {
        TeiaUser teiaUser = userService.deleteContactFromUser(userId, contactId);
        return ResponseEntity.ok(teiaUser);
    }
}