package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "open_ai_info")
@Data
public class OpenAIInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "open_ai_api_key", length = 255, nullable = false)
    private String openAIApiKey;

    @Column(name = "open_ai_assistant_key", length = 255, nullable = false)
    private String openAIAssistantKey;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @OneToOne(mappedBy = "openAIInfo")
    @JsonBackReference
    private TeiaUser teiaUser;

    public OpenAIInfo() {
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }

    public OpenAIInfo(String openAIApiKey, String openAIAssistantKey) {
        this.openAIApiKey = openAIApiKey;
        this.openAIAssistantKey = openAIAssistantKey;
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }
}