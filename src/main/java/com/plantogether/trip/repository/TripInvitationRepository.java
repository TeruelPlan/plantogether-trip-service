package com.plantogether.trip.repository;

import com.plantogether.trip.domain.TripInvitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripInvitationRepository extends JpaRepository<TripInvitation, UUID> {

  Optional<TripInvitation> findByTripId(UUID tripId);

  Optional<TripInvitation> findByToken(UUID token);
}
