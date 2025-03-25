package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "teia_user")
@Data
public class TeiaUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "phone_number", length = 255, nullable = false)
    private String phoneNumber;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "eleven_labs_info_id", nullable = false)
    private ElevenLabsInfo elevenLabsInfo;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "twilio_info_id", nullable = false)
    private TwilioInfo twilioInfo;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "open_ai_info_id", nullable = false)
    private OpenAIInfo openAIInfo;

    @OneToMany(mappedBy = "teiaUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Contact> contacts;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    public TeiaUser() {
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }

    public TeiaUser(String name, String email, String phoneNumber, ElevenLabsInfo elevenLabsInfo, TwilioInfo twilioInfo, OpenAIInfo openAIInfo, List<Contact> contacts) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.elevenLabsInfo = elevenLabsInfo;
        this.twilioInfo = twilioInfo;
        this.openAIInfo = openAIInfo;
        this.contacts = contacts;
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }
}