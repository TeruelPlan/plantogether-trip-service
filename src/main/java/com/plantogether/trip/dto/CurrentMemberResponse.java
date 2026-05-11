package com.plantogether.trip.dto;

import com.plantogether.trip.domain.TripMember;
import java.util.UUID;

public record CurrentMemberResponse(UUID tripMemberId, String displayName, String role) {
  public static CurrentMemberResponse from(TripMember member) {
    return new CurrentMemberResponse(
        member.getId(), member.getDisplayName(), member.getRole().name());
  }
}
