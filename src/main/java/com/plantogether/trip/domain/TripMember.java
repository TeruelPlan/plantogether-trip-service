package com.plantogether.trip.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "trip_member",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"trip_id", "device_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripMember {

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private MemberRole role;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
