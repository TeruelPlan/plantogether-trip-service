package com.plantogether.trip.grpc.server;

import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.grpc.GetTripCurrencyRequest;
import com.plantogether.trip.grpc.GetTripCurrencyResponse;
import com.plantogether.trip.grpc.GetTripMembersRequest;
import com.plantogether.trip.grpc.GetTripMembersResponse;
import com.plantogether.trip.grpc.GetTripRequest;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripMemberProto;
import com.plantogether.trip.grpc.TripServiceGrpc;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TripGrpcServiceImpl extends TripServiceGrpc.TripServiceImplBase {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;

    public TripGrpcServiceImpl(TripRepository tripRepository, TripMemberRepository tripMemberRepository) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
    }

    @Override
    public void isMember(IsMemberRequest request, StreamObserver<IsMemberResponse> responseObserver) {
        try {
            UUID tripId = UUID.fromString(request.getTripId());
            UUID deviceId = UUID.fromString(request.getDeviceId());
            Optional<TripMember> member = tripMemberRepository
                .findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId);
            responseObserver.onNext(IsMemberResponse.newBuilder()
                .setIsMember(member.isPresent())
                .setRole(member.map(m -> m.getRole().name()).orElse(""))
                .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid UUID format").asException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asException());
        }
    }

    @Override
    public void getTripMembers(GetTripMembersRequest request, StreamObserver<GetTripMembersResponse> responseObserver) {
        try {
            UUID tripId = UUID.fromString(request.getTripId());
            tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("Trip not found: " + tripId).asRuntimeException());
            List<TripMember> members = tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId);
            var response = GetTripMembersResponse.newBuilder()
                .addAllMembers(members.stream()
                    .map(m -> TripMemberProto.newBuilder()
                        .setDeviceId(m.getDeviceId().toString())
                        .setRole(m.getRole().name())
                        .setDisplayName(m.getDisplayName())
                        .build())
                    .toList())
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid UUID format").asException());
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asException());
        }
    }

    @Override
    public void getTripCurrency(GetTripCurrencyRequest request, StreamObserver<GetTripCurrencyResponse> responseObserver) {
        try {
            UUID tripId = UUID.fromString(request.getTripId());
            Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("Trip not found: " + tripId).asRuntimeException());
            responseObserver.onNext(GetTripCurrencyResponse.newBuilder()
                .setCurrencyCode(trip.getReferenceCurrency())
                .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid UUID format").asException());
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asException());
        }
    }

    @Override
    public void getTrip(GetTripRequest request, StreamObserver<com.plantogether.trip.grpc.TripResponse> responseObserver) {
        try {
            UUID tripId = UUID.fromString(request.getTripId());
            Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("Trip not found: " + tripId).asRuntimeException());
            List<TripMember> members = tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId);
            var response = com.plantogether.trip.grpc.TripResponse.newBuilder()
                .setTripId(trip.getId().toString())
                .setName(trip.getTitle())
                .setOrganizerDeviceId(trip.getCreatedBy().toString())
                .addAllMemberDeviceIds(members.stream()
                    .map(m -> m.getDeviceId().toString())
                    .toList())
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid UUID format").asException());
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asException());
        }
    }
}
