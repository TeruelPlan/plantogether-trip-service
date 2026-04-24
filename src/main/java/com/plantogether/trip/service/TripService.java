package com.plantogether.trip.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.BadRequestException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.*;
import com.plantogether.trip.dto.TripMemberResponse;
import com.plantogether.trip.dto.TripPreviewResponse;
import com.plantogether.trip.dto.TripResponse;
import com.plantogether.trip.dto.UpdateTripRequest;
import com.plantogether.trip.exception.TripStateException;
import com.plantogether.trip.repository.TripInvitationRepository;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import com.plantogether.trip.repository.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

  private final TripRepository tripRepository;
  private final TripMemberRepository tripMemberRepository;
  private final TripInvitationRepository tripInvitationRepository;
  private final UserProfileRepository userProfileRepository;
  private final UserProfileService userProfileService;
  private final ApplicationEventPublisher applicationEventPublisher;

  public TripService(
      TripRepository tripRepository,
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

    Trip trip =
        Trip.builder()
            .title(title)
            .description(description)
            .status(TripStatus.PLANNING)
            .createdBy(deviceId)
            .referenceCurrency(currency != null && !currency.isBlank() ? currency : "EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    trip = tripRepository.save(trip);

    TripMember member =
        TripMember.builder()
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
    Trip trip =
        tripRepository
            .findByIdAndDeletedAtIsNull(tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
    tripMemberRepository
        .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
        .orElseThrow(() -> new AccessDeniedException("Not a member of this trip"));
    return trip;
  }

  @Transactional(readOnly = true)
  public List<Trip> listTripsForDevice(UUID deviceId) {
    return tripRepository.findAllByMemberDeviceId(deviceId);
  }

  @Transactional(readOnly = true)
  public TripResponse getTripResponse(UUID tripId, UUID deviceId) {
    Trip trip =
        tripRepository
            .findByIdAndDeletedAtIsNull(tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
    tripMemberRepository
        .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
        .orElseThrow(() -> new AccessDeniedException("Not a member of this trip"));
    List<TripMember> members = tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId);
    return TripResponse.from(trip, members, deviceId);
  }

  @Transactional(readOnly = true)
  public List<TripResponse> listTripResponsesForDevice(UUID deviceId) {
    List<Trip> trips = tripRepository.findAllByMemberDeviceId(deviceId);
    return trips.stream()
        .map(
            trip -> {
              long memberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId());
              return TripResponse.builder()
                  .id(trip.getId())
                  .title(trip.getTitle())
                  .description(trip.getDescription())
                  .status(trip.getStatus().name())
                  .referenceCurrency(trip.getReferenceCurrency())
                  .startDate(trip.getStartDate())
                  .endDate(trip.getEndDate())
                  .createdBy(trip.getCreatedBy())
                  .createdAt(trip.getCreatedAt())
                  .updatedAt(trip.getUpdatedAt())
                  .memberCount((int) memberCount)
                  .members(List.of())
                  .build();
            })
        .toList();
  }

  @Transactional
  public TripResponse updateTrip(UUID tripId, UUID deviceId, UpdateTripRequest request) {
    Trip trip =
        tripRepository
            .findByIdAndDeletedAtIsNull(tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
    requireOrganizer(tripId, deviceId);

    if (trip.getStatus() == TripStatus.ARCHIVED) {
      throw new TripStateException("Cannot update an archived trip");
    }

    trip.setTitle(request.getTitle());
    if (request.getDescription() != null) {
      trip.setDescription(request.getDescription().isEmpty() ? null : request.getDescription());
    }
    if (request.getReferenceCurrency() != null) {
      trip.setReferenceCurrency(request.getReferenceCurrency());
    }
    trip.setUpdatedAt(Instant.now());
    trip = tripRepository.save(trip);

    List<TripMember> members = tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId);
    return TripResponse.from(trip, members, deviceId);
  }

  @Transactional
  public TripResponse archiveTrip(UUID tripId, UUID deviceId) {
    Trip trip =
        tripRepository
            .findByIdAndDeletedAtIsNull(tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
    requireOrganizer(tripId, deviceId);

    if (trip.getStatus() == TripStatus.ARCHIVED) {
      throw new TripStateException("Trip is already archived");
    }

    trip.setStatus(TripStatus.ARCHIVED);
    trip.setUpdatedAt(Instant.now());
    trip = tripRepository.save(trip);

    List<TripMember> members = tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId);
    return TripResponse.from(trip, members, deviceId);
  }

  @Transactional
  public TripResponse joinTrip(UUID tripId, UUID token, UUID deviceId) {
    TripInvitation invitation =
        tripInvitationRepository
            .findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

    if (!invitation.getTrip().getId().equals(tripId)) {
      throw new BadRequestException("Token does not match this trip");
    }

    if (invitation.getTrip().getDeletedAt() != null) {
      throw new ResourceNotFoundException("Trip no longer exists: " + tripId);
    }

    if (tripMemberRepository
        .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
        .isPresent()) {
      List<TripMember> existingMembers =
          tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId);
      return TripResponse.from(invitation.getTrip(), existingMembers, deviceId);
    }

    UserProfile profile =
        userProfileRepository
            .findById(deviceId)
            .orElseThrow(() -> new BadRequestException("Display name required"));

    if (profile.getDisplayName() == null) {
      throw new BadRequestException("Display name required");
    }

    TripMember member =
        TripMember.builder()
            .trip(invitation.getTrip())
            .deviceId(deviceId)
            .displayName(profile.getDisplayName())
            .role(MemberRole.PARTICIPANT)
            .joinedAt(Instant.now())
            .build();
    tripMemberRepository.save(member);

    applicationEventPublisher.publishEvent(
        new MemberJoinedInternalEvent(tripId, deviceId, member.getJoinedAt()));

    List<TripMember> allMembers = tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId);
    return TripResponse.from(invitation.getTrip(), allMembers, deviceId);
  }

  @Transactional(readOnly = true)
  public TripPreviewResponse getTripPreview(UUID tripId, UUID token, UUID deviceId) {
    TripInvitation invitation =
        tripInvitationRepository
            .findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

    if (!invitation.getTrip().getId().equals(tripId)) {
      throw new ResourceNotFoundException("Invitation not found");
    }

    Trip trip = invitation.getTrip();
    if (trip.getDeletedAt() != null) {
      throw new ResourceNotFoundException("Trip no longer exists: " + tripId);
    }
    long memberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId);
    boolean isMember =
        tripMemberRepository
            .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
            .isPresent();

    return TripPreviewResponse.builder()
        .id(trip.getId())
        .title(trip.getTitle())
        .description(trip.getDescription())
        .coverImageKey(trip.getCoverImageKey())
        .memberCount(memberCount)
        .isMember(isMember)
        .build();
  }

  @Transactional(readOnly = true)
  public List<TripMemberResponse> getTripMembers(UUID tripId, UUID deviceId) {
    tripMemberRepository
        .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
        .orElseThrow(() -> new AccessDeniedException("Not a member of this trip"));
    return tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
        .map(m -> TripMemberResponse.from(m, deviceId))
        .toList();
  }

  @Transactional
  public void removeMember(UUID tripId, UUID callerDeviceId, UUID memberId) {
    requireOrganizer(tripId, callerDeviceId);
    TripMember target =
        tripMemberRepository
            .findByIdAndTripIdAndDeletedAtIsNull(memberId, tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
    if (target.getDeviceId().equals(callerDeviceId)) {
      throw new BadRequestException("Organizer cannot remove themselves");
    }
    if (target.getRole() == MemberRole.ORGANIZER) {
      throw new BadRequestException("Cannot remove another organizer");
    }
    target.setDeletedAt(Instant.now());
    tripMemberRepository.save(target);
  }

  private TripMember requireOrganizer(UUID tripId, UUID deviceId) {
    TripMember member =
        tripMemberRepository
            .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
            .orElseThrow(() -> new AccessDeniedException("Not a member of this trip"));
    if (member.getRole() != MemberRole.ORGANIZER) {
      throw new AccessDeniedException("Only the organizer can perform this action");
    }
    return member;
  }

  @Transactional
  public TripResponse createTripWithResponse(
      UUID deviceId, String title, String description, String currency) {
    Trip trip = createTrip(deviceId, title, description, currency);
    List<TripMember> members = tripMemberRepository.findByTripIdAndDeletedAtIsNull(trip.getId());
    return TripResponse.from(trip, members, deviceId);
  }

  public record MemberJoinedInternalEvent(UUID tripId, UUID deviceId, Instant joinedAt) {}
}
