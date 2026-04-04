package com.plantogether.trip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPreviewResponse {
    private UUID id;
    private String title;
    private String description;
    private String coverImageKey;
    private long memberCount;
    @JsonProperty("isMember")
    private boolean isMember;
}
