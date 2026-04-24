package com.plantogether.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantogether.trip.dto.CreateTripRequest;
import com.plantogether.trip.dto.JoinTripRequest;
import com.plantogether.trip.dto.TripInvitationResponse;
import com.plantogether.trip.dto.TripResponse;
import com.plantogether.trip.dto.UpdateProfileRequest;
import com.plantogether.trip.dto.UserProfileResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class TripCrudIT extends AbstractIntegrationTest {

  private HttpHeaders headers(UUID deviceId) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.set("X-Device-Id", deviceId.toString());
    return h;
  }

  private TripResponse createTrip(UUID deviceId, String title) {
    CreateTripRequest body =
        CreateTripRequest.builder().title(title).description("desc").currency("EUR").build();
    ResponseEntity<TripResponse> response =
        restTemplate.exchange(
            "/api/v1/trips",
            HttpMethod.POST,
            new HttpEntity<>(body, headers(deviceId)),
            TripResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  @Test
  void createTrip_returns201() {
    UUID deviceId = UUID.randomUUID();
    TripResponse trip = createTrip(deviceId, "Beach Trip");

    assertThat(trip).isNotNull();
    assertThat(trip.getId()).isNotNull();
    assertThat(trip.getTitle()).isEqualTo("Beach Trip");
    assertThat(trip.getStatus()).isEqualTo("PLANNING");
    assertThat(trip.getReferenceCurrency()).isEqualTo("EUR");
    assertThat(trip.getCreatedBy()).isEqualTo(deviceId);
  }

  @Test
  void listTrips_returnsOwnedTrips() {
    UUID deviceA = UUID.randomUUID();
    UUID deviceB = UUID.randomUUID();
    createTrip(deviceA, "Trip A1");
    createTrip(deviceA, "Trip A2");
    createTrip(deviceB, "Trip B1");

    ResponseEntity<List<TripResponse>> response =
        restTemplate.exchange(
            "/api/v1/trips",
            HttpMethod.GET,
            new HttpEntity<>(headers(deviceA)),
            new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(2);
    assertThat(response.getBody())
        .extracting(TripResponse::getTitle)
        .containsExactlyInAnyOrder("Trip A1", "Trip A2");
  }

  @Test
  void getTrip_memberCanFetch() {
    UUID deviceId = UUID.randomUUID();
    TripResponse created = createTrip(deviceId, "My Trip");

    ResponseEntity<TripResponse> response =
        restTemplate.exchange(
            "/api/v1/trips/" + created.getId(),
            HttpMethod.GET,
            new HttpEntity<>(headers(deviceId)),
            TripResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getTitle()).isEqualTo("My Trip");
  }

  @Test
  void getTrip_nonMember_returns403() {
    UUID organizer = UUID.randomUUID();
    UUID intruder = UUID.randomUUID();
    TripResponse created = createTrip(organizer, "Private Trip");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/trips/" + created.getId(),
            HttpMethod.GET,
            new HttpEntity<>(headers(intruder)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void archiveTrip_organizerCanArchive() {
    UUID deviceId = UUID.randomUUID();
    TripResponse created = createTrip(deviceId, "To Archive");

    ResponseEntity<TripResponse> response =
        restTemplate.exchange(
            "/api/v1/trips/" + created.getId() + "/archive",
            HttpMethod.PATCH,
            new HttpEntity<>(headers(deviceId)),
            TripResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getStatus()).isEqualTo("ARCHIVED");
  }

  private void setDisplayName(UUID deviceId, String name) {
    restTemplate.exchange(
        "/api/v1/users/me",
        HttpMethod.GET,
        new HttpEntity<>(headers(deviceId)),
        UserProfileResponse.class);

    UpdateProfileRequest body = new UpdateProfileRequest(name);
    ResponseEntity<UserProfileResponse> response =
        restTemplate.exchange(
            "/api/v1/users/me",
            HttpMethod.PUT,
            new HttpEntity<>(body, headers(deviceId)),
            UserProfileResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void joinTrip_withValidToken() {
    UUID organizer = UUID.randomUUID();
    UUID joiner = UUID.randomUUID();
    setDisplayName(joiner, "Bob");
    TripResponse created = createTrip(organizer, "Shared Trip");

    ResponseEntity<TripInvitationResponse> invitation =
        restTemplate.exchange(
            "/api/v1/trips/" + created.getId() + "/invitation",
            HttpMethod.GET,
            new HttpEntity<>(headers(organizer)),
            TripInvitationResponse.class);
    assertThat(invitation.getStatusCode()).isEqualTo(HttpStatus.OK);
    UUID token = UUID.fromString(invitation.getBody().getToken());

    JoinTripRequest joinBody = JoinTripRequest.builder().token(token).build();
    ResponseEntity<TripResponse> joined =
        restTemplate.exchange(
            "/api/v1/trips/" + created.getId() + "/join",
            HttpMethod.POST,
            new HttpEntity<>(joinBody, headers(joiner)),
            TripResponse.class);

    assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(joined.getBody().getId()).isEqualTo(created.getId());
  }
}
