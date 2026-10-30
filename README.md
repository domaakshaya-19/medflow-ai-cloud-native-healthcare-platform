# MedFlow AI

MedFlow AI is an academic service-oriented healthcare platform. This repository currently implements only the Day-1 foundation: discovery, gateway routing, secure patient identity, and a protected React dashboard. Clinical services and AI are deliberately not implemented yet.

## Implemented architecture

```text
React/Vite :5173 -> Spring Cloud Gateway :8080 -> Auth Service :8081 -> PostgreSQL :5432
                              |                    |
                              +---------- Eureka :8761
```

The Maven reactor contains `discovery-server`, `api-gateway`, and `auth-service`. Auth Service alone owns `medflow_auth`.

## Prerequisites

- Java 21 and Maven 3.9+
- Node.js 22+ and npm
- PostgreSQL 16+, or Docker Desktop with Compose

## Environment

In PowerShell, copy the placeholder file and replace the password and JWT secret locally:

```powershell
Copy-Item .env.example .env
# Generate a secret (then place the printed value in .env):
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

`.env` is ignored. Never commit its contents. Spring reads process environment variables; for manual startup, load/set the values in the current shell or your IDE launch configuration.

## Local PostgreSQL

```sql
CREATE USER medflow_auth WITH PASSWORD 'your-local-password';
CREATE DATABASE medflow_auth OWNER medflow_auth;
```

Then set `AUTH_DB_URL`, `AUTH_DB_USER`, `AUTH_DB_PASSWORD`, and `JWT_SECRET` in the environment.

## Build and test

```powershell
mvn clean verify
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
```

If this Windows machine maps Java's home to an unwritable location, use a workspace-local Maven cache:

```powershell
$env:MAVEN_OPTS="-Duser.home=$PWD"
mvn clean verify
```

## Local startup order

For Windows Patient Dashboard connectivity or port 8082 conflicts, use the
[local discovery troubleshooting procedure](docs/troubleshooting/patient-dashboard-local.md).
It includes the opt-in `local` profile, safe process checks, startup order, and
direct-versus-Gateway authentication checks. The profile is for one host only.

Open separate PowerShell terminals from the repository root after setting environment variables:

```powershell
mvn -pl backend/discovery-server spring-boot:run
mvn -pl backend/auth-service spring-boot:run
mvn -pl backend/api-gateway spring-boot:run
npm.cmd --prefix frontend run dev
```

Register a synthetic patient, sign in, and open the protected dashboard. The client restores an existing JWT-backed session with `/api/auth/me` and clears invalid sessions on startup.

## Docker

Backend Dockerfiles use multi-stage Maven builds, so Compose builds directly from the clean source tree:

```powershell
docker compose config
docker compose up --build
```

Compose reads `.env`, starts PostgreSQL and the three backend services with health-gated dependencies, then serves the frontend through nginx.

## Ports and URLs

| Component | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API Gateway | http://localhost:8080 |
| Auth Service (direct development access) | http://localhost:8081 |
| Eureka | http://localhost:8761 |
| Gateway health | http://localhost:8080/actuator/health |

## API

| Method | Endpoint | Authentication | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create PATIENT account |
| POST | `/api/auth/login` | Public | Return JWT and identity |
| GET | `/api/auth/me` | Bearer JWT | Return current identity |

## Security and schema

`user_accounts` stores UUID `id`, normalized unique `email`, `full_name`, BCrypt `password_hash`, enum `role`, `enabled`, and UTC audit timestamps. Public registration accepts no role and always assigns `PATIENT`. JWT signing material and database credentials are environment-only. The API returns DTOs and consistent errors, never entities, hashes, secrets, or stack traces.

## Current limitations

- Staff provisioning and all clinical services are intentionally absent.
- The Day-1 schema uses Hibernate update for local academic development; introduce versioned migrations before production deployment.
- JWT logout is client-side token removal; revocation/refresh tokens are a later security concern.
- Gemini, Kubernetes, cloud deployment, and monitoring are not implemented.

### Not yet implemented

- Patient, Doctor, Appointment, Medical Records, Laboratory, Pharmacy, Billing, Emergency, Notification, and Nutrition services
- AI Service and Gemini integration
- Kubernetes
- Monitoring
- DevSecOps security scans

More detail is in [architecture](docs/architecture/day-1.md), [API](docs/api/auth-api.md), [security](docs/security/day-1-security.md), and [testing](docs/testing/day-1-testing.md).

## Next stage

After Day 1 is verified end-to-end, Day 2 should implement Patient Service, Doctor Service, and role-based dashboards.
