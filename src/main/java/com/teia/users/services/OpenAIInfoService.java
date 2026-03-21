package com.teia.users.services;

import com.teia.users.models.OpenAIInfo;
import com.teia.users.repositories.OpenAIInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OpenAIInfoService {

    @Autowired
    private OpenAIInfoRepository openAIInfoRepository;

    public OpenAIInfo create(OpenAIInfo openAIInfo) {
        openAIInfo.setCreateDate(LocalDateTime.now());
        openAIInfo.setUpdateDate(LocalDateTime.now());
        return openAIInfoRepository.save(openAIInfo);
    }

    public OpenAIInfo update(Long id, OpenAIInfo openAIInfoDetails) {
        Optional<OpenAIInfo> optional = openAIInfoRepository.findById(id);
        if (optional.isPresent()) {
            OpenAIInfo openAIInfo = optional.get();

            if (openAIInfoDetails.getOpenAIApiKey() != null) {
                openAIInfo.setOpenAIApiKey(openAIInfoDetails.getOpenAIApiKey());
            }
            if (openAIInfoDetails.getOpenAIAssistantKey() != null) {
                openAIInfo.setOpenAIAssistantKey(openAIInfoDetails.getOpenAIAssistantKey());
            }

            openAIInfo.setUpdateDate(LocalDateTime.now());
            return openAIInfoRepository.save(openAIInfo);
        } else {
            throw new RuntimeException("OpenAIInfo not found with id " + id);
        }
    }

    public void delete(Long id) {
        openAIInfoRepository.deleteById(id);
    }

    public OpenAIInfo retrieve(Long id) {
        return openAIInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OpenAIInfo not found with id " + id));
    }

    public List<OpenAIInfo> retrieveAll() {
        return openAIInfoRepository.findAll();
    }
}
