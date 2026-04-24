package com.plantogether.trip.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.BadRequestException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.*;
import com.plantogether.trip.dto.TripMemberResponse;
import com.plantogether.trip.dto.TripResponse;
import com.plantogether.trip.dto.UpdateTripRequest;
import com.plantogether.trip.exception.TripStateException;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

  @Mock TripRepository tripRepository;
  @Mock TripMemberRepository tripMemberRepository;
  @Mock UserProfileService userProfileService;
  @Mock ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks TripService tripService;

  UUID deviceId;
  UserProfile profile;

  @BeforeEach
  void setUp() {
    deviceId = UUID.randomUUID();
    profile =
        UserProfile.builder()
            .deviceId(deviceId)
            .displayName("Alice")
            .updatedAt(Instant.now())
            .build();
  }

  // -------------------------------------------------------------------------

  @Nested
  class CreateTrip {

    @BeforeEach
    void stubDefaults() {
      when(userProfileService.getOrCreateProfile(deviceId, null, null)).thenReturn(profile);
      when(tripRepository.save(any(Trip.class)))
          .thenAnswer(
              inv -> {
                Trip t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
              });
      when(tripMemberRepository.save(any(TripMember.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createTrip_savesTrip_savesMember_publishesEvent() {
      Trip result = tripService.createTrip(deviceId, "Beach Trip", "Fun", "USD");

      assertNotNull(result.getId());
      assertEquals("Beach Trip", result.getTitle());
      assertEquals(TripStatus.PLANNING, result.getStatus());
      assertEquals("USD", result.getReferenceCurrency());
      assertEquals(deviceId, result.getCreatedBy());

      ArgumentCaptor<TripMember> memberCaptor = ArgumentCaptor.forClass(TripMember.class);
      verify(tripMemberRepository).save(memberCaptor.capture());
      TripMember savedMember = memberCaptor.getValue();
      assertEquals(deviceId, savedMember.getDeviceId());
      assertEquals(MemberRole.ORGANIZER, savedMember.getRole());
      assertEquals("Alice", savedMember.getDisplayName());

      verify(applicationEventPublisher).publishEvent(result);
    }

    @Test
    void createTrip_nullCurrency_defaultsToEUR() {
      Trip result = tripService.createTrip(deviceId, "Trip", null, null);
      assertEquals("EUR", result.getReferenceCurrency());
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  @MockitoSettings(strictness = Strictness.LENIENT)
  class GetTrip {

    UUID tripId;
    Trip trip;
    TripMember member;

    @BeforeEach
    void stubDefaults() {
      tripId = UUID.randomUUID();
      trip =
          Trip.builder()
              .id(tripId)
              .title("Test")
              .status(TripStatus.PLANNING)
              .createdBy(deviceId)
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      member =
          TripMember.builder()
              .deviceId(deviceId)
              .displayName("Alice")
              .role(MemberRole.ORGANIZER)
              .joinedAt(Instant.now())
              .build();

      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(member));
    }

    @Test
    void getTrip_memberExists_returnsTrip() {
      Trip result = tripService.getTrip(tripId, deviceId);
      assertEquals(tripId, result.getId());
    }

    @Test
    void getTrip_notMember_throwsAccessDenied() {
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.empty());
      assertThrows(AccessDeniedException.class, () -> tripService.getTrip(tripId, deviceId));
    }

    @Test
    void getTrip_notFound_throwsResourceNotFound() {
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.empty());
      assertThrows(ResourceNotFoundException.class, () -> tripService.getTrip(tripId, deviceId));
    }

    @Test
    void getTripResponse_memberExists_returnsTripResponseWithMembers() {
      when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)).thenReturn(List.of(member));

      TripResponse response = tripService.getTripResponse(tripId, deviceId);

      assertEquals(tripId, response.getId());
      assertEquals(1, response.getMemberCount());
      assertNotNull(response.getMembers());
      assertEquals(1, response.getMembers().size());
      assertEquals("Alice", response.getMembers().get(0).getDisplayName());
      assertEquals("ORGANIZER", response.getMembers().get(0).getRole());
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class ListTrips {

    @Test
    void listTripsForDevice_delegatesToRepository() {
      Trip trip = Trip.builder().id(UUID.randomUUID()).title("Test").build();
      when(tripRepository.findAllByMemberDeviceId(deviceId)).thenReturn(List.of(trip));

      List<Trip> result = tripService.listTripsForDevice(deviceId);

      assertEquals(1, result.size());
      verify(tripRepository).findAllByMemberDeviceId(deviceId);
    }

    @Test
    void listTripResponsesForDevice_returnsMemberCount() {
      UUID tripId = UUID.randomUUID();
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Test")
              .status(TripStatus.PLANNING)
              .createdBy(deviceId)
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      when(tripRepository.findAllByMemberDeviceId(deviceId)).thenReturn(List.of(trip));
      when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId)).thenReturn(1L);

      List<TripResponse> responses = tripService.listTripResponsesForDevice(deviceId);

      assertEquals(1, responses.size());
      assertEquals(1, responses.get(0).getMemberCount());
      assertNotNull(responses.get(0).getMembers());
      assertEquals(0, responses.get(0).getMembers().size());
      assertEquals("Test", responses.get(0).getTitle());
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  @MockitoSettings(strictness = Strictness.LENIENT)
  class UpdateTrip {

    UUID tripId;
    TripMember organizer;

    @BeforeEach
    void stubOrganizerAndSave() {
      tripId = UUID.randomUUID();
      organizer =
          TripMember.builder()
              .deviceId(deviceId)
              .role(MemberRole.ORGANIZER)
              .displayName("Alice")
              .joinedAt(Instant.now())
              .build();
      when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId))
          .thenReturn(List.of(organizer));
      when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubPlanningTrip() {
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Old Title")
              .description("Old Desc")
              .status(TripStatus.PLANNING)
              .createdBy(deviceId)
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(organizer));
    }

    @Test
    void updateTrip_organizerCanUpdateTitle() {
      stubPlanningTrip();
      UpdateTripRequest request = UpdateTripRequest.builder().title("New Title").build();
      TripResponse response = tripService.updateTrip(tripId, deviceId, request);
      assertEquals("New Title", response.getTitle());
      assertEquals("Old Desc", response.getDescription());
    }

    @Test
    void updateTrip_organizerCanUpdateDescriptionAndCurrency() {
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Trip")
              .description("Old")
              .status(TripStatus.PLANNING)
              .createdBy(deviceId)
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(organizer));

      UpdateTripRequest request =
          UpdateTripRequest.builder()
              .title("Trip")
              .description("New Desc")
              .referenceCurrency("USD")
              .build();
      TripResponse response = tripService.updateTrip(tripId, deviceId, request);

      assertEquals("New Desc", response.getDescription());
      assertEquals("USD", response.getReferenceCurrency());
    }

    @Test
    void updateTrip_participantGetsAccessDenied() {
      TripMember participant =
          TripMember.builder()
              .deviceId(deviceId)
              .role(MemberRole.PARTICIPANT)
              .displayName("Bob")
              .joinedAt(Instant.now())
              .build();
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Trip")
              .status(TripStatus.PLANNING)
              .createdBy(UUID.randomUUID())
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(participant));

      assertThrows(
          AccessDeniedException.class,
          () ->
              tripService.updateTrip(
                  tripId, deviceId, UpdateTripRequest.builder().title("Hacked").build()));
    }

    @Test
    void updateTrip_nonMemberGetsAccessDenied() {
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Trip")
              .status(TripStatus.PLANNING)
              .createdBy(UUID.randomUUID())
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.empty());

      assertThrows(
          AccessDeniedException.class,
          () ->
              tripService.updateTrip(
                  tripId, deviceId, UpdateTripRequest.builder().title("Hacked").build()));
    }

    @Test
    void updateTrip_archivedTripThrowsTripStateException() {
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Trip")
              .status(TripStatus.ARCHIVED)
              .createdBy(deviceId)
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(organizer));

      assertThrows(
          TripStateException.class,
          () ->
              tripService.updateTrip(
                  tripId, deviceId, UpdateTripRequest.builder().title("Nope").build()));
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  @MockitoSettings(strictness = Strictness.LENIENT)
  class ArchiveTrip {

    UUID tripId;
    TripMember organizer;

    @BeforeEach
    void stubOrganizerAndSave() {
      tripId = UUID.randomUUID();
      organizer =
          TripMember.builder()
              .deviceId(deviceId)
              .role(MemberRole.ORGANIZER)
              .displayName("Alice")
              .joinedAt(Instant.now())
              .build();
      when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId))
          .thenReturn(List.of(organizer));
      when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void archiveTrip_organizerCanArchivePlanningTrip() {
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Trip")
              .status(TripStatus.PLANNING)
              .createdBy(deviceId)
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(organizer));

      TripResponse response = tripService.archiveTrip(tripId, deviceId);

      assertEquals("ARCHIVED", response.getStatus());
    }

    @Test
    void archiveTrip_alreadyArchivedThrowsIllegalState() {
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Trip")
              .status(TripStatus.ARCHIVED)
              .createdBy(deviceId)
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(organizer));

      assertThrows(TripStateException.class, () -> tripService.archiveTrip(tripId, deviceId));
    }

    @Test
    void archiveTrip_participantGetsAccessDenied() {
      TripMember participant =
          TripMember.builder()
              .deviceId(deviceId)
              .role(MemberRole.PARTICIPANT)
              .displayName("Bob")
              .joinedAt(Instant.now())
              .build();
      Trip trip =
          Trip.builder()
              .id(tripId)
              .title("Trip")
              .status(TripStatus.PLANNING)
              .createdBy(UUID.randomUUID())
              .referenceCurrency("EUR")
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(participant));

      assertThrows(AccessDeniedException.class, () -> tripService.archiveTrip(tripId, deviceId));
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class TripMembers {

    UUID tripId;
    TripMember organizer;

    @BeforeEach
    void stubOrganizer() {
      tripId = UUID.randomUUID();
      organizer =
          TripMember.builder()
              .deviceId(deviceId)
              .displayName("Alice")
              .role(MemberRole.ORGANIZER)
              .joinedAt(Instant.now())
              .build();
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(organizer));
    }

    @Test
    void getTripMembers_memberExists_returnsMemberList() {
      when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId))
          .thenReturn(List.of(organizer));

      List<TripMemberResponse> result = tripService.getTripMembers(tripId, deviceId);

      assertEquals(1, result.size());
      assertEquals("Alice", result.get(0).getDisplayName());
      assertEquals("ORGANIZER", result.get(0).getRole());
    }

    @Test
    void getTripMembers_notMember_throwsAccessDenied() {
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.empty());
      assertThrows(AccessDeniedException.class, () -> tripService.getTripMembers(tripId, deviceId));
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class RemoveMember {

    UUID tripId;
    TripMember organizer;

    @BeforeEach
    void stubOrganizer() {
      tripId = UUID.randomUUID();
      organizer = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(organizer));
    }

    @Test
    void removeMember_organizerRemovesParticipant_softDeletes() {
      UUID memberId = UUID.randomUUID();
      UUID targetDeviceId = UUID.randomUUID();
      TripMember target =
          TripMember.builder().deviceId(targetDeviceId).role(MemberRole.PARTICIPANT).build();
      when(tripMemberRepository.findByIdAndTripIdAndDeletedAtIsNull(memberId, tripId))
          .thenReturn(Optional.of(target));
      when(tripMemberRepository.save(any(TripMember.class))).thenAnswer(inv -> inv.getArgument(0));

      tripService.removeMember(tripId, deviceId, memberId);

      assertNotNull(target.getDeletedAt());
      verify(tripMemberRepository).save(target);
    }

    @Test
    void removeMember_organizerRemovesSelf_throwsBadRequest() {
      UUID memberId = UUID.randomUUID();
      // target's deviceId matches the caller's deviceId
      TripMember selfMember =
          TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();
      when(tripMemberRepository.findByIdAndTripIdAndDeletedAtIsNull(memberId, tripId))
          .thenReturn(Optional.of(selfMember));

      assertThrows(
          BadRequestException.class, () -> tripService.removeMember(tripId, deviceId, memberId));
    }

    @Test
    void removeMember_participantRemovesAnother_throwsAccessDenied() {
      UUID memberId = UUID.randomUUID();
      TripMember participant =
          TripMember.builder().deviceId(deviceId).role(MemberRole.PARTICIPANT).build();
      when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
          .thenReturn(Optional.of(participant));

      assertThrows(
          AccessDeniedException.class, () -> tripService.removeMember(tripId, deviceId, memberId));
    }

    @Test
    void removeMember_targetNotFound_throwsResourceNotFound() {
      UUID memberId = UUID.randomUUID();
      when(tripMemberRepository.findByIdAndTripIdAndDeletedAtIsNull(memberId, tripId))
          .thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> tripService.removeMember(tripId, deviceId, memberId));
    }
  }
}
