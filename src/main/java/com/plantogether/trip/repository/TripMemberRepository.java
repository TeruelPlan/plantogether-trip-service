package com.plantogether.trip.repository;

import com.plantogether.trip.domain.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

    Optional<TripMember> findByTripIdAndDeviceIdAndDeletedAtIsNull(UUID tripId, UUID deviceId);

    List<TripMember> findByTripIdAndDeletedAtIsNull(UUID tripId);

    long countByTripIdAndDeletedAtIsNull(UUID tripId);

    List<TripMember> findByDeviceIdAndDeletedAtIsNull(UUID deviceId);
}
