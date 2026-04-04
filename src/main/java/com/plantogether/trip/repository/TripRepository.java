package com.plantogether.trip.repository;

import com.plantogether.trip.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
        SELECT DISTINCT t FROM Trip t
        JOIN t.members m
        WHERE m.deviceId = :deviceId
        AND m.deletedAt IS NULL
        AND t.deletedAt IS NULL
        ORDER BY t.createdAt DESC
    """)
    List<Trip> findAllByMemberDeviceId(@Param("deviceId") UUID deviceId);
}
