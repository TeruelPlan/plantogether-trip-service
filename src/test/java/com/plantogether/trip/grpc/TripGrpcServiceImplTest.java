package com.plantogether.trip.grpc;

import com.plantogether.trip.domain.MemberRole;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.grpc.server.TripGrpcServiceImpl;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import io.grpc.stub.StreamObserver;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripGrpcServiceImplTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripMemberRepository tripMemberRepository;

    @InjectMocks
    private TripGrpcServiceImpl grpcService;

    @Mock
    private StreamObserver<IsMemberResponse> isMemberObserver;

    @Mock
    private StreamObserver<GetTripMembersResponse> getMembersObserver;

    @Mock
    private StreamObserver<GetTripCurrencyResponse> getCurrencyObserver;

    private UUID tripId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
    }

    @Test
    void isMember_memberExists_returnsTrue() {
        TripMember member = TripMember.builder()
            .deviceId(deviceId).role(MemberRole.ORGANIZER).build();
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.of(member));

        grpcService.isMember(
            IsMemberRequest.newBuilder()
                .setTripId(tripId.toString())
                .setDeviceId(deviceId.toString())
                .build(),
            isMemberObserver
        );

        ArgumentCaptor<IsMemberResponse> captor = ArgumentCaptor.forClass(IsMemberResponse.class);
        verify(isMemberObserver).onNext(captor.capture());
        verify(isMemberObserver).onCompleted();

        assertTrue(captor.getValue().getIsMember());
        assertEquals("ORGANIZER", captor.getValue().getRole());
    }

    @Test
    void isMember_notMember_returnsFalse() {
        when(tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId))
            .thenReturn(Optional.empty());

        grpcService.isMember(
            IsMemberRequest.newBuilder()
                .setTripId(tripId.toString())
                .setDeviceId(deviceId.toString())
                .build(),
            isMemberObserver
        );

        ArgumentCaptor<IsMemberResponse> captor = ArgumentCaptor.forClass(IsMemberResponse.class);
        verify(isMemberObserver).onNext(captor.capture());
        verify(isMemberObserver).onCompleted();

        assertFalse(captor.getValue().getIsMember());
        assertEquals("", captor.getValue().getRole());
    }

    @Test
    void getTripMembers_returnsMemberList() {
        Trip trip = Trip.builder()
            .id(tripId).title("Test").referenceCurrency("EUR")
            .status(TripStatus.PLANNING).createdBy(deviceId)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));
        TripMember m1 = TripMember.builder()
            .deviceId(deviceId).role(MemberRole.ORGANIZER).displayName("Alice").build();
        UUID device2 = UUID.randomUUID();
        TripMember m2 = TripMember.builder()
            .deviceId(device2).role(MemberRole.PARTICIPANT).displayName("Bob").build();
        when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId))
            .thenReturn(List.of(m1, m2));

        grpcService.getTripMembers(
            GetTripMembersRequest.newBuilder().setTripId(tripId.toString()).build(),
            getMembersObserver
        );

        ArgumentCaptor<GetTripMembersResponse> captor = ArgumentCaptor.forClass(GetTripMembersResponse.class);
        verify(getMembersObserver).onNext(captor.capture());
        verify(getMembersObserver).onCompleted();

        assertEquals(2, captor.getValue().getMembersList().size());
        assertEquals("Alice", captor.getValue().getMembers(0).getDisplayName());
        assertEquals("ORGANIZER", captor.getValue().getMembers(0).getRole());
    }

    @Test
    void getTripCurrency_returnsCurrencyCode() {
        Trip trip = Trip.builder()
            .id(tripId).title("Test").referenceCurrency("USD")
            .status(TripStatus.PLANNING).createdBy(deviceId)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(trip));

        grpcService.getTripCurrency(
            GetTripCurrencyRequest.newBuilder().setTripId(tripId.toString()).build(),
            getCurrencyObserver
        );

        ArgumentCaptor<GetTripCurrencyResponse> captor = ArgumentCaptor.forClass(GetTripCurrencyResponse.class);
        verify(getCurrencyObserver).onNext(captor.capture());
        verify(getCurrencyObserver).onCompleted();

        assertEquals("USD", captor.getValue().getCurrencyCode());
    }
}
