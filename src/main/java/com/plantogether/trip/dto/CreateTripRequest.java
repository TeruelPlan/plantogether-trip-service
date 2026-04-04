package com.plantogether.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripRequest {

    @NotBlank(message = "Trip name is required")
    private String title;

    private String description;

    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code")
    private String currency;
}
