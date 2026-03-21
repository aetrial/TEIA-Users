package com.teia.users.services;

import com.teia.users.models.ElevenLabsInfo;
import com.teia.users.repositories.ElevenLabsInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ElevenLabsInfoService {

    @Autowired
    private ElevenLabsInfoRepository elevenLabsInfoRepository;

    public ElevenLabsInfo create(ElevenLabsInfo elevenLabsInfo) {
        elevenLabsInfo.setCreateDate(LocalDateTime.now());
        elevenLabsInfo.setUpdateDate(LocalDateTime.now());
        return elevenLabsInfoRepository.save(elevenLabsInfo);
    }

    public ElevenLabsInfo update(Long id, ElevenLabsInfo elevenLabsInfoDetails) {
        Optional<ElevenLabsInfo> optional = elevenLabsInfoRepository.findById(id);
        if (optional.isPresent()) {
            ElevenLabsInfo elevenLabsInfo = optional.get();

            if (elevenLabsInfoDetails.getElevenLabsAgentId() != null) {
                elevenLabsInfo.setElevenLabsAgentId(elevenLabsInfoDetails.getElevenLabsAgentId());
            }
            if (elevenLabsInfoDetails.getElevenLabsApiKey() != null) {
                elevenLabsInfo.setElevenLabsApiKey(elevenLabsInfoDetails.getElevenLabsApiKey());
            }

            elevenLabsInfo.setUpdateDate(LocalDateTime.now());
            return elevenLabsInfoRepository.save(elevenLabsInfo);
        } else {
            throw new RuntimeException("ElevenLabsInfo not found with id " + id);
        }
    }

    public void delete(Long id) {
        elevenLabsInfoRepository.deleteById(id);
    }

    public ElevenLabsInfo retrieve(Long id) {
        return elevenLabsInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ElevenLabsInfo not found with id " + id));
    }

    public List<ElevenLabsInfo> retrieveAll() {
        return elevenLabsInfoRepository.findAll();
    }
}
