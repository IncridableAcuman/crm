# SAVDOCRM — Developer Guide

This guide covers local development setup, prerequisites, execution commands, and testing workflows for **SAVDOCRM**.

---

## 1. Prerequisites

- **Java**: OpenJDK 21 LTS (`java -version`)
- **Node.js**: Node.js 20+ / 22+ (`node -v`)
- **Docker**: Docker Engine & Docker Compose (`docker compose version`)
- **Gradle**: Managed via included Gradle Wrapper 9.8.2 (`./gradlew`)

---

## 2. Infrastructure Setup (Local PostgreSQL)

The project includes a `docker-compose.yml` preconfigured for PostgreSQL 16:

```bash
# Start PostgreSQL container in background
docker compose -f docker/docker-compose.yml up -d

# Verify container health
docker compose -f docker/docker-compose.yml ps
```

Configuration defaults are specified in [`.env.example`](file:///home/izzatbek/Desktop/crm/.env.example). Copy to `.env` if custom credentials are required.

---

## 3. Backend Development (Spring Boot 4.2.0)

The backend is located in [`backend/`](file:///home/izzatbek/Desktop/crm/backend).

```bash
cd backend

# Execute clean tests
./gradlew clean test

# Build production executable bootJar
./gradlew build

# Run application locally with active dev profile
./gradlew bootRun
```

### Key URLs:
- **Server Root**: `http://localhost:8080`
- **Health Check**: `http://localhost:8080/api/v1/health`
- **Actuator Health**: `http://localhost:8080/actuator/health`
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI v3 Docs**: `http://localhost:8080/v3/api-docs`

---

## 4. Frontend Development (React 19 / FSD)

The frontend is located in [`frontend/`](file:///home/izzatbek/Desktop/crm/frontend).

```bash
cd frontend

# Install dependencies (already executed during setup)
npm install

# Start Vite dev server with proxy to backend
npm run dev

# Run TypeScript check and production build
npm run build
```

The frontend dev server runs at `http://localhost:3000` and automatically proxies `/api/*` requests to `http://localhost:8080`.

---

## 5. Liquibase Database Migrations

Database migrations are tracked in [`backend/src/main/resources/db/changelog/`](file:///home/izzatbek/Desktop/crm/backend/src/main/resources/db/changelog).
- Master changelog: `db.changelog-master.xml`
- Migrations execute automatically on application startup.
