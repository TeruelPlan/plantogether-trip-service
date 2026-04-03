package com.plantogether.trip.service;

import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.repository.UserProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public UserProfile getOrCreateProfile(UUID deviceId, String displayName, String avatarUrl) {
        return userProfileRepository.findById(deviceId)
            .orElseGet(() -> {
                try {
                    return userProfileRepository.save(
                        UserProfile.builder()
                            .deviceId(deviceId)
                            .displayName(displayName != null ? displayName : "Guest " + deviceId.toString().substring(0, 4))
                            .avatarUrl(avatarUrl)
                            .updatedAt(Instant.now())
                            .build()
                    );
                } catch (DataIntegrityViolationException e) {
                    // Concurrent insert — row was just created by another request; return it
                    return userProfileRepository.findById(deviceId)
                        .orElseThrow(() -> e);
                }
            });
    }

    @Transactional
    public UserProfile updateDisplayName(UUID deviceId, String newDisplayName) {
        UserProfile profile = userProfileRepository.findById(deviceId)
            .orElseGet(() -> UserProfile.builder()
                .deviceId(deviceId)
                .avatarUrl(null)
                .updatedAt(Instant.now())
                .build());
        profile.setDisplayName(newDisplayName);
        profile.setUpdatedAt(Instant.now());
        return userProfileRepository.save(profile);
    }
}
