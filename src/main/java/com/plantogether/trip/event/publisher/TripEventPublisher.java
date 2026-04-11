package com.plantogether.trip.event.publisher;

import com.plantogether.common.event.MemberJoinedEvent;
import com.plantogether.common.event.TripCreatedEvent;
import com.plantogether.trip.config.RabbitConfig;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.service.TripService.MemberJoinedInternalEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TripEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TripEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishTripCreated(Trip trip) {
        TripCreatedEvent event = TripCreatedEvent.builder()
                .tripId(trip.getId())
                .name(trip.getTitle())
                .organizerDeviceId(trip.getCreatedBy().toString())
                .createdAt(trip.getCreatedAt())
                .build();
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY_TRIP_CREATED,
                event
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMemberJoined(MemberJoinedInternalEvent internalEvent) {
        MemberJoinedEvent event = MemberJoinedEvent.builder()
                .tripId(internalEvent.tripId())
                .deviceId(internalEvent.deviceId().toString())
                .joinedAt(internalEvent.joinedAt())
                .build();
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY_MEMBER_JOINED,
                event
        );
    }
}
