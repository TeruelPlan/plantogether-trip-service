package com.plantogether.trip.controller;

import com.plantogether.trip.dto.*;
import com.plantogether.trip.service.InvitationService;
import com.plantogether.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;
    private final InvitationService invitationService;

    public TripController(TripService tripService, InvitationService invitationService) {
        this.tripService = tripService;
        this.invitationService = invitationService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            Authentication authentication,
            @Valid @RequestBody CreateTripRequest request) {
        UUID deviceId = UUID.fromString(authentication.getName());
        TripResponse response = tripService.createTripWithResponse(
                deviceId,
                request.getTitle(),
                request.getDescription(),
                request.getCurrency()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TripResponse>> listMyTrips(Authentication authentication) {
        UUID deviceId = UUID.fromString(authentication.getName());
        List<TripResponse> trips = tripService.listTripResponsesForDevice(deviceId);
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTrip(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID deviceId = UUID.fromString(authentication.getName());
        TripResponse response = tripService.getTripResponse(id, deviceId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TripResponse> updateTrip(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTripRequest request) {
        UUID deviceId = UUID.fromString(authentication.getName());
        TripResponse response = tripService.updateTrip(id, deviceId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<TripResponse> archiveTrip(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID deviceId = UUID.fromString(authentication.getName());
        TripResponse response = tripService.archiveTrip(id, deviceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/invitation")
    public ResponseEntity<TripInvitationResponse> getInvitation(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID deviceId = UUID.fromString(authentication.getName());
        TripInvitationResponse response = invitationService.getOrCreateInvitation(id, deviceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<TripPreviewResponse> getTripPreview(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam UUID token) {
        UUID deviceId = UUID.fromString(authentication.getName());
        TripPreviewResponse response = tripService.getTripPreview(id, token, deviceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<TripMemberResponse>> getMembers(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID deviceId = UUID.fromString(authentication.getName());
        List<TripMemberResponse> members = tripService.getTripMembers(id, deviceId);
        return ResponseEntity.ok(members);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID memberId) {
        UUID callerDeviceId = UUID.fromString(authentication.getName());
        tripService.removeMember(id, callerDeviceId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<TripResponse> joinTrip(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody JoinTripRequest request) {
        UUID deviceId = UUID.fromString(authentication.getName());
        TripResponse trip = tripService.joinTrip(id, request.getToken(), deviceId);
        return ResponseEntity.ok(trip);
    }
}
