package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.teia.users.models.enums.ChannelType;
import com.teia.users.models.enums.ContactStates;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact")
@Data
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", length = 255, nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContactStates status;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private TeiaUser teiaUser;

    public Contact() {
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }

    public Contact(String phoneNumber, ChannelType channel, ContactStates status, String name, TeiaUser teiaUser) {
        this.phoneNumber = phoneNumber;
        this.channel = channel;
        this.status = status;
        this.name = name;
        this.teiaUser = teiaUser;
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }
}
