package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.teia.users.security.EncryptedStringConverter;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "twilio_info")
@Data
public class TwilioInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "twilio_acc_sid", length = 255, nullable = false)
    private String twilioAccSID;

    @Column(name = "twilio_auth_token", length = 500, nullable = false)
    @Convert(converter = EncryptedStringConverter.class)
    private String twilioAuthToken;

    @Column(name = "twilio_phone_number_calls", length = 255, nullable = false)
    private String twilioPhoneNumberCalls;

    @Column(name = "twilio_phone_number_whatsapp", length = 255, nullable = false)
    private String twilioPhoneNumberWhatsapp;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @OneToOne(mappedBy = "twilioInfo")
    @JsonBackReference
    private TeiaUser teiaUser;

    public TwilioInfo() {
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }

    public TwilioInfo(String twilioAccSID, String twilioAuthToken, String twilioPhoneNumberCalls, String twilioPhoneNumberWhatsapp) {
        this.twilioAccSID = twilioAccSID;
        this.twilioAuthToken = twilioAuthToken;
        this.twilioPhoneNumberCalls = twilioPhoneNumberCalls;
        this.twilioPhoneNumberWhatsapp = twilioPhoneNumberWhatsapp;
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }
}
