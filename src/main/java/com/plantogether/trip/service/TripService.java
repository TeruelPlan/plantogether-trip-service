package com.plantogether.trip.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.BadRequestException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.MemberRole;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripInvitation;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.dto.TripPreviewResponse;
import com.plantogether.trip.repository.TripInvitationRepository;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import com.plantogether.trip.repository.UserProfileRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripInvitationRepository tripInvitationRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileService userProfileService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public TripService(TripRepository tripRepository,
                       TripMemberRepository tripMemberRepository,
                       TripInvitationRepository tripInvitationRepository,
                       UserProfileRepository userProfileRepository,
                       UserProfileService userProfileService,
                       ApplicationEventPublisher applicationEventPublisher) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.tripInvitationRepository = tripInvitationRepository;
        this.userProfileRepository = userProfileRepository;
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

    @Transactional
    public Trip joinTrip(UUID tripId, UUID token, UUID deviceId) {
        TripInvitation invitation = tripInvitationRepository.findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (!invitation.getTrip().getId().equals(tripId)) {
            throw new BadRequestException("Token does not match this trip");
        }

        Trip trip = invitation.getTrip();
        if (trip.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Trip not found");
        }

        // Idempotent: if already a member, return the trip
        Optional<TripMember> existing = tripMemberRepository
            .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId);
        if (existing.isPresent()) {
            return trip;
        }

        // Resolve display name from user profile
        UserProfile profile = userProfileRepository.findById(deviceId)
            .orElseThrow(() -> new BadRequestException("Display name required"));
        if (profile.getDisplayName() == null) {
            throw new BadRequestException("Display name required");
        }

        Instant joinedAt = Instant.now();
        TripMember member = TripMember.builder()
            .trip(trip)
            .deviceId(deviceId)
            .displayName(profile.getDisplayName())
            .role(MemberRole.PARTICIPANT)
            .joinedAt(joinedAt)
            .build();
        tripMemberRepository.save(member);

        // Publish MemberJoinedEvent via application event (handled by TripEventPublisher)
        applicationEventPublisher.publishEvent(new MemberJoinedInternalEvent(tripId, deviceId, joinedAt));

        return trip;
    }

    @Transactional(readOnly = true)
    public TripPreviewResponse getTripPreview(UUID tripId, UUID token, UUID deviceId) {
        TripInvitation invitation = tripInvitationRepository.findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (!invitation.getTrip().getId().equals(tripId)) {
            throw new BadRequestException("Token does not match this trip");
        }

        Trip trip = invitation.getTrip();
        if (trip.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Trip not found");
        }

        long memberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId);
        boolean isMember = tripMemberRepository
            .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId).isPresent();

        return TripPreviewResponse.builder()
            .id(trip.getId())
            .title(trip.getTitle())
            .description(trip.getDescription())
            .coverImageKey(trip.getCoverImageKey())
            .memberCount(memberCount)
            .isMember(isMember)
            .build();
    }

    /**
     * Internal event to decouple join logic from RabbitMQ publishing.
     * Handled by TripEventPublisher.
     */
    public record MemberJoinedInternalEvent(UUID tripId, UUID deviceId, Instant joinedAt) {}
}
