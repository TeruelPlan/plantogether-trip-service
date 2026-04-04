package com.plantogether.trip.repository;

import com.plantogether.trip.domain.TripInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripInvitationRepository extends JpaRepository<TripInvitation, UUID> {

    Optional<TripInvitation> findByTripId(UUID tripId);

    Optional<TripInvitation> findByToken(UUID token);
}
