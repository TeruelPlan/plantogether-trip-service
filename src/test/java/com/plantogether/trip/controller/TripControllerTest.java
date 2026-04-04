package com.plantogether.trip.controller;

import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.service.InvitationService;
import com.plantogether.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
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
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TripService tripService;

    @MockBean
    private InvitationService invitationService;

    private Trip buildTrip(UUID deviceId) {
        return Trip.builder()
            .id(UUID.randomUUID())
            .title("Beach Trip")
            .description("Fun at the beach")
            .status(TripStatus.PLANNING)
            .createdBy(deviceId)
            .referenceCurrency("EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    void postTrip_validBody_returns201() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Trip trip = buildTrip(deviceId);

        when(tripService.createTrip(any(), eq("Beach Trip"), eq("Fun at the beach"), eq("EUR")))
            .thenReturn(trip);

        mockMvc.perform(post("/api/v1/trips")
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("""
                    {"title": "Beach Trip", "description": "Fun at the beach", "currency": "EUR"}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Beach Trip"))
            .andExpect(jsonPath("$.status").value("PLANNING"))
            .andExpect(jsonPath("$.referenceCurrency").value("EUR"))
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void postTrip_emptyTitle_returns400() throws Exception {
        UUID deviceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/trips")
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("""
                    {"title": "", "description": "No title"}
                """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postTrip_missingTitle_returns400() throws Exception {
        UUID deviceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/trips")
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content("""
                    {"description": "No title field"}
                """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getTrips_returnsList() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Trip trip = buildTrip(deviceId);

        when(tripService.listTripsForDevice(any())).thenReturn(List.of(trip));

        mockMvc.perform(get("/api/v1/trips")
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Beach Trip"));
    }

    @Test
    void getTrip_noDeviceId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/trips"))
            .andExpect(status().isUnauthorized());
    }
}
