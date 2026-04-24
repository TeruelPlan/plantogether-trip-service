package com.plantogether.trip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plantogether.trip.domain.TripMember;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripMemberResponse {
  private UUID id;
  private String displayName;
  private String role;
  private Instant joinedAt;

  @JsonProperty("isMe")
  private boolean isMe;

  public static TripMemberResponse from(TripMember member, UUID currentDeviceId) {
    return TripMemberResponse.builder()
        .id(member.getId())
        .displayName(member.getDisplayName())
        .role(member.getRole().name())
        .joinedAt(member.getJoinedAt())
        .isMe(member.getDeviceId().equals(currentDeviceId))
        .build();
  }
}
