package com.plantogether.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
  @NotBlank
  @Size(max = 100)
  private String displayName;

  public UpdateProfileRequest() {}

  public UpdateProfileRequest(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }
}
