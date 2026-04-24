package com.plantogether.trip.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.plantogether.common.exception.BadRequestException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.*;
import com.plantogether.trip.dto.TripPreviewResponse;
import com.plantogether.trip.dto.TripResponse;
import com.plantogether.trip.repository.TripInvitationRepository;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import com.plantogether.trip.repository.UserProfileRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TripServiceJoinTest {

  @Mock private TripRepository tripRepository;

  @Mock private TripMemberRepository tripMemberRepository;

  @Mock private TripInvitationRepository tripInvitationRepository;

  @Mock private UserProfileRepository userProfileRepository;

  @Mock private UserProfileService userProfileService;

  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks private TripService tripService;

  private UUID tripId;
  private UUID deviceId;
  private UUID token;
  private Trip trip;
  private TripInvitation invitation;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    deviceId = UUID.randomUUID();
    token = UUID.randomUUID();
    trip =
        Trip.builder()
            .id(tripId)
            .title("Test Trip")
            .status(TripStatus.PLANNING)
            .createdBy(UUID.randomUUID())
            .referenceCurrency("EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    invitation =
        TripInvitation.builder()
            .trip(trip)
            .token(token)
            .createdBy(trip.getCreatedBy())
            .createdAt(Instant.now())
            .build();
  }

  @Test
  void joinTrip_validToken_createsParticipant() {
    UserProfile profile =
        UserProfile.builder()
            .deviceId(deviceId)
            .displayName("Bob")
            .updatedAt(Instant.now())
            .build();

    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.empty());
    when(userProfileRepository.findById(deviceId)).thenReturn(Optional.of(profile));
    when(tripMemberRepository.save(any(TripMember.class))).thenAnswer(inv -> inv.getArgument(0));

    TripResponse result = tripService.joinTrip(tripId, token, deviceId);

    assertEquals(tripId, result.getId());
    verify(tripMemberRepository).save(any(TripMember.class));
    verify(applicationEventPublisher)
        .publishEvent(any(TripService.MemberJoinedInternalEvent.class));
  }

  @Test
  void joinTrip_alreadyMember_idempotent() {
    TripMember existing =
        TripMember.builder().deviceId(deviceId).role(MemberRole.PARTICIPANT).build();

    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.of(existing));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId))
        .thenReturn(java.util.List.of(existing));

    TripResponse result = tripService.joinTrip(tripId, token, deviceId);

    assertEquals(tripId, result.getId());
    verify(tripMemberRepository, never()).save(any());
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void joinTrip_invalidToken_throws404() {
    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> tripService.joinTrip(tripId, token, deviceId));
  }

  @Test
  void joinTrip_tokenMismatch_throws400() {
    UUID wrongTripId = UUID.randomUUID();
    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

    assertThrows(
        BadRequestException.class, () -> tripService.joinTrip(wrongTripId, token, deviceId));
  }

  @Test
  void joinTrip_noProfile_throws400() {
    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.empty());
    when(userProfileRepository.findById(deviceId)).thenReturn(Optional.empty());

    assertThrows(BadRequestException.class, () -> tripService.joinTrip(tripId, token, deviceId));
  }

  @Test
  void joinTrip_nullDisplayName_throws400() {
    UserProfile profileNoName =
        UserProfile.builder().deviceId(deviceId).displayName(null).updatedAt(Instant.now()).build();

    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.empty());
    when(userProfileRepository.findById(deviceId)).thenReturn(Optional.of(profileNoName));

    assertThrows(BadRequestException.class, () -> tripService.joinTrip(tripId, token, deviceId));
  }

  @Test
  void getTripPreview_validToken_returnsPreview() {
    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId)).thenReturn(1L);
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.empty());

    TripPreviewResponse result = tripService.getTripPreview(tripId, token, deviceId);

    assertEquals("Test Trip", result.getTitle());
    assertEquals(1, result.getMemberCount());
    assertFalse(result.isMember());
  }

  @Test
  void getTripPreview_existingMember_isMemberTrue() {
    TripMember existing = TripMember.builder().deviceId(deviceId).build();

    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId)).thenReturn(1L);
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.of(existing));

    TripPreviewResponse result = tripService.getTripPreview(tripId, token, deviceId);

    assertTrue(result.isMember());
  }

  @Test
  void getTripPreview_invalidToken_throws404() {
    when(tripInvitationRepository.findByToken(token)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> tripService.getTripPreview(tripId, token, deviceId));
  }
}
