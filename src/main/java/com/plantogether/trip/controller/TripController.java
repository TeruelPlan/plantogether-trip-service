package com.plantogether.trip.controller;

import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.dto.CreateTripRequest;
import com.plantogether.trip.dto.TripResponse;
import com.plantogether.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            Authentication authentication,
            @Valid @RequestBody CreateTripRequest request) {
        UUID deviceId = UUID.fromString(authentication.getName());
        Trip trip = tripService.createTrip(
            deviceId,
            request.getTitle(),
            request.getDescription(),
            request.getCurrency()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TripResponse.from(trip));
    }

    @GetMapping
    public ResponseEntity<List<TripResponse>> listMyTrips(Authentication authentication) {
        UUID deviceId = UUID.fromString(authentication.getName());
        List<TripResponse> trips = tripService.listTripsForDevice(deviceId).stream()
            .map(TripResponse::from)
            .toList();
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTrip(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID deviceId = UUID.fromString(authentication.getName());
        Trip trip = tripService.getTrip(id, deviceId);
        return ResponseEntity.ok(TripResponse.from(trip));
    }
}
