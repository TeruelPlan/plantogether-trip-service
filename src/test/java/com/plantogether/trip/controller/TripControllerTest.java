package com.plantogether.trip.controller;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.BadRequestException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.trip.dto.TripMemberResponse;
import com.plantogether.trip.dto.TripResponse;
import com.plantogether.trip.dto.UpdateTripRequest;
import com.plantogether.trip.exception.TripStateException;
import com.plantogether.trip.service.InvitationService;
import com.plantogether.trip.service.TripService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripController.class)
@Import(SecurityAutoConfiguration.class)
class TripControllerTest {

    private final UUID deviceId = UUID.randomUUID();
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private TripService tripService;
    @MockBean
    private InvitationService invitationService;

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

    @AfterEach
    void tearDown() {
        Mockito.reset(tripService, invitationService);
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

    // --- PUT /api/v1/trips/{id} ---

    @Test
    void putTrip_validBody_returns200() throws Exception {
        UUID tripId = UUID.randomUUID();
        TripResponse response = TripResponse.builder()
                .id(tripId).title("Updated Title").description("New desc")
                .status("PLANNING").referenceCurrency("USD")
                .createdBy(deviceId).createdAt(Instant.now()).updatedAt(Instant.now())
                .memberCount(1).members(List.of()).build();

        when(tripService.updateTrip(eq(tripId), any(), any(UpdateTripRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/trips/" + tripId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content("""
                                    {"title": "Updated Title", "description": "New desc", "referenceCurrency": "USD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.referenceCurrency").value("USD"));
    }

    @Test
    void putTrip_emptyTitle_returns400() throws Exception {
        UUID tripId = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/trips/" + tripId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content("""
                                    {"title": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putTrip_nonOrganizer_returns403() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(tripService.updateTrip(eq(tripId), any(), any(UpdateTripRequest.class)))
                .thenThrow(new AccessDeniedException("Only the organizer can perform this action"));

        mockMvc.perform(put("/api/v1/trips/" + tripId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content("""
                                    {"title": "Hacked"}
                                """))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /api/v1/trips/{id}/archive ---

    @Test
    void patchArchive_returns200() throws Exception {
        UUID tripId = UUID.randomUUID();
        TripResponse response = TripResponse.builder()
                .id(tripId).title("Trip").status("ARCHIVED")
                .referenceCurrency("EUR").createdBy(deviceId)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .memberCount(1).members(List.of()).build();

        when(tripService.archiveTrip(eq(tripId), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/trips/" + tripId + "/archive")
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void patchArchive_alreadyArchived_returns409() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(tripService.archiveTrip(eq(tripId), any()))
                .thenThrow(new TripStateException("Trip is already archived"));

        mockMvc.perform(patch("/api/v1/trips/" + tripId + "/archive")
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void patchArchive_nonOrganizer_returns403() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(tripService.archiveTrip(eq(tripId), any()))
                .thenThrow(new AccessDeniedException("Only the organizer can perform this action"));

        mockMvc.perform(patch("/api/v1/trips/" + tripId + "/archive")
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/v1/trips/{id}/members ---

    @Test
    void getMembers_memberCaller_returns200WithList() throws Exception {
        UUID tripId = UUID.randomUUID();
        List<TripMemberResponse> members = List.of(
                TripMemberResponse.builder()
                        .deviceId(deviceId).displayName("Alice").role("ORGANIZER").joinedAt(Instant.now()).build()
        );

        when(tripService.getTripMembers(eq(tripId), any())).thenReturn(members);

        mockMvc.perform(get("/api/v1/trips/" + tripId + "/members")
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Alice"))
                .andExpect(jsonPath("$[0].role").value("ORGANIZER"));
    }

    // --- DELETE /api/v1/trips/{id}/members/{deviceId} ---

    @Test
    void deleteMember_organizerRemovesParticipant_returns204() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        doNothing().when(tripService).removeMember(eq(tripId), any(), eq(targetId));

        mockMvc.perform(delete("/api/v1/trips/" + tripId + "/members/" + targetId)
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMember_participant_returns403() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        doThrow(new AccessDeniedException("Only the organizer can perform this action"))
                .when(tripService).removeMember(eq(tripId), any(), eq(targetId));

        mockMvc.perform(delete("/api/v1/trips/" + tripId + "/members/" + targetId)
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteMember_selfRemoval_returns400() throws Exception {
        UUID tripId = UUID.randomUUID();

        doThrow(new BadRequestException("Organizer cannot remove themselves"))
                .when(tripService).removeMember(eq(tripId), any(), any());

        mockMvc.perform(delete("/api/v1/trips/" + tripId + "/members/" + deviceId)
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isBadRequest());
    }
}
