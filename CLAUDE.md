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
  -e KEYCLOAK_URL=http://host.docker.internal:8180 \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  plantogether-trip-service
```

**Prerequisites:** install shared libs first:
```bash
cd ../plantogether-proto && mvn clean install
cd ../plantogether-common && mvn clean install
```

## Architecture

Spring Boot 3.3.6 microservice (Java 25). Manages collaborative trips, members, invitations, and user profiles.

**Ports:** REST `8081` · gRPC `9081`

**Package:** `com.plantogether.trip`

### Package structure

```
com.plantogether.trip/
├── config/          # SecurityConfig, RabbitConfig, GrpcServerConfig
├── controller/      # REST controllers
├── domain/          # JPA entities (Trip, TripMember, TripInvitation, UserProfile)
├── repository/      # Spring Data JPA
├── service/         # Business logic
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   ├── server/      # TripGrpcServiceImpl (exposes gRPC on port 9081)
│   └── client/      # (none — trip-service has no outgoing gRPC calls)
└── event/
    ├── publisher/   # RabbitMQ publishers
    └── listener/    # RabbitMQ consumers (poll.locked, user.profile.updated, user.deleted)
```

### Infrastructure dependencies

| Dependency | Default (local) | Purpose |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/plantogether_trip` | Primary persistence |
| RabbitMQ | `localhost:5672` | Event publishing + consuming |
| Redis | `localhost:6379` | Rate limiting (Bucket4j) |
| Keycloak 24+ | `localhost:8180` realm `plantogether` | JWT validation via JWKS |
| MinIO | `localhost:9000` | Cover image storage (key only, no passthrough) |


### Domain model (db_trip)

**`user_profile`** — only table in the platform that stores PII (zero PII principle: all other services store Keycloak UUIDs only):

| Column | Type | Notes |
|---|---|---|
| keycloak_id | UUID | PK — Keycloak `sub` claim |
| display_name | VARCHAR(255) | NOT NULL — from `preferred_username` or `name` |
| avatar_url | VARCHAR(512) | NULLABLE — from `picture` claim |
| email | VARCHAR(320) | NOT NULL — used by notification-service |
| updated_at | TIMESTAMP | Last sync from Keycloak |

RGPD: on `user.deleted` event → `display_name = 'Utilisateur supprimé'`, `email = NULL`, `avatar_url = NULL`. `keycloak_id` retained for referential integrity.

**`trip`** — UUID id, title, description, cover_image_key (MinIO key), status (`PLANNING`/`ACTIVE`/`ARCHIVED`),
created_by (Keycloak UUID), start_date, end_date, created_at, updated_at, deleted_at (soft delete).

**`trip_member`** — UUID id, trip_id (FK), keycloak_id, role (`ORGANIZER`/`PARTICIPANT`), joined_at.
Unique index on (trip_id, keycloak_id).

**`trip_invitation`** — UUID id, trip_id (FK), token (unique), created_by, expires_at, used_by (Set\<UUID\>).

### gRPC server (port 9081)

Implements `TripGrpcService` (defined in `plantogether-proto`):

| Method | Description |
|---|---|
| `CheckMembership(tripId, userId)` | Returns `{is_member, role}`. Called by all other services before any write operation. |
| `GetTripMembers(tripId)` | Returns full `MemberProfile[]` with display_name, avatar_url, email. Used by notification-service. |
| `GetUserProfiles(keycloakIds[])` | Bulk profile lookup. Used for enriching responses. |
| `GetTripCurrency(tripId)` | Returns the trip's reference currency. Used by expense-service. |

### REST API (`/api/v1/`)

| Method | Endpoint | Auth |
|---|---|---|
| GET | `/api/v1/users/me` | Bearer JWT |
| GET | `/api/v1/users/batch?ids=uuid1,uuid2` | Bearer JWT |
| GET | `/api/v1/users/search?q=term` | Bearer JWT |
| POST | `/api/v1/trips` | Bearer JWT |
| GET | `/api/v1/trips` | Bearer JWT |
| GET | `/api/v1/trips/{id}` | Bearer JWT + member |
| PUT | `/api/v1/trips/{id}` | Bearer JWT + ORGANIZER |
| DELETE | `/api/v1/trips/{id}` | Bearer JWT + ORGANIZER (soft delete) |
| POST | `/api/v1/trips/{id}/invite` | Bearer JWT + ORGANIZER |
| POST | `/api/v1/trips/{id}/join` | Bearer JWT (token in body) |
| GET | `/api/v1/trips/{id}/members` | Bearer JWT + member |
| DELETE | `/api/v1/trips/{id}/members/{uid}` | Bearer JWT + ORGANIZER |

### RabbitMQ events

**Publishes** (exchange `plantogether.events`):
- `trip.created` — on trip creation
- `trip.member.joined` — on member joining

**Consumes:**
- `poll.locked` — updates `trip.start_date` / `trip.end_date` from locked poll slot
- `user.profile.updated` — syncs user_profile table from Keycloak SPI event
- `user.deleted` — anonymises user_profile row (RGPD)

### Security model

- Stateless JWT via `KeycloakJwtConverter` — extracts `realm_access.roles` → `ROLE_<ROLE>` Spring authorities
- Principal name = Keycloak subject UUID
- Public: `/actuator/health`, `/actuator/info`
- ORGANIZER role required for trip modifications and member removal
- Zero PII in downstream services — only Keycloak UUIDs stored elsewhere

### UserProfile synchronisation

- **On join:** Trip Service extracts claims from JWT (`sub`, `preferred_username`, `email`, `picture`) and upserts `user_profile`
- **Lazy update:** on each authenticated call, if `display_name` in JWT differs from stored value, update silently
- **Async:** Keycloak SPI publishes `user.profile.updated` → consumed by Trip Service

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_USER` | `plantogether` | DB username |
| `DB_PASSWORD` | `plantogether` | DB password |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `REDIS_HOST` | `localhost` | Redis host |
| `KEYCLOAK_URL` | `http://localhost:8180` | Keycloak base URL |
| `MINIO_ENDPOINT` | — | MinIO endpoint |
| `MINIO_ACCESS_KEY` | — | MinIO access key |
| `MINIO_SECRET_KEY` | — | MinIO secret key |

