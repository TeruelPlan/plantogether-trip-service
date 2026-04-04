package com.plantogether.trip.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.MemberRole;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserProfileService userProfileService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public TripService(TripRepository tripRepository,
                       TripMemberRepository tripMemberRepository,
                       UserProfileService userProfileService,
                       ApplicationEventPublisher applicationEventPublisher) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.userProfileService = userProfileService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public Trip createTrip(UUID deviceId, String title, String description, String currency) {
        UserProfile profile = userProfileService.getOrCreateProfile(deviceId, null, null);

        Trip trip = Trip.builder()
            .title(title)
            .description(description)
            .status(TripStatus.PLANNING)
            .createdBy(deviceId)
            .referenceCurrency(currency != null && !currency.isBlank() ? currency : "EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        trip = tripRepository.save(trip);

        TripMember member = TripMember.builder()
            .trip(trip)
            .deviceId(deviceId)
            .displayName(profile.getDisplayName())
            .role(MemberRole.ORGANIZER)
            .joinedAt(Instant.now())
            .build();
        tripMemberRepository.save(member);

        applicationEventPublisher.publishEvent(trip);
        return trip;
    }

    @Transactional(readOnly = true)
    public Trip getTrip(UUID tripId, UUID deviceId) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
            .orElseThrow(() -> new AccessDeniedException("Not a member of this trip"));
        return trip;
    }

    @Transactional(readOnly = true)
    public List<Trip> listTripsForDevice(UUID deviceId) {
        return tripRepository.findAllByMemberDeviceId(deviceId);
    }
}
