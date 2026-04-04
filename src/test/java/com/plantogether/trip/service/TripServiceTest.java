package com.plantogether.trip.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.MemberRole;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.dto.TripResponse;
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
