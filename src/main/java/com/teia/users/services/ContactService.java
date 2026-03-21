package com.teia.users.services;

import com.teia.users.models.Contact;
import com.teia.users.repositories.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    @Autowired
    private ContactRepository contactRepository;

    public Contact create(Contact contact) {
        contact.setCreateDate(LocalDateTime.now());
        contact.setUpdateDate(LocalDateTime.now());
        return contactRepository.save(contact);
    }

    public Contact update(Long id, Contact contactDetails) {
        Optional<Contact> optional = contactRepository.findById(id);
        if (optional.isPresent()) {
            Contact contact = optional.get();

            if (contactDetails.getName() != null) {
                contact.setName(contactDetails.getName());
            }
            if (contactDetails.getPhoneNumber() != null) {
                contact.setPhoneNumber(contactDetails.getPhoneNumber());
            }
            if (contactDetails.getChannel() != null) {
                contact.setChannel(contactDetails.getChannel());
            }
            if (contactDetails.getStatus() != null) {
                contact.setStatus(contactDetails.getStatus());
            }

            contact.setUpdateDate(LocalDateTime.now());
            return contactRepository.save(contact);
        } else {
            throw new RuntimeException("Contact not found with id " + id);
        }
    }

    public void delete(Long id) {
        contactRepository.deleteById(id);
    }

    public Contact retrieve(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found with id " + id));
    }

    public List<Contact> retrieveAll() {
        return contactRepository.findAll();
    }
}
