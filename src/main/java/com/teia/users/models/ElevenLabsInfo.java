package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eleven_labs_info")
@Data
public class ElevenLabsInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "eleven_labs_agent_id", length = 255, nullable = false)
    private String elevenLabsAgentId;

    @Column(name = "eleven_labs_api_key", length = 255, nullable = false)
    private String elevenLabsApiKey;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @OneToOne(mappedBy = "elevenLabsInfo")
    @JsonBackReference
    private TeiaUser teiaUser;

    public ElevenLabsInfo() {
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }

    public ElevenLabsInfo(String elevenLabsAgentId, String elevenLabsApiKey) {
        this.elevenLabsAgentId = elevenLabsAgentId;
        this.elevenLabsApiKey = elevenLabsApiKey;
        this.createDate = LocalDateTime.now();
        this.updateDate = LocalDateTime.now();
    }
}
