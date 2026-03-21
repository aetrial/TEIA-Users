package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.teia.users.security.EncryptedStringConverter;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cal_info")
@Data
public class CalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key", length = 500, nullable = false)
    @Convert(converter = EncryptedStringConverter.class)
    private String apiKey;

    @Column(name = "user_id", length = 255, nullable = false)
    private String userId;

    @Column(name = "event_id", length = 255, nullable = false)
    private String eventId;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @OneToOne(mappedBy = "calInfo")
    @JsonBackReference
    private TeiaUser teiaUser;

    public CalInfo() {
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }

    public CalInfo(String apiKey, String userId, String eventId) {
        this.apiKey = apiKey;
        this.userId = userId;
        this.eventId = eventId;
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }
}
