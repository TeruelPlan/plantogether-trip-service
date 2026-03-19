# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the application
mvn spring-boot:run

# Build JAR
mvn clean package

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Build without tests
mvn clean package -DskipTests

# Docker build & run
docker build -t plantogether-trip-service .
docker run -p 8081:8081 \
  -e KEYCLOAK_SERVER_URL=http://host.docker.internal:8080 \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  plantogether-trip-service
```

## Architecture

This is a Spring Boot 3.3.6 microservice (Java 21) within the PlanTogether platform. It manages collaborative trips,
their members, and invitations.

**Package structure** (`com.plantogether.trip`):

- `security/` — OAuth2/JWT config via Keycloak; `KeycloakJwtConverter` extracts `realm_access` roles into Spring
  Security `ROLE_` authorities
- `exception/` — `GlobalExceptionHandler` returns standardized `ErrorResponse` (timestamp, status, error, message, path)
  for 400/403/404
- `controller/`, `service/`, `model/`, `repository/`, `dto/`, `config/` — stub packages awaiting implementation

**Infrastructure dependencies:**

- **PostgreSQL 16** — `plantogether_trip` DB; schema managed by Flyway (`src/main/resources/db/migration/`); JPA DDL set
  to `validate`
- **Keycloak** — JWT validation via `${KEYCLOAK_URL}/realms/plantogether/protocol/openid-connect/certs`
- **RabbitMQ** — event publishing (TripCreated, TripUpdated, TripStatusChanged, MemberJoined, MemberRemoved); consumes
  nothing
- **Redis** — session cache
- **MinIO** — cover image storage (bucket key stored in Trip entity, not the binary)
- **Eureka** — service discovery at `${EUREKA_URL}`

**Security model:**

- All endpoints require a valid Keycloak Bearer JWT except `/actuator/health` and `/actuator/info`
- Only Keycloak UUIDs are stored (no PII)
- Only the organizer role can modify or delete a trip

**Domain model (to be implemented):**

- `Trip` — UUID id, title, description, cover_image_key, status (PLANNING/ACTIVE/ARCHIVED), created_by (Keycloak UUID),
  start_date, end_date
- `TripMember` — composite key (trip_id, keycloak_id), role (ORGANIZER/PARTICIPANT), joined_at
- `TripInvitation` — UUID id, trip_id, token (unique), created_by, expires_at, used_by (Set<UUID>)

**Planned REST API** (`/api/trips`):

- CRUD on trips + status transitions (PLANNING → ACTIVE → ARCHIVED)
- `POST /{id}/invite` — generate invitation token
- `GET/DELETE /{id}/members` — member management

**Shared dependency:** `com.plantogether:plantogether-common:1.0.0-SNAPSHOT` for shared exceptions, DTOs, and utilities.
