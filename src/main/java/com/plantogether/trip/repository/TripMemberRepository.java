package com.plantogether.trip.repository;

import com.plantogether.trip.domain.TripMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

  Optional<TripMember> findByTripIdAndDeviceIdAndDeletedAtIsNull(UUID tripId, UUID deviceId);

  Optional<TripMember> findByIdAndTripIdAndDeletedAtIsNull(UUID id, UUID tripId);

  List<TripMember> findByTripIdAndDeletedAtIsNull(UUID tripId);

  long countByTripIdAndDeletedAtIsNull(UUID tripId);

  List<TripMember> findByDeviceIdAndDeletedAtIsNull(UUID deviceId);
}
