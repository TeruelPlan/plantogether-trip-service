package com.plantogether.trip.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantogether.trip.AbstractIntegrationTest;
import com.plantogether.trip.domain.MemberRole;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.grpc.server.TripGrpcServiceImpl;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TripGrpcIT extends AbstractIntegrationTest {

  @Autowired private TripGrpcServiceImpl tripGrpcService;
  @Autowired private TripRepository tripRepository;
  @Autowired private TripMemberRepository tripMemberRepository;

  private Server server;
  private ManagedChannel channel;
  private TripServiceGrpc.TripServiceBlockingStub stub;

  @BeforeEach
  void startGrpc() throws Exception {
    String name = InProcessServerBuilder.generateName();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(tripGrpcService)
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    stub = TripServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void stopGrpc() throws Exception {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private Trip persistTrip(UUID organizer, String currency) {
    Instant now = Instant.now();
    Trip trip =
        Trip.builder()
            .title("Test Trip")
            .status(TripStatus.PLANNING)
            .createdBy(organizer)
            .referenceCurrency(currency)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Trip saved = tripRepository.save(trip);

    TripMember member =
        TripMember.builder()
            .trip(saved)
            .deviceId(organizer)
            .displayName("Alice")
            .role(MemberRole.ORGANIZER)
            .joinedAt(now)
            .build();
    tripMemberRepository.save(member);
    return saved;
  }

  @Test
  void isMember_existingMember_returnsTrueWithRole() {
    UUID organizer = UUID.randomUUID();
    Trip trip = persistTrip(organizer, "EUR");

    IsMemberResponse response =
        stub.isMember(
            IsMemberRequest.newBuilder()
                .setTripId(trip.getId().toString())
                .setDeviceId(organizer.toString())
                .build());

    assertThat(response.getIsMember()).isTrue();
    assertThat(response.getRole()).isEqualTo("ORGANIZER");
  }

  @Test
  void isMember_nonMember_returnsFalse() {
    UUID organizer = UUID.randomUUID();
    Trip trip = persistTrip(organizer, "EUR");

    IsMemberResponse response =
        stub.isMember(
            IsMemberRequest.newBuilder()
                .setTripId(trip.getId().toString())
                .setDeviceId(UUID.randomUUID().toString())
                .build());

    assertThat(response.getIsMember()).isFalse();
    assertThat(response.getRole()).isEmpty();
  }

  @Test
  void isMember_invalidUuid_returnsInvalidArgument() {
    assertThatThrownBy(
            () ->
                stub.isMember(
                    IsMemberRequest.newBuilder()
                        .setTripId("not-a-uuid")
                        .setDeviceId(UUID.randomUUID().toString())
                        .build()))
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void getTripMembers_returnsAllMembers() {
    UUID organizer = UUID.randomUUID();
    Trip trip = persistTrip(organizer, "EUR");

    GetTripMembersResponse response =
        stub.getTripMembers(
            GetTripMembersRequest.newBuilder().setTripId(trip.getId().toString()).build());

    assertThat(response.getMembersList()).hasSize(1);
    assertThat(response.getMembers(0).getTripMemberId()).isNotEmpty();
    assertThat(response.getMembers(0).getRole()).isEqualTo("ORGANIZER");
    assertThat(response.getMembers(0).getDisplayName()).isEqualTo("Alice");
  }

  @Test
  void getTripMembers_unknownTrip_returnsNotFound() {
    assertThatThrownBy(
            () ->
                stub.getTripMembers(
                    GetTripMembersRequest.newBuilder()
                        .setTripId(UUID.randomUUID().toString())
                        .build()))
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
        .isEqualTo(Status.Code.NOT_FOUND);
  }

  @Test
  void getTripCurrency_returnsReferenceCurrency() {
    UUID organizer = UUID.randomUUID();
    Trip trip = persistTrip(organizer, "USD");

    GetTripCurrencyResponse response =
        stub.getTripCurrency(
            GetTripCurrencyRequest.newBuilder().setTripId(trip.getId().toString()).build());

    assertThat(response.getCurrencyCode()).isEqualTo("USD");
  }

  @Test
  void getTrip_returnsTripWithMembers() {
    UUID organizer = UUID.randomUUID();
    Trip trip = persistTrip(organizer, "EUR");

    TripResponse response =
        stub.getTrip(GetTripRequest.newBuilder().setTripId(trip.getId().toString()).build());

    assertThat(response.getTripId()).isEqualTo(trip.getId().toString());
    assertThat(response.getName()).isEqualTo("Test Trip");
    assertThat(response.getOrganizerDeviceId()).isEqualTo(organizer.toString());
    assertThat(response.getMemberDeviceIdsList()).containsExactly(organizer.toString());
  }
}
