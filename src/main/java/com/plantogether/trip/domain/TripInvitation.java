package com.plantogether.trip.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "trip_invitation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripInvitation {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Column(nullable = false, unique = true)
  private UUID token;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
