package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.teia.users.models.enums.Role;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "teia_user", indexes = {
    @Index(name = "idx_teia_user_email", columnList = "email", unique = true)
})
@Data
public class TeiaUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password", length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private Role role = Role.CLIENT;

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

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cal_info_id", nullable = false)
    private CalInfo calInfo;

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

    public TeiaUser(String name, String email, String phoneNumber, ElevenLabsInfo elevenLabsInfo, TwilioInfo twilioInfo, OpenAIInfo openAIInfo, CalInfo calInfo, List<Contact> contacts) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.elevenLabsInfo = elevenLabsInfo;
        this.twilioInfo = twilioInfo;
        this.openAIInfo = openAIInfo;
        this.calInfo = calInfo;
        this.contacts = contacts;
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }
}
