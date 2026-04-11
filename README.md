# Trip Service

> Collaborative trip management and user profile service

## Role in the Architecture

The Trip Service is the core of PlanTogether. It manages the complete lifecycle of trips (creation, modification,
archiving), member administration, and the invitation system. It is also the **sole owner of user profiles** in the
system: the `user_profile` table (display_name, avatar_url) is hosted exclusively in `db_trip`. Other microservices
only store opaque `device_id` references and call this service via gRPC to resolve profiles.

## Features

- Trip creation, read, update, and archiving (soft delete via `deleted_at`)
- Status management: PLANNING → ACTIVE → ARCHIVED
- Member management (ORGANIZER / PARTICIPANT)
- Invitation system with tokens (link or QR code)
- Sole owner of `user_profile` — lightweight profile keyed by device UUID
- Profile exposure via gRPC to all other services
- Zero PII: no email, no real name — only device UUIDs and free-form display names

## REST Endpoints

| Method | Endpoint                                | Description                                    |
|--------|-----------------------------------------|------------------------------------------------|
| GET    | `/api/v1/users/me`                      | Current user profile (from X-Device-Id header) |
| PUT    | `/api/v1/users/me`                      | Update current user profile                    |
| POST   | `/api/v1/trips`                         | Create a trip                                  |
| GET    | `/api/v1/trips`                         | My trips (paginated)                           |
| GET    | `/api/v1/trips/{id}`                    | Trip details                                   |
| PUT    | `/api/v1/trips/{id}`                    | Update a trip (ORGANIZER)                      |
| DELETE | `/api/v1/trips/{id}`                    | Archive a trip (soft delete, ORGANIZER)        |
| POST   | `/api/v1/trips/{id}/invite`             | Generate an invitation link                    |
| POST   | `/api/v1/trips/{id}/join`               | Join via token                                 |
| GET    | `/api/v1/trips/{id}/members`            | List members                                   |
| DELETE | `/api/v1/trips/{id}/members/{deviceId}` | Remove a member (ORGANIZER)                    |

## gRPC Server (port 9081)

The Trip Service exposes a gRPC server consumed by all other microservices:

| RPC                          | Description                                     |
|------------------------------|-------------------------------------------------|
| `IsMember(tripId, deviceId)` | Checks membership + returns role                |
| `GetTripMembers(tripId)`     | Full member profiles for the trip               |
| `GetTripCurrency(tripId)`    | Trip reference currency                         |
| `GetTrip(tripId)`            | Trip details (id, name, organizer, member list) |

## Data Model (`db_trip`)

**user_profile** — *lightweight profile, zero PII (no email, no real name)*

| Column         | Type                  | Description                  |
|----------------|-----------------------|------------------------------|
| `device_id`    | UUID PK               | Client-generated device UUID |
| `display_name` | VARCHAR(255) NOT NULL | User-chosen display name     |
| `avatar_url`   | VARCHAR(512) NULLABLE | Avatar URL                   |
| `updated_at`   | TIMESTAMP NOT NULL    | Last update                  |

**trip**

| Column            | Type                  | Description                  |
|-------------------|-----------------------|------------------------------|
| `id`              | UUID PK               | Unique identifier (UUID v7)  |
| `title`           | VARCHAR(255) NOT NULL | Trip name                    |
| `description`     | TEXT NULLABLE         | Free-form description        |
| `cover_image_key` | VARCHAR(500) NULLABLE | MinIO key for cover image    |
| `status`          | ENUM NOT NULL         | PLANNING / ACTIVE / ARCHIVED |
| `created_by`      | UUID NOT NULL         | device_id of the organizer   |
| `start_date`      | DATE NULLABLE         | Locked from Poll Service     |
| `end_date`        | DATE NULLABLE         | End date                     |
| `created_at`      | TIMESTAMP NOT NULL    |                              |
| `updated_at`      | TIMESTAMP NOT NULL    |                              |
| `deleted_at`      | TIMESTAMP NULLABLE    | Soft delete                  |

**trip_member**

| Column         | Type                  | Description               |
|----------------|-----------------------|---------------------------|
| `id`           | UUID PK               |                           |
| `trip_id`      | UUID NOT NULL FK→trip |                           |
| `device_id`    | UUID NOT NULL         | Device UUID of the member |
| `display_name` | VARCHAR(255) NOT NULL | Per-trip display name     |
| `role`         | ENUM NOT NULL         | ORGANIZER / PARTICIPANT   |
| `joined_at`    | TIMESTAMP NOT NULL    |                           |

Unique index: `(trip_id, device_id)`

## RabbitMQ Events (Exchange: `plantogether.events`)

**Publishes:**

| Routing Key          | Trigger                 |
|----------------------|-------------------------|
| `trip.created`       | Trip creation           |
| `trip.member.joined` | A member joins the trip |

**Consumes:**

| Routing Key   | Action                                        |
|---------------|-----------------------------------------------|
| `poll.locked` | Updates `start_date` / `end_date` of the trip |

## Configuration

```yaml
server:
  port: 8081

spring:
  application:
    name: plantogether-trip-service
  datasource:
    url: jdbc:postgresql://postgres:5432/db_trip
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate

grpc:
  server:
    port: 9081
```

## Running Locally

```bash
# Prerequisites: docker compose up -d
# + plantogether-proto and plantogether-common installed (mvn clean install)

mvn spring-boot:run
```

## Dependencies

- **PostgreSQL 16** (`db_trip`): trips, members, user profiles
- **RabbitMQ**: event publishing and consumption
- **Redis**: rate limiting (Bucket4j — 100 req/min/device, 10 trip creations/hour)
- **MinIO**: cover image storage (key only, no passthrough)
- **plantogether-proto**: gRPC contracts (server exposed on 9081)
- **plantogether-common**: event DTOs, DeviceIdFilter, SecurityAutoConfiguration, CorsConfig, rate limiting

## Security

- Anonymous device-based identity: `X-Device-Id` header on every request
- `DeviceIdFilter` (from plantogether-common, auto-configured via `SecurityAutoConfiguration`) extracts the device UUID
  and sets the SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- Only the ORGANIZER can modify, archive, or remove members
- Zero PII: no email, no real name — only device UUIDs and free-form display names
