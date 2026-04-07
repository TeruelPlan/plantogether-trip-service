package com.plantogether.trip.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.BadRequestException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.MemberRole;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.dto.TripMemberResponse;
import com.plantogether.trip.dto.TripResponse;
import com.plantogether.trip.dto.UpdateTripRequest;
import com.plantogether.trip.exception.TripStateException;
import org.springframework.context.ApplicationEventPublisher;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripMemberRepository tripMemberRepository;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TripService tripService;

    private UUID deviceId;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
        profile = UserProfile.builder()
            .deviceId(deviceId)
            .displayName("Alice")
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    void createTrip_savesTrip_savesMember_publishesEvent() {
        when(userProfileService.getOrCreateProfile(deviceId, null, null)).thenReturn(profile);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(tripMemberRepository.save(any(TripMember.class))).thenAnswer(inv -> inv.getArgument(0));

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
        when(userProfileService.getOrCreateProfile(deviceId, null, null)).thenReturn(profile);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(tripMemberRepository.save(any(TripMember.class))).thenAnswer(inv -> inv.getArgument(0));

        Trip result = tripService.createTrip(deviceId, "Trip", null, null);

        assertEquals("EUR", result.getReferenceCurrency());
    }

    @Test
    void getTrip_memberExists_returnsTrip() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Test").status(TripStatus.PLANNING)
            .createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));

        Trip result = tripService.getTrip(tripId, deviceId);
        assertEquals(tripId, result.getId());
    }

    @Test
    void getTrip_notMember_throwsAccessDenied() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Test").status(TripStatus.PLANNING)
            .createdBy(UUID.randomUUID()).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> tripService.getTrip(tripId, deviceId));
    }

    @Test
    void getTrip_notFound_throwsResourceNotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tripService.getTrip(tripId, deviceId));
    }

    @Test
    void listTripsForDevice_delegatesToRepository() {
        Trip trip = Trip.builder().id(UUID.randomUUID()).title("Test").build();
        when(tripRepository.findAllByMemberDeviceId(deviceId)).thenReturn(List.of(trip));

        List<Trip> result = tripService.listTripsForDevice(deviceId);
        assertEquals(1, result.size());
        verify(tripRepository).findAllByMemberDeviceId(deviceId);
    }

    @Test
    void getTripResponse_memberExists_returnsTripResponseWithMembers() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Test").status(TripStatus.PLANNING)
            .createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder()
            .deviceId(deviceId).displayName("Alice").role(MemberRole.ORGANIZER)
            .joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));
        when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId))
            .thenReturn(List.of(member));

        TripResponse response = tripService.getTripResponse(tripId, deviceId);

        assertEquals(tripId, response.getId());
        assertEquals(1, response.getMemberCount());
        assertNotNull(response.getMembers());
        assertEquals(1, response.getMembers().size());
        assertEquals("Alice", response.getMembers().get(0).getDisplayName());
        assertEquals("ORGANIZER", response.getMembers().get(0).getRole());
    }

    // --- updateTrip tests ---

    @Test
    void updateTrip_organizerCanUpdateTitle() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Old Title").description("Old Desc")
            .status(TripStatus.PLANNING).createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER)
            .displayName("Alice").joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)).thenReturn(List.of(member));

        UpdateTripRequest request = UpdateTripRequest.builder().title("New Title").build();
        TripResponse response = tripService.updateTrip(tripId, deviceId, request);

        assertEquals("New Title", response.getTitle());
        assertEquals("Old Desc", response.getDescription());
    }

    @Test
    void updateTrip_organizerCanUpdateDescriptionAndCurrency() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Trip").description("Old")
            .status(TripStatus.PLANNING).createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER)
            .displayName("Alice").joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)).thenReturn(List.of(member));

        UpdateTripRequest request = UpdateTripRequest.builder()
            .title("Trip").description("New Desc").referenceCurrency("USD").build();
        TripResponse response = tripService.updateTrip(tripId, deviceId, request);

        assertEquals("New Desc", response.getDescription());
        assertEquals("USD", response.getReferenceCurrency());
    }

    @Test
    void updateTrip_participantGetsAccessDenied() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Trip").status(TripStatus.PLANNING)
            .createdBy(UUID.randomUUID()).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.PARTICIPANT)
            .displayName("Bob").joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));

        UpdateTripRequest request = UpdateTripRequest.builder().title("Hacked").build();
        assertThrows(AccessDeniedException.class, () -> tripService.updateTrip(tripId, deviceId, request));
    }

    @Test
    void updateTrip_nonMemberGetsAccessDenied() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Trip").status(TripStatus.PLANNING)
            .createdBy(UUID.randomUUID()).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.empty());

        UpdateTripRequest request = UpdateTripRequest.builder().title("Hacked").build();
        assertThrows(AccessDeniedException.class, () -> tripService.updateTrip(tripId, deviceId, request));
    }

    // --- archiveTrip tests ---

    @Test
    void archiveTrip_organizerCanArchivePlanningTrip() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Trip").status(TripStatus.PLANNING)
            .createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER)
            .displayName("Alice").joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)).thenReturn(List.of(member));

        TripResponse response = tripService.archiveTrip(tripId, deviceId);

        assertEquals("ARCHIVED", response.getStatus());
    }

    @Test
    void archiveTrip_alreadyArchivedThrowsIllegalState() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Trip").status(TripStatus.ARCHIVED)
            .createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER)
            .displayName("Alice").joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));

        assertThrows(TripStateException.class, () -> tripService.archiveTrip(tripId, deviceId));
    }

    @Test
    void archiveTrip_participantGetsAccessDenied() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Trip").status(TripStatus.PLANNING)
            .createdBy(UUID.randomUUID()).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.PARTICIPANT)
            .displayName("Bob").joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));

        assertThrows(AccessDeniedException.class, () -> tripService.archiveTrip(tripId, deviceId));
    }

    @Test
    void updateTrip_archivedTripThrowsTripStateException() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Trip").status(TripStatus.ARCHIVED)
            .createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER)
            .displayName("Alice").joinedAt(Instant.now()).build();

        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));

        UpdateTripRequest request = UpdateTripRequest.builder().title("Nope").build();
        assertThrows(TripStateException.class, () -> tripService.updateTrip(tripId, deviceId, request));
    }

    // --- getTripMembers tests ---

    @Test
    void getTripMembers_memberExists_returnsMemberList() {
        UUID tripId = UUID.randomUUID();
        TripMember member = TripMember.builder()
            .deviceId(deviceId).displayName("Alice").role(MemberRole.ORGANIZER)
            .joinedAt(Instant.now()).build();

        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));
        when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId))
            .thenReturn(List.of(member));

        List<TripMemberResponse> result = tripService.getTripMembers(tripId, deviceId);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getDisplayName());
        assertEquals("ORGANIZER", result.get(0).getRole());
    }

    @Test
    void getTripMembers_notMember_throwsAccessDenied() {
        UUID tripId = UUID.randomUUID();

        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> tripService.getTripMembers(tripId, deviceId));
    }

    // --- removeMember tests ---

    @Test
    void removeMember_organizerRemovesParticipant_softDeletes() {
        UUID tripId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        TripMember organizer = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();
        TripMember target = TripMember.builder().deviceId(targetId).role(MemberRole.PARTICIPANT).build();

        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(organizer));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, targetId))
            .thenReturn(Optional.of(target));
        when(tripMemberRepository.save(any(TripMember.class))).thenAnswer(inv -> inv.getArgument(0));

        tripService.removeMember(tripId, deviceId, targetId);

        assertNotNull(target.getDeletedAt());
        verify(tripMemberRepository).save(target);
    }

    @Test
    void removeMember_organizerRemovesSelf_throwsBadRequest() {
        UUID tripId = UUID.randomUUID();
        TripMember organizer = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();

        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(organizer));

        assertThrows(BadRequestException.class, () -> tripService.removeMember(tripId, deviceId, deviceId));
    }

    @Test
    void removeMember_participantRemovesAnother_throwsAccessDenied() {
        UUID tripId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        TripMember participant = TripMember.builder().deviceId(deviceId).role(MemberRole.PARTICIPANT).build();

        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(participant));

        assertThrows(AccessDeniedException.class, () -> tripService.removeMember(tripId, deviceId, targetId));
    }

    @Test
    void removeMember_targetNotFound_throwsResourceNotFound() {
        UUID tripId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        TripMember organizer = TripMember.builder().deviceId(deviceId).role(MemberRole.ORGANIZER).build();

        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(organizer));
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, targetId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tripService.removeMember(tripId, deviceId, targetId));
    }

    @Test
    void listTripResponsesForDevice_returnsMemberCount() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).title("Test").status(TripStatus.PLANNING)
            .createdBy(deviceId).referenceCurrency("EUR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        TripMember member = TripMember.builder()
            .deviceId(deviceId).displayName("Alice").role(MemberRole.ORGANIZER)
            .joinedAt(Instant.now()).build();

        when(tripRepository.findAllByMemberDeviceId(deviceId)).thenReturn(List.of(trip));
        when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId))
            .thenReturn(1L);

        List<TripResponse> responses = tripService.listTripResponsesForDevice(deviceId);

        assertEquals(1, responses.size());
        assertEquals(1, responses.get(0).getMemberCount());
        assertNotNull(responses.get(0).getMembers());
        assertEquals(0, responses.get(0).getMembers().size());
        assertEquals("Test", responses.get(0).getTitle());
    }
}
