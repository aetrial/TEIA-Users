package com.teia.users.services;

import com.teia.users.models.TwilioInfo;
import com.teia.users.repositories.TwilioInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TwilioInfoService {

    @Autowired
    private TwilioInfoRepository twilioInfoRepository;

    public TwilioInfo create(TwilioInfo twilioInfo) {
        twilioInfo.setCreateDate(LocalDateTime.now());
        twilioInfo.setUpdateDate(LocalDateTime.now());
        return twilioInfoRepository.save(twilioInfo);
    }

    public TwilioInfo update(Long id, TwilioInfo twilioInfoDetails) {
        Optional<TwilioInfo> optional = twilioInfoRepository.findById(id);
        if (optional.isPresent()) {
            TwilioInfo twilioInfo = optional.get();

            if (twilioInfoDetails.getTwilioAccSID() != null) {
                twilioInfo.setTwilioAccSID(twilioInfoDetails.getTwilioAccSID());
            }
            if (twilioInfoDetails.getTwilioAuthToken() != null) {
                twilioInfo.setTwilioAuthToken(twilioInfoDetails.getTwilioAuthToken());
            }
            if (twilioInfoDetails.getTwilioPhoneNumberCalls() != null) {
                twilioInfo.setTwilioPhoneNumberCalls(twilioInfoDetails.getTwilioPhoneNumberCalls());
            }
            if (twilioInfoDetails.getTwilioPhoneNumberWhatsapp() != null) {
                twilioInfo.setTwilioPhoneNumberWhatsapp(twilioInfoDetails.getTwilioPhoneNumberWhatsapp());
            }

            twilioInfo.setUpdateDate(LocalDateTime.now());
            return twilioInfoRepository.save(twilioInfo);
        } else {
            throw new RuntimeException("TwilioInfo not found with id " + id);
        }
    }

    public void delete(Long id) {
        twilioInfoRepository.deleteById(id);
    }

    public TwilioInfo retrieve(Long id) {
        return twilioInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TwilioInfo not found with id " + id));
    }

    public List<TwilioInfo> retrieveAll() {
        return twilioInfoRepository.findAll();
    }
}
