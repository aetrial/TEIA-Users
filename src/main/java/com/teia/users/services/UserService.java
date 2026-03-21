package com.teia.users.services;

import com.teia.users.models.Contact;
import com.teia.users.models.TeiaUser;
import com.teia.users.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TwilioInfoRepository twilioInfoRepository;

    @Autowired
    private ElevenLabsInfoRepository elevenLabsInfoRepository;

    @Autowired
    private OpenAIInfoRepository openAIInfoRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private CalInfoRepository calInfoRepository;

    public TeiaUser createUser(TeiaUser teiaUser) {
        if (teiaUser.getTwilioInfo() != null) {
            twilioInfoRepository.save(teiaUser.getTwilioInfo());
        }
        if (teiaUser.getElevenLabsInfo() != null) {
            elevenLabsInfoRepository.save(teiaUser.getElevenLabsInfo());
        }
        if (teiaUser.getOpenAIInfo() != null) {
            openAIInfoRepository.save(teiaUser.getOpenAIInfo());
        }
        if (teiaUser.getCalInfo() != null) {
            calInfoRepository.save(teiaUser.getCalInfo());
        }

        return userRepository.save(teiaUser);
    }

    public TeiaUser updateUser(Long id, TeiaUser teiaUserDetails) {
        Optional<TeiaUser> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            TeiaUser teiaUser = userOptional.get();

            // Update fields only if they are provided in the payload
            if (teiaUserDetails.getName() != null) {
                teiaUser.setName(teiaUserDetails.getName());
            }
            if (teiaUserDetails.getEmail() != null) {
                teiaUser.setEmail(teiaUserDetails.getEmail());
            }
            if (teiaUserDetails.getPhoneNumber() != null) {
                teiaUser.setPhoneNumber(teiaUserDetails.getPhoneNumber());
            }
            if (teiaUserDetails.getElevenLabsInfo() != null) {
                teiaUser.setElevenLabsInfo(teiaUserDetails.getElevenLabsInfo());
            }
            if (teiaUserDetails.getTwilioInfo() != null) {
                teiaUser.setTwilioInfo(teiaUserDetails.getTwilioInfo());
            }
            if (teiaUserDetails.getOpenAIInfo() != null) {
                teiaUser.setOpenAIInfo(teiaUserDetails.getOpenAIInfo());
            }
            if (teiaUserDetails.getCalInfo() != null) {
                teiaUser.setCalInfo(teiaUserDetails.getCalInfo());
            }
            if (teiaUserDetails.getContacts() != null) {
                teiaUser.getContacts().clear();
                for (Contact contact : teiaUserDetails.getContacts()) {
                    contact.setTeiaUser(teiaUser);
                    teiaUser.getContacts().add(contact);
                }
            }

            teiaUser.setUpdateDate(LocalDateTime.now());

            return userRepository.save(teiaUser);
        } else {
            throw new RuntimeException("User not found with id " + id);
        }
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public TeiaUser retrieveUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with id " + id));
    }

    public List<TeiaUser> retrieveAllUsers() {
        return userRepository.findAll();
    }

    public List<Contact> retrieveUserContacts(Long userId) {
        TeiaUser teiaUser = retrieveUser(userId);
        return teiaUser.getContacts();
    }

    public TeiaUser addContactToUser(Long userId, Contact contact) {
        TeiaUser teiaUser = retrieveUser(userId);
        contact.setTeiaUser(teiaUser);
        teiaUser.getContacts().add(contact);
        return userRepository.save(teiaUser);
    }

    public TeiaUser deleteContactFromUser(Long userId, Long contactId) {
        TeiaUser teiaUser = retrieveUser(userId);
        teiaUser.getContacts().removeIf(contact -> contact.getId().equals(contactId));
        return userRepository.save(teiaUser);
    }
}