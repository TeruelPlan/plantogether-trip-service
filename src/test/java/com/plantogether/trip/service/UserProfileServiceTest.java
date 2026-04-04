package com.plantogether.trip.service;

import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.domain.UserProfile;
import com.plantogether.trip.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private UUID testDeviceId;
    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
        testDeviceId = UUID.randomUUID();
        testProfile = UserProfile.builder()
            .deviceId(testDeviceId)
            .displayName("Alice")
            .avatarUrl("https://example.com/alice.jpg")
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    void getOrCreateProfile_noExistingRow_createsAndSavesNew() {
        when(userProfileRepository.findById(testDeviceId)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(testProfile);

        UserProfile result = userProfileService.getOrCreateProfile(
            testDeviceId, "Alice", "https://example.com/alice.jpg"
        );

        assertNotNull(result);
        assertEquals(testDeviceId, result.getDeviceId());
        assertEquals("Alice", result.getDisplayName());
        verify(userProfileRepository).findById(testDeviceId);
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void getOrCreateProfile_existingRow_returnsWithoutOverwrite() {
        when(userProfileRepository.findById(testDeviceId)).thenReturn(Optional.of(testProfile));

        UserProfile result = userProfileService.getOrCreateProfile(
            testDeviceId, "Bob", "https://example.com/bob.jpg"
        );

        assertNotNull(result);
        assertEquals("Alice", result.getDisplayName());
        verify(userProfileRepository).findById(testDeviceId);
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void updateDisplayName_existingRow_savesUpdatedProfile() {
        when(userProfileRepository.findById(testDeviceId)).thenReturn(Optional.of(testProfile));
        when(userProfileRepository.save(testProfile)).thenReturn(testProfile);

        UserProfile result = userProfileService.updateDisplayName(testDeviceId, "Alice Updated");

        assertNotNull(result);
        verify(userProfileRepository).findById(testDeviceId);
        verify(userProfileRepository).save(testProfile);
    }

    @Test
    void updateDisplayName_noRow_throwsResourceNotFoundException() {
        when(userProfileRepository.findById(testDeviceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> userProfileService.updateDisplayName(testDeviceId, "New Name")
        );

        verify(userProfileRepository).findById(testDeviceId);
        verify(userProfileRepository, never()).save(any());
    }
}
