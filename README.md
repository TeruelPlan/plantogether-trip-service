# Trip Service

> Service de gestion des voyages collaboratifs et des profils utilisateurs

## Rôle dans l'architecture

Le Trip Service est le cœur de PlanTogether. Il gère le cycle de vie complet des voyages (création, modification,
archivage), l'administration des membres et le système d'invitations. Il est également l'**unique propriétaire des
profils utilisateurs** dans le système : la table `user_profile` (display_name, avatar_url, email) est hébergée
exclusivement dans `db_trip`. Les autres microservices ne stockent que des `keycloak_id` opaques et appellent ce
service via gRPC pour résoudre les profils.

## Fonctionnalités

- Création, lecture, modification et archivage de voyages (soft delete via `deleted_at`)
- Gestion des états : PLANNING → ACTIVE → ARCHIVED
- Gestion des membres (ORGANIZER / PARTICIPANT)
- Système d'invitations avec tokens (lien ou QR code)
- Propriétaire unique de `user_profile` — synchronisation lazy depuis les claims JWT
- Exposition des profils via gRPC à tous les autres services
- Anonymisation RGPD sur événement `user.deleted`

## Endpoints REST

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/v1/users/me` | Profil de l'utilisateur courant (claims JWT) |
| GET | `/api/v1/users/batch?ids=uuid1,uuid2` | Profils par IDs (batch) |
| GET | `/api/v1/users/search?q=term` | Recherche (Keycloak Admin API) |
| POST | `/api/v1/trips` | Créer un voyage |
| GET | `/api/v1/trips` | Mes voyages (paginé) |
| GET | `/api/v1/trips/{id}` | Détail d'un voyage |
| PUT | `/api/v1/trips/{id}` | Modifier un voyage (ORGANIZER) |
| DELETE | `/api/v1/trips/{id}` | Archiver un voyage (soft delete, ORGANIZER) |
| POST | `/api/v1/trips/{id}/invite` | Générer un lien d'invitation |
| POST | `/api/v1/trips/{id}/join` | Rejoindre via token |
| GET | `/api/v1/trips/{id}/members` | Liste des membres |
| DELETE | `/api/v1/trips/{id}/members/{uid}` | Retirer un membre (ORGANIZER) |

## gRPC Server (port 9081)

Le Trip Service expose un serveur gRPC consommé par tous les autres microservices :

| RPC | Description |
|-----|-------------|
| `CheckMembership(tripId, userId)` | Vérifie l'appartenance + retourne le rôle |
| `GetTripMembers(tripId)` | Profils complets des membres du trip |
| `GetUserProfiles(keycloakIds[])` | Résolution batch de profils par IDs |
| `GetTripCurrency(tripId)` | Devise de référence du trip |

## Modèle de données (`db_trip`)

**user_profile** — *seule copie des PII dans tout le système*

| Colonne | Type | Description |
|---------|------|-------------|
| `keycloak_id` | UUID PK | Identifiant Keycloak (claim `sub`) |
| `display_name` | VARCHAR(255) NOT NULL | Nom affiché |
| `avatar_url` | VARCHAR(512) NULLABLE | URL de l'avatar |
| `email` | VARCHAR(320) NOT NULL | Utilisé par Notification Service |
| `updated_at` | TIMESTAMP NOT NULL | Dernière synchronisation |

**trip**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | Identifiant unique (UUID v7) |
| `title` | VARCHAR(255) NOT NULL | Nom du voyage |
| `description` | TEXT NULLABLE | Description libre |
| `cover_image_key` | VARCHAR(500) NULLABLE | Clé MinIO de l'image de couverture |
| `status` | ENUM NOT NULL | PLANNING / ACTIVE / ARCHIVED |
| `created_by` | UUID NOT NULL | keycloak_id de l'organisateur |
| `start_date` | DATE NULLABLE | Lockée depuis Poll Service |
| `end_date` | DATE NULLABLE | Date de fin |
| `created_at` | TIMESTAMP NOT NULL | |
| `updated_at` | TIMESTAMP NOT NULL | |
| `deleted_at` | TIMESTAMP NULLABLE | Soft delete |

**trip_member**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | |
| `trip_id` | UUID NOT NULL FK→trip | |
| `keycloak_id` | UUID NOT NULL | UUID Keycloak du membre |
| `role` | ENUM NOT NULL | ORGANIZER / PARTICIPANT |
| `joined_at` | TIMESTAMP NOT NULL | |

Index unique : `(trip_id, keycloak_id)`

## Événements RabbitMQ (Exchange : `plantogether.events`)

**Publie :**

| Routing Key | Déclencheur |
|-------------|-------------|
| `trip.created` | Création d'un voyage |
| `trip.member.joined` | Un membre rejoint le voyage |

**Consomme :**

| Routing Key | Action |
|-------------|--------|
| `poll.locked` | Met à jour `start_date` / `end_date` du trip |
| `user.profile.updated` | Synchronise `user_profile` (Keycloak SPI) |
| `user.deleted` | Anonymise `user_profile` : display_name → « Utilisateur supprimé », email → NULL, avatar_url → NULL |

## Synchronisation des profils

- **Création** : à l'entrée dans un trip, les claims JWT (`sub`, `preferred_username`, `email`, `picture`) alimentent `user_profile`
- **Mise à jour lazy** : à chaque appel API, le `display_name` du JWT est comparé avec la copie locale et mis à jour si différent
- **Mise à jour asynchrone** : événement RabbitMQ `user.profile.updated` publié par le Keycloak SPI

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

## Lancer en local

```bash
# Prérequis : docker compose --profile essential up -d
# + plantogether-proto et plantogether-common installés (mvn clean install)

mvn spring-boot:run
```

## Dépendances

- **Keycloak 24+** : validation JWT, Admin API (recherche/résolution utilisateurs)
- **PostgreSQL 16** (`db_trip`) : voyages, membres, profils utilisateurs
- **RabbitMQ** : publication et consommation d'événements métier
- **Redis** : rate limiting (Bucket4j — 100 req/min/user, 10 créations trip/heure)
- **plantogether-proto** : contrats gRPC (serveur exposé sur 9081)
- **plantogether-common** : DTOs events, CorsConfig, sécurité partagée

## Sécurité

- Tous les endpoints requièrent un token Bearer Keycloak valide
- Seul l'ORGANIZER peut modifier, archiver ou retirer des membres
- Zero PII en dehors de `user_profile` — les autres services ne stockent que des `keycloak_id`
- Anonymisation RGPD automatique sur suppression de compte Keycloak
