package com.teia.users.controllers;

import com.teia.users.models.Contact;
import com.teia.users.models.TeiaUser;
import com.teia.users.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<TeiaUser> createUser(@RequestBody TeiaUser teiaUser) {
        TeiaUser createdTeiaUser = userService.createUser(teiaUser);
        return ResponseEntity.ok(createdTeiaUser);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeiaUser> updateUser(@PathVariable Long id, @RequestBody TeiaUser teiaUserDetails) {
        TeiaUser updatedTeiaUser = userService.updateUser(id, teiaUserDetails);
        return ResponseEntity.ok(updatedTeiaUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeiaUser> retrieveUser(@PathVariable Long id) {
        TeiaUser teiaUser = userService.retrieveUser(id);
        return ResponseEntity.ok(teiaUser);
    }

    @GetMapping
    public ResponseEntity<List<TeiaUser>> retrieveAllUsers() {
        List<TeiaUser> teiaUsers = userService.retrieveAllUsers();
        return ResponseEntity.ok(teiaUsers);
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<List<Contact>> retrieveUserContacts(@PathVariable Long id) {
        List<Contact> contacts = userService.retrieveUserContacts(id);
        return ResponseEntity.ok(contacts);
    }

    @PostMapping("/{id}/contacts")
    public ResponseEntity<TeiaUser> addContactToUser(@PathVariable Long id, @RequestBody Contact contact) {
        TeiaUser teiaUser = userService.addContactToUser(id, contact);
        return ResponseEntity.ok(teiaUser);
    }

    @DeleteMapping("/{userId}/contacts/{contactId}")
    public ResponseEntity<TeiaUser> deleteContactFromUser(@PathVariable Long userId, @PathVariable Long contactId) {
        TeiaUser teiaUser = userService.deleteContactFromUser(userId, contactId);
        return ResponseEntity.ok(teiaUser);
    }
}