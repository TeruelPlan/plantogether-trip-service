package com.plantogether.trip.event.listener;

import com.plantogether.common.event.PollLockedEvent;
import com.plantogether.trip.config.RabbitConfig;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripStatus;
import com.plantogether.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PollLockedListener {

    private final TripRepository tripRepository;

    @Transactional
    @RabbitListener(queues = RabbitConfig.QUEUE_POLL_LOCKED)
    public void onPollLocked(PollLockedEvent event) {
        UUID tripId;
        try {
            tripId = UUID.fromString(event.getTripId());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("PollLockedEvent with malformed tripId={} — ack and drop", event.getTripId());
            return;
        }
        tripRepository.findByIdAndDeletedAtIsNull(tripId).ifPresentOrElse(trip -> {
            if (trip.getStatus() == TripStatus.ARCHIVED) {
                log.warn("PollLockedEvent for archived trip={} — ack and drop", tripId);
                return;
            }
            if (sameDates(trip, event)) {
                log.debug("PollLockedEvent for trip={} already applied, skipping", tripId);
                return;
            }
            trip.setStartDate(event.getStartDate());
            trip.setEndDate(event.getEndDate());
            trip.setUpdatedAt(Instant.now());
            tripRepository.save(trip);
            log.info("Updated trip={} dates to {}–{} from poll.locked event pollId={}",
                    tripId, event.getStartDate(), event.getEndDate(), event.getPollId());
        }, () -> log.warn("PollLockedEvent for unknown or deleted tripId={} — ack and drop", tripId));
    }

    private boolean sameDates(Trip trip, PollLockedEvent event) {
        return event.getStartDate().equals(trip.getStartDate())
                && event.getEndDate().equals(trip.getEndDate());
    }
}
