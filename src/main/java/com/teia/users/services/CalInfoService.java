package com.teia.users.services;

import com.teia.users.models.CalInfo;
import com.teia.users.repositories.CalInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CalInfoService {

    @Autowired
    private CalInfoRepository calInfoRepository;

    public CalInfo create(CalInfo calInfo) {
        calInfo.setCreateDate(LocalDateTime.now());
        calInfo.setUpdateDate(LocalDateTime.now());
        return calInfoRepository.save(calInfo);
    }

    public CalInfo update(Long id, CalInfo calInfoDetails) {
        Optional<CalInfo> calInfoOptional = calInfoRepository.findById(id);
        if (calInfoOptional.isPresent()) {
            CalInfo calInfo = calInfoOptional.get();

            if (calInfoDetails.getApiKey() != null) {
                calInfo.setApiKey(calInfoDetails.getApiKey());
            }
            if (calInfoDetails.getUserId() != null) {
                calInfo.setUserId(calInfoDetails.getUserId());
            }
            if (calInfoDetails.getEventId() != null) {
                calInfo.setEventId(calInfoDetails.getEventId());
            }

            calInfo.setUpdateDate(LocalDateTime.now());
            return calInfoRepository.save(calInfo);
        } else {
            throw new RuntimeException("CalInfo not found with id " + id);
        }
    }

    public void delete(Long id) {
        calInfoRepository.deleteById(id);
    }

    public CalInfo retrieve(Long id) {
        return calInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("CalInfo not found with id " + id));
    }

    public List<CalInfo> retrieveAll() {
        return calInfoRepository.findAll();
    }
}
