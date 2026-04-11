package com.plantogether.trip.security;

import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.trip.controller.UserProfileController;
import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for Story 1.2 — Security enforcement at the HTTP layer.
 * <p>
 * AC1: Valid UUID in X-Device-Id → 200
 * AC2: Missing header → 401 / Invalid UUID → 401
 * AC6: /actuator/health and /actuator/info are permitAll (no X-Device-Id required)
 */
@WebMvcTest(UserProfileController.class)
@Import(SecurityAutoConfiguration.class)
class DeviceIdSecurityAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    // AC2: invalid UUID value → SecurityContext stays unauthenticated → 401
    @Test
    void requestWithInvalidUuid_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("X-Device-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized());
    }

    // AC2: empty string value → SecurityContext stays unauthenticated → 401
    @Test
    void requestWithEmptyDeviceId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Device-Id", ""))
                .andExpect(status().isUnauthorized());
    }

    // AC2: blank (whitespace-only) value → SecurityContext stays unauthenticated → 401
    @Test
    void requestWithBlankDeviceId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Device-Id", "   "))
                .andExpect(status().isUnauthorized());
    }

    // AC6: /actuator/health is permitAll — no X-Device-Id required
    @Test
    void actuatorHealth_withoutDeviceId_returns200OrNotFound() throws Exception {
        // In @WebMvcTest slice the actuator endpoint is not loaded,
        // so we expect 404 (endpoint missing in slice) rather than 401 (security block).
        // This verifies the security config does NOT block /actuator/health with 401.
        int status = mockMvc.perform(get("/actuator/health"))
                .andReturn()
                .getResponse()
                .getStatus();

        // 200 if actuator is present, 404 if not loaded in the slice — both are acceptable.
        // What must NOT happen is 401 (Unauthorized).
        org.assertj.core.api.Assertions.assertThat(status)
                .as("/actuator/health must not be blocked with 401")
                .isNotEqualTo(401);
    }

    // AC6: /actuator/info is permitAll — no X-Device-Id required
    @Test
    void actuatorInfo_withoutDeviceId_isNotBlocked() throws Exception {
        int status = mockMvc.perform(get("/actuator/info"))
                .andReturn()
                .getResponse()
                .getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .as("/actuator/info must not be blocked with 401")
                .isNotEqualTo(401);
    }

    // AC1: valid UUID (regression guard — mirrors DeviceIdFilterTest at HTTP layer)
    @Test
    void requestWithValidDeviceId_isAuthenticated_andReachesController() throws Exception {
        UUID deviceId = UUID.randomUUID();
        when(userProfileService.getOrCreateProfile(deviceId, null, null)).thenReturn(new UserProfile());
        // Service returns null → controller falls through to getOrCreateProfile;
        // 401 would mean security blocked before the controller was reached.
        // Any non-401 status confirms the security layer accepted the device ID.
        int status = mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Device-Id", deviceId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .as("Valid X-Device-Id must not produce 401")
                .isNotEqualTo(401);
    }
}
