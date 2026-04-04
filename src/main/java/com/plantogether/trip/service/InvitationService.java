package com.plantogether.trip.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import com.plantogether.trip.domain.MemberRole;
import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripInvitation;
import com.plantogether.trip.domain.TripMember;
import com.plantogether.trip.dto.TripInvitationResponse;
import com.plantogether.trip.repository.TripInvitationRepository;
import com.plantogether.trip.repository.TripMemberRepository;
import com.plantogether.trip.repository.TripRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InvitationService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripInvitationRepository tripInvitationRepository;
    private final String inviteBaseUrl;

    public InvitationService(TripRepository tripRepository,
                             TripMemberRepository tripMemberRepository,
                             TripInvitationRepository tripInvitationRepository,
                             @Value("${app.invite-base-url:http://localhost}") String inviteBaseUrl) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.tripInvitationRepository = tripInvitationRepository;
        this.inviteBaseUrl = inviteBaseUrl;
    }

    @Transactional
    public TripInvitationResponse getOrCreateInvitation(UUID tripId, UUID deviceId) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        TripMember member = tripMemberRepository.findByTripIdAndDeviceIdAndDeletedAtIsNull(tripId, deviceId)
            .orElseThrow(() -> new AccessDeniedException("Not a member of this trip"));

        if (member.getRole() != MemberRole.ORGANIZER) {
            throw new AccessDeniedException("Only the organizer can generate invitations");
        }

        TripInvitation invitation = tripInvitationRepository.findByTripId(tripId)
            .orElseGet(() -> {
                try {
                    TripInvitation newInvitation = TripInvitation.builder()
                        .trip(trip)
                        .token(UUID.randomUUID())
                        .createdBy(deviceId)
                        .createdAt(Instant.now())
                        .build();
                    return tripInvitationRepository.save(newInvitation);
                } catch (DataIntegrityViolationException e) {
                    return tripInvitationRepository.findByTripId(tripId)
                        .orElseThrow(() -> e);
                }
            });

        String inviteUrl = inviteBaseUrl + "/trips/" + tripId + "/join?token=" + invitation.getToken();
        return TripInvitationResponse.builder()
            .inviteUrl(inviteUrl)
            .token(invitation.getToken().toString())
            .build();
    }
}
