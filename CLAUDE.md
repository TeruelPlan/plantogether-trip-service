# CLAUDE.md

This file provides guidance to Claude when working with code in this repository.

## Commands

```bash
# Run the application
mvn spring-boot:run

# Build JAR
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Docker build
docker build -t plantogether-trip-service .
docker run -p 8081:8081 -p 9081:9081 \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  plantogether-trip-service
```

**Prerequisites:** install shared libs first:

```bash
cd ../plantogether-proto && mvn clean install
cd ../plantogether-common && mvn clean install
```

## Architecture

Spring Boot 3.3.6 microservice (Java 21). Manages collaborative trips, members, invitations, and user profiles.

**Ports:** REST `8081` · gRPC `9081`

**Package:** `com.plantogether.trip`

### Package structure

```
com.plantogether.trip/
├── config/          # RabbitConfig, GrpcServerConfig
├── controller/      # REST controllers (TripController, UserProfileController)
├── domain/          # JPA entities (Trip, TripMember, UserProfile, TripStatus, MemberRole)
├── repository/      # Spring Data JPA
├── service/         # Business logic (TripService, UserProfileService)
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   ├── server/      # TripGrpcServiceImpl (exposes gRPC on port 9081)
│   └── client/      # (none — trip-service has no outgoing gRPC calls)
├── event/
│   ├── publisher/   # RabbitMQ publishers (TripEventPublisher)
│   └── listener/    # RabbitMQ consumers (poll.locked)
└── exception/       # GlobalExceptionHandler (RFC 9457 ProblemDetail)
```

### Infrastructure dependencies

| Dependency    | Default (local)                    | Purpose                                        |
|---------------|------------------------------------|------------------------------------------------|
| PostgreSQL 16 | `localhost:5432/plantogether_trip` | Primary persistence                            |
| RabbitMQ      | `localhost:5672`                   | Event publishing + consuming                   |
| Redis         | `localhost:6379`                   | Rate limiting (Bucket4j)                       |
| MinIO         | `localhost:9000`                   | Cover image storage (key only, no passthrough) |

### Security model

- **Anonymous device identity** — no login, no JWT, no sessions
- `DeviceIdFilter` from `plantogether-common` (auto-configured via `SecurityAutoConfiguration`) extracts `X-Device-Id`
  header and sets `SecurityContext` principal
- `authentication.getName()` returns the device UUID string in controllers
- Public endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/actuator/metrics`
- Trip-scoped authorization: membership checked via `TripMemberRepository` (ORGANIZER role for write ops)
- **Do NOT create a SecurityConfig.java** — `SecurityAutoConfiguration` handles everything

### Domain model (db_trip)

**`user_profile`** — lightweight profile keyed by device UUID (zero PII — no email, no real name):

| Column         | Type         | Notes                               |
|----------------|--------------|-------------------------------------|
| `device_id`    | UUID         | PK — client-generated device UUID   |
| `display_name` | VARCHAR(255) | NOT NULL — user-chosen display name |
| `avatar_url`   | VARCHAR(512) | NULLABLE                            |
| `updated_at`   | TIMESTAMPTZ  | Last update timestamp               |

**`trip`** — UUID v7 id, title, description, cover_image_key, status (`PLANNING`/`ACTIVE`/`ARCHIVED`),
created_by (device UUID), reference_currency, start_date, end_date, created_at, updated_at, deleted_at (soft delete).

**`trip_member`** — UUID v7 id, trip_id (FK), device_id, display_name (per-trip), role (`ORGANIZER`/`PARTICIPANT`),
joined_at, deleted_at.
Unique constraint on `(trip_id, device_id)`.

**`trip_invitation`** (Story 2.2) — UUID id, trip_id (FK), token (unique), created_by (device UUID), created_at.

### gRPC server (port 9081)

Implements `TripService` from `plantogether-proto` (`trip_service.proto`). Manual server setup via `GrpcServerConfig` (
SmartLifecycle) — no grpc-spring-boot-starter.

| Method                       | Description                                                                                   |
|------------------------------|-----------------------------------------------------------------------------------------------|
| `IsMember(tripId, deviceId)` | Returns `{is_member, role}`. Called by all other services before any trip-scoped operation.   |
| `GetTripMembers(tripId)`     | Returns `TripMemberProto[]` with device_id, role, display_name. Used by notification-service. |
| `GetTripCurrency(tripId)`    | Returns the trip's reference currency. Used by expense-service.                               |
| `GetTrip(tripId)`            | Returns trip_id, name, organizer_device_id, member_device_ids[].                              |

### REST API (`/api/v1/`)

| Method | Endpoint                                | Auth                                  |
|--------|-----------------------------------------|---------------------------------------|
| GET    | `/api/v1/users/me`                      | X-Device-Id                           |
| PUT    | `/api/v1/users/me`                      | X-Device-Id                           |
| POST   | `/api/v1/trips`                         | X-Device-Id                           |
| GET    | `/api/v1/trips`                         | X-Device-Id                           |
| GET    | `/api/v1/trips/{id}`                    | X-Device-Id + member                  |
| PUT    | `/api/v1/trips/{id}`                    | X-Device-Id + ORGANIZER               |
| DELETE | `/api/v1/trips/{id}`                    | X-Device-Id + ORGANIZER (soft delete) |
| POST   | `/api/v1/trips/{id}/invite`             | X-Device-Id + ORGANIZER               |
| POST   | `/api/v1/trips/{id}/join`               | X-Device-Id (token in body)           |
| GET    | `/api/v1/trips/{id}/members`            | X-Device-Id + member                  |
| DELETE | `/api/v1/trips/{id}/members/{deviceId}` | X-Device-Id + ORGANIZER               |

### RabbitMQ events

**Publishes** (exchange `plantogether.events`):

- `trip.created` — `TripCreatedEvent { tripId, name, organizerDeviceId, createdAt }`
- `trip.member.joined` — `MemberJoinedEvent { tripId, deviceId, joinedAt }`

**Consumes:**

- `poll.locked` — updates `trip.start_date` / `trip.end_date` from locked poll slot

### Environment variables

| Variable           | Default        | Description      |
|--------------------|----------------|------------------|
| `DB_HOST`          | `localhost`    | PostgreSQL host  |
| `DB_USER`          | `plantogether` | DB username      |
| `DB_PASSWORD`      | `plantogether` | DB password      |
| `RABBITMQ_HOST`    | `localhost`    | RabbitMQ host    |
| `REDIS_HOST`       | `localhost`    | Redis host       |
| `GRPC_PORT`        | `9081`         | gRPC server port |
| `MINIO_ENDPOINT`   | —              | MinIO endpoint   |
| `MINIO_ACCESS_KEY` | —              | MinIO access key |
| `MINIO_SECRET_KEY` | —              | MinIO secret key |
