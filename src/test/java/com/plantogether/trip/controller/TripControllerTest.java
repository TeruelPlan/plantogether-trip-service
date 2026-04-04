package com.plantogether.trip.controller;

import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.trip.dto.TripMemberResponse;
import com.plantogether.trip.dto.TripResponse;
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

    private final UUID deviceId = UUID.randomUUID();

    private TripResponse buildTripResponse(UUID tripId) {
        return TripResponse.builder()
            .id(tripId)
            .title("Beach Trip")
            .description("Fun at the beach")
            .status("PLANNING")
            .createdBy(deviceId)
            .referenceCurrency("EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .memberCount(1)
            .members(List.of(TripMemberResponse.builder()
                .deviceId(deviceId)
                .displayName("Alice")
                .role("ORGANIZER")
                .joinedAt(Instant.now())
                .build()))
            .build();
    }

    @Test
    void postTrip_validBody_returns201() throws Exception {
        TripResponse response = buildTripResponse(UUID.randomUUID());

        when(tripService.createTripWithResponse(any(), eq("Beach Trip"), eq("Fun at the beach"), eq("EUR")))
            .thenReturn(response);

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
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.members[0].displayName").value("Alice"))
            .andExpect(jsonPath("$.members[0].role").value("ORGANIZER"));
    }

    @Test
    void postTrip_emptyTitle_returns400() throws Exception {
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
        TripResponse response = TripResponse.builder()
            .id(UUID.randomUUID())
            .title("Beach Trip")
            .status("PLANNING")
            .referenceCurrency("EUR")
            .createdBy(deviceId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .memberCount(2)
            .build();

        when(tripService.listTripResponsesForDevice(any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/trips")
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Beach Trip"))
            .andExpect(jsonPath("$[0].memberCount").value(2));
    }

    @Test
    void getTrip_returnsMembersArray() throws Exception {
        UUID tripId = UUID.randomUUID();
        TripResponse response = buildTripResponse(tripId);

        when(tripService.getTripResponse(eq(tripId), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/trips/" + tripId)
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Beach Trip"))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.members").isArray())
            .andExpect(jsonPath("$.members[0].deviceId").exists())
            .andExpect(jsonPath("$.members[0].displayName").value("Alice"))
            .andExpect(jsonPath("$.members[0].role").value("ORGANIZER"));
    }

    @Test
    void getTrip_noDeviceId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/trips"))
            .andExpect(status().isUnauthorized());
    }
}
