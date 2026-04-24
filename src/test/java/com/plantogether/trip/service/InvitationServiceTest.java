package com.plantogether.trip.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.*;
import com.plantogether.trip.dto.TripInvitationResponse;
import com.plantogether.trip.repository.TripInvitationRepository;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

  @Mock private TripRepository tripRepository;

  @Mock private TripMemberRepository tripMemberRepository;

  @Mock private TripInvitationRepository tripInvitationRepository;

  private InvitationService invitationService;

  private UUID tripId;
  private UUID deviceId;
  private Trip trip;

  @BeforeEach
  void setUp() {
    invitationService =
        new InvitationService(
            tripRepository, tripMemberRepository, tripInvitationRepository, "http://localhost");
    tripId = UUID.randomUUID();
    deviceId = UUID.randomUUID();
    trip =
        Trip.builder()
            .id(tripId)
            .title("Test Trip")
            .status(TripStatus.PLANNING)
            .createdBy(deviceId)
            .referenceCurrency("EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Test
  void getOrCreateInvitation_createsNewInvitation() {
    TripMember organizer =
        TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();

    when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.of(organizer));
    when(tripInvitationRepository.findByTripId(tripId)).thenReturn(Optional.empty());
    when(tripInvitationRepository.save(any(TripInvitation.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    TripInvitationResponse response = invitationService.getOrCreateInvitation(tripId, deviceId);

    assertNotNull(response.getToken());
    assertTrue(response.getInviteUrl().contains("/trips/" + tripId + "/join?token="));
    verify(tripInvitationRepository).save(any(TripInvitation.class));
  }

  @Test
  void getOrCreateInvitation_reusesExistingToken() {
    UUID existingToken = UUID.randomUUID();
    TripMember organizer =
        TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();
    TripInvitation existing =
        TripInvitation.builder()
            .trip(trip)
            .token(existingToken)
            .createdBy(deviceId)
            .createdAt(Instant.now())
            .build();

    when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.of(organizer));
    when(tripInvitationRepository.findByTripId(tripId)).thenReturn(Optional.of(existing));

    TripInvitationResponse response = invitationService.getOrCreateInvitation(tripId, deviceId);

    assertEquals(existingToken.toString(), response.getToken());
    verify(tripInvitationRepository, never()).save(any());
  }

  @Test
  void getOrCreateInvitation_nonOrganizer_throwsForbidden() {
    TripMember participant =
        TripMember.builder().deviceId(deviceId).role(MemberRole.PARTICIPANT).build();

    when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.of(participant));

    assertThrows(
        AccessDeniedException.class,
        () -> invitationService.getOrCreateInvitation(tripId, deviceId));
  }

  @Test
  void getOrCreateInvitation_nonMember_throwsForbidden() {
    when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
        .thenReturn(Optional.empty());

    assertThrows(
        AccessDeniedException.class,
        () -> invitationService.getOrCreateInvitation(tripId, deviceId));
  }

  @Test
  void getOrCreateInvitation_tripNotFound_throws404() {
    when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> invitationService.getOrCreateInvitation(tripId, deviceId));
  }
}
