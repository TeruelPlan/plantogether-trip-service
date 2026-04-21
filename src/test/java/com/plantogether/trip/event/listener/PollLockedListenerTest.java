package com.plantogether.trip.event.listener;

import com.plantogether.common.event.PollLockedEvent;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollLockedListenerTest {

    @Mock TripRepository tripRepository;
    @InjectMocks PollLockedListener listener;

    UUID tripId;
    UUID pollId;
    UUID slotId;
    LocalDate start;
    LocalDate end;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        pollId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        start = LocalDate.of(2026, 6, 7);
        end = LocalDate.of(2026, 6, 8);
    }

    private PollLockedEvent event(LocalDate s, LocalDate e) {
        return PollLockedEvent.builder()
                .pollId(pollId.toString())
                .tripId(tripId.toString())
                .slotId(slotId.toString())
                .startDate(s)
                .endDate(e)
                .lockedByDeviceId(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .build();
    }

    private Trip trip(LocalDate s, LocalDate e) {
        return Trip.builder()
                .id(tripId)
                .title("Trip")
                .status(TripStatus.PLANNING)
                .createdBy(UUID.randomUUID())
                .referenceCurrency("EUR")
                .startDate(s)
                .endDate(e)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void onPollLocked_updatesStartAndEndDate() {
        Trip t = trip(null, null);
        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(t));

        listener.onPollLocked(event(start, end));

        assertEquals(start, t.getStartDate());
        assertEquals(end, t.getEndDate());
        verify(tripRepository).save(t);
    }

    @Test
    void onPollLocked_sameDatesAlreadySet_isIdempotent() {
        Trip t = trip(start, end);
        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(t));

        listener.onPollLocked(event(start, end));

        verify(tripRepository, never()).save(any());
    }

    @Test
    void onPollLocked_unknownTrip_logsAndAcks() {
        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.empty());

        listener.onPollLocked(event(start, end));

        verify(tripRepository, never()).save(any());
    }

    @Test
    void onPollLocked_archivedTrip_skipsAndAcks() {
        Trip t = trip(null, null);
        t.setStatus(TripStatus.ARCHIVED);
        when(tripRepository.findByIdAndDeletedAtIsNull(tripId)).thenReturn(Optional.of(t));

        listener.onPollLocked(event(start, end));

        verify(tripRepository, never()).save(any());
        assertEquals(null, t.getStartDate());
    }

    @Test
    void onPollLocked_malformedTripId_acksAndDrops() {
        PollLockedEvent bad = PollLockedEvent.builder()
                .pollId(pollId.toString())
                .tripId("not-a-uuid")
                .slotId(slotId.toString())
                .startDate(start)
                .endDate(end)
                .lockedByDeviceId(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .build();

        listener.onPollLocked(bad);

        verify(tripRepository, never()).save(any());
    }
}
