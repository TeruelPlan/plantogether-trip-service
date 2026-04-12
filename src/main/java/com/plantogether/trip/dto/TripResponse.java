package com.plantogether.trip.dto;

import com.plantogether.trip.domain.Trip;
import com.plantogether.trip.domain.TripMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripResponse {

    private UUID id;
    private String title;
    private String description;
    private String status;
    private String referenceCurrency;
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private int memberCount;
    private List<TripMemberResponse> members;

    public static TripResponse from(Trip trip, List<TripMember> members, UUID currentDeviceId) {
        List<TripMemberResponse> memberResponses = members.stream()
                .map(m -> TripMemberResponse.from(m, currentDeviceId))
                .toList();
        return TripResponse.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .description(trip.getDescription())
                .status(trip.getStatus().name())
                .referenceCurrency(trip.getReferenceCurrency())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .createdBy(trip.getCreatedBy())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .memberCount(memberResponses.size())
                .members(memberResponses)
                .build();
    }
}
