package com.plantogether.trip.controller;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.BadRequestException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.dto.TripInvitationResponse;
import com.plantogether.trip.dto.TripPreviewResponse;
import com.plantogether.trip.service.InvitationService;
import com.plantogether.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripController.class)
@Import(SecurityAutoConfiguration.class)
class TripControllerInvitationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TripService tripService;

    @MockBean
    private InvitationService invitationService;

    @Test
    void getInvitation_organizer_returns200() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(invitationService.getOrCreateInvitation(eq(tripId), any()))
            .thenReturn(TripInvitationResponse.builder()
                .inviteUrl("http://localhost/trips/" + tripId + "/join?token=" + token)
                .token(token.toString())
                .build());

        mockMvc.perform(get("/api/v1/trips/{id}/invitation", tripId)
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(token.toString()))
            .andExpect(jsonPath("$.inviteUrl").isNotEmpty());
    }

    @Test
    void getInvitation_nonOrganizer_returns403() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        when(invitationService.getOrCreateInvitation(eq(tripId), any()))
            .thenThrow(new AccessDeniedException("Only the organizer can generate invitations"));

        mockMvc.perform(get("/api/v1/trips/{id}/invitation", tripId)
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void getTripPreview_validToken_returns200() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(tripService.getTripPreview(eq(tripId), eq(token), any()))
            .thenReturn(TripPreviewResponse.builder()
                .id(tripId).title("Beach Trip").memberCount(3).isMember(false)
                .build());

        mockMvc.perform(get("/api/v1/trips/{id}/preview", tripId)
                .header("X-Device-Id", deviceId.toString())
                .param("token", token.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Beach Trip"))
            .andExpect(jsonPath("$.memberCount").value(3))
            .andExpect(jsonPath("$.isMember").value(false));
    }

    @Test
    void getTripPreview_invalidToken_returns404() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(tripService.getTripPreview(eq(tripId), eq(token), any()))
            .thenThrow(new ResourceNotFoundException("Invitation not found"));

        mockMvc.perform(get("/api/v1/trips/{id}/preview", tripId)
                .header("X-Device-Id", deviceId.toString())
                .param("token", token.toString()))
            .andExpect(status().isNotFound());
    }

    @Test
    void joinTrip_validToken_returns200() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        Trip trip = Trip.builder()
            .id(tripId).title("Beach Trip").status(TripStatus.PLANNING)
            .createdBy(UUID.randomUUID()).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        when(tripService.joinTrip(eq(tripId), eq(token), any())).thenReturn(trip);

        mockMvc.perform(post("/api/v1/trips/{id}/join", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"token\": \"" + token + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Beach Trip"));
    }

    @Test
    void joinTrip_invalidToken_returns404() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(tripService.joinTrip(eq(tripId), eq(token), any()))
            .thenThrow(new ResourceNotFoundException("Invitation not found"));

        mockMvc.perform(post("/api/v1/trips/{id}/join", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"token\": \"" + token + "\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void joinTrip_wrongTripToken_returns400() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(tripService.joinTrip(eq(tripId), eq(token), any()))
            .thenThrow(new BadRequestException("Token does not match this trip"));

        mockMvc.perform(post("/api/v1/trips/{id}/join", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"token\": \"" + token + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void joinTrip_noDisplayName_returns400() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(tripService.joinTrip(eq(tripId), eq(token), any()))
            .thenThrow(new BadRequestException("Display name required"));

        mockMvc.perform(post("/api/v1/trips/{id}/join", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("{\"token\": \"" + token + "\"}"))
            .andExpect(status().isBadRequest());
    }
}
