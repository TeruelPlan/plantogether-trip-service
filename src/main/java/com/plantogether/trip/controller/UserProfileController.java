package com.plantogether.trip.controller;

import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.dto.UpdateProfileRequest;
import com.plantogether.trip.dto.UserProfileResponse;
import com.plantogether.trip.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Authentication authentication) {
        UUID deviceId = UUID.fromString(authentication.getName());
        UserProfile profile = userProfileService.getOrCreateProfile(deviceId, null, null);
        return ResponseEntity.ok(toResponse(profile));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID deviceId = UUID.fromString(authentication.getName());
        UserProfile updated = userProfileService.updateDisplayName(deviceId, request.getDisplayName());
        return ResponseEntity.ok(toResponse(updated));
    }

    private UserProfileResponse toResponse(UserProfile p) {
        return UserProfileResponse.builder()
                .deviceId(p.getDeviceId())
                .displayName(p.getDisplayName())
                .avatarUrl(p.getAvatarUrl())
                .build();
    }
}
