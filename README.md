# Trip Service

> Service de gestion des voyages collaboratifs

## Rôle dans l'architecture

Le Trip Service est le cœur de PlanTogether. Il gère le cycle de vie complet des voyages (création, modification,
suppression), l'administration des membres du groupe de voyage et le système d'invitations. Ce service est le point
central d'accès pour toutes les données de voyage et coordonne les interactions avec les autres microservices.

## Fonctionnalités

- Création, lecture, mise à jour et suppression de voyages (CRUD)
- Gestion des états de voyage : PLANNING → ACTIVE → ARCHIVED
- Gestion des membres du groupe (organizer, participants)
- Système d'invitations avec tokens persistants
- Gestion des images de couverture stockées dans MinIO
- Authentification via tokens JWT Keycloak (Bearer)
- Publication d'événements vers RabbitMQ pour les autres services

## Endpoints REST

| Méthode | Endpoint                               | Description                                |
|---------|----------------------------------------|--------------------------------------------|
| POST    | `/api/trips`                           | Créer un nouveau voyage                    |
| GET     | `/api/trips`                           | Lister tous les voyages de l'utilisateur   |
| GET     | `/api/trips/{id}`                      | Récupérer les détails d'un voyage          |
| PUT     | `/api/trips/{id}`                      | Mettre à jour un voyage                    |
| DELETE  | `/api/trips/{id}`                      | Supprimer un voyage (organizer uniquement) |
| POST    | `/api/trips/{id}/invite`               | Générer et envoyer une invitation          |
| GET     | `/api/trips/{id}/members`              | Lister les membres du voyage               |
| DELETE  | `/api/trips/{id}/members/{keycloakId}` | Retirer un membre                          |
| PUT     | `/api/trips/{id}/status`               | Changer l'état du voyage                   |

## Modèle de données

**Trip**

- `id` (UUID) : identifiant unique
- `title` (String) : titre du voyage
- `description` (String, nullable) : description
- `cover_image_key` (String, nullable) : clé de l'image sur MinIO
- `status` (ENUM: PLANNING, ACTIVE, ARCHIVED) : état actuel
- `created_by` (UUID) : ID Keycloak du créateur
- `start_date` (LocalDate, nullable)
- `end_date` (LocalDate, nullable)
- `created_at` (Timestamp)
- `updated_at` (Timestamp)

**TripMember**

- `trip_id` (UUID, FK)
- `keycloak_id` (UUID) : ID Keycloak de l'utilisateur
- `role` (ENUM: ORGANIZER, PARTICIPANT) : rôle dans le voyage
- `joined_at` (Timestamp)

**TripInvitation**

- `id` (UUID)
- `trip_id` (UUID, FK)
- `token` (String, unique) : token d'invitation
- `created_by` (UUID)
- `created_at` (Timestamp)
- `expires_at` (Timestamp)
- `used_by` (Set<UUID>, nullable)

## Événements (RabbitMQ)

**Publie :**

- `TripCreated` — Émis lors de la création d'un nouveau voyage
- `TripUpdated` — Émis lors de la modification des données du voyage
- `TripStatusChanged` — Émis quand le statut change (PLANNING → ACTIVE, etc.)
- `MemberJoined` — Émis quand un nouveau membre accepte une invitation
- `MemberRemoved` — Émis quand un membre est retiré

**Consomme :** (aucun)

## Configuration

```yaml
server:
  port: 8081
  servlet:
    context-path: /
    
spring:
  application:
    name: plantogether-trip-service
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  datasource:
    url: jdbc:postgresql://postgres:5432/plantogether_trip
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}

keycloak:
  serverUrl: ${KEYCLOAK_SERVER_URL:http://keycloak:8080}
  realm: ${KEYCLOAK_REALM:plantogether}
  clientId: ${KEYCLOAK_CLIENT_ID}
  
minio:
  endpoint: ${MINIO_ENDPOINT:http://minio:9000}
  accessKey: ${MINIO_ACCESS_KEY}
  secretKey: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET:plantogether}
```

## Lancer en local

```bash
# Prérequis : Docker Compose (infra), Java 21+, Maven 3.9+

# Option 1 : Maven
mvn spring-boot:run

# Option 2 : Docker
docker build -t plantogether-trip-service .
docker run -p 8081:8081 \
  -e KEYCLOAK_SERVER_URL=http://host.docker.internal:8080 \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  plantogether-trip-service
```

## Dépendances

- **Keycloak 24+** : validation et récupération des tokens JWT OIDC
- **PostgreSQL 16** : persistance des voyages et membres
- **RabbitMQ** : publication d'événements pour les autres services
- **Redis** : cache sessions
- **MinIO** : stockage des images de couverture
- **Spring Boot 3.3.6** : framework web
- **Spring Cloud Netflix Eureka** : service discovery
- **Spring Security OAuth2** : authentification par tokens porteur

## Notes de sécurité

- Tous les endpoints requièrent un token Bearer valide de Keycloak
- Seul l'organisateur peut modifier ou supprimer un voyage
- Les UUIDs Keycloak sont les seules données utilisateur stockées (zéro PII)
- Les images de couverture sont chiffrées dans MinIO
