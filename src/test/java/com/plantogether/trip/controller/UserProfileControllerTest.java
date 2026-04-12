package com.plantogether.trip.controller;

import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import(SecurityAutoConfiguration.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    @AfterEach
    void tearDown() {
        Mockito.reset(userProfileService);
    }

    @Test
    void getMyProfile_withDeviceId_returns200() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder()
                .deviceId(deviceId)
                .displayName("Guest abc1")
                .avatarUrl(null)
                .updatedAt(Instant.now())
                .build();

        when(userProfileService.getOrCreateProfile(any(), any(), any())).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Guest abc1"))
                .andExpect(jsonPath("$.deviceId").exists());
    }

    @Test
    void putMyProfile_validBody_returns200() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder()
                .deviceId(deviceId)
                .displayName("Alice Updated")
                .updatedAt(Instant.now())
                .build();

        when(userProfileService.updateDisplayName(any(), any())).thenReturn(profile);

        mockMvc.perform(put("/api/v1/users/me")
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content("{\"displayName\": \"Alice Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Updated"));
    }

    @Test
    void putMyProfile_blankDisplayName_returns400() throws Exception {
        UUID deviceId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content("{\"displayName\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyProfile_noDeviceId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
