package com.plantogether.trip.dto;

import com.plantogether.trip.domain.TripMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripMemberResponse {
    private UUID deviceId;
    private String displayName;
    private String role;
    private Instant joinedAt;

    public static TripMemberResponse from(TripMember member) {
        return TripMemberResponse.builder()
                .deviceId(member.getDeviceId())
                .displayName(member.getDisplayName())
                .role(member.getRole().name())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
