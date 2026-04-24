package com.plantogether.trip.repository;

import com.plantogether.trip.domain.UserProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {}
