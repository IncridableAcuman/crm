# SAVDOCRM — Production Store Management & POS Platform

SAVDOCRM is a high-performance, multi-tenant retail store management and Point of Sale (POS) CRM platform built for small and medium retail businesses (grocery stores, apparel, electronics, and household goods).

---

## Architecture & Technology Stack

### Backend
- **Framework**: Spring Boot 4.2.0 (Spring Framework 7.1)
- **Runtime**: Java 21 LTS with Virtual Threads enabled
- **Build Tool**: Gradle Wrapper 9.9.1
- **Persistence**: Spring Data JPA, PostgreSQL 16+, Liquibase migrations
- **Security**: Spring Security, Stateless Dual-Phase JWT (JJWT 0.12.6), BCrypt (cost 12), Refresh Token Rotation (RTR)
- **API Documentation**: Springdoc OpenAPI 3.2.1 (`/swagger-ui/index.html`, `/v3/api-docs`)
- **Testing**: JUnit 5, Mockito, Testcontainers PostgreSQL 16, ArchUnit 1.4.0

### Frontend
- **Framework**: React 19 + TypeScript 5.7.2 (strict mode)
- **Bundler & Dev Server**: Vite 6
- **Architecture**: Feature-Sliced Design (FSD)
- **Styling & UI**: Tailwind CSS 3.4.16, shadcn/ui primitives, Lucide React icons
- **State Management**: TanStack Query v5, Zustand v5 with local storage persistence
- **HTTP Client**: Axios with concurrent 401 token refresh queue
- **Form & Validation**: React Hook Form with Zod schemas
- **Testing**: Vitest with Testing Library

---

## Milestones Status

| Milestone | Scope | Status | Test Coverage |
| :--- | :--- | :--- | :--- |
| **Milestone 01** | Project Foundation & Tooling | **COMPLETED** | Verified |
| **Milestone 02** | Multi-Tenancy Foundation & Isolation | **COMPLETED** | Verified (17 tests) |
| **Milestone 03** | Authentication & Session Management | **COMPLETED** | Verified (32 backend + 6 frontend tests) |
| **Milestone 04** | Role-Based Access Control (RBAC) & Permissions | *PLANNED* | - |
| **Milestone 05** | Product Catalog & Matrix Variants | *PLANNED* | - |
| **Milestone 06** | Inventory Management & Ledger Movements | *PLANNED* | - |
| **Milestone 07** | Sales & POS Transactions | *PLANNED* | - |

---

## Authentication & Dual-Phase Flow (Milestone 03)

SAVDOCRM uses a zero-trust multi-tenancy model where client `X-Tenant-Id` headers are strictly ignored:
1. **Global Login** (`POST /api/v1/auth/login`): Verifies credentials, returns a global access token and a list of stores the user belongs to.
2. **Tenant Selection** (`POST /api/v1/auth/select-tenant`): Verifies active store membership and returns a tenant-scoped access token containing `tenantId` and `membershipId`.
3. **Request Filtering** (`JwtAuthenticationFilter`): Resolves tenant from the verified JWT claims, verifies live store status, sets `TenantContextHolder` in a fail-closed pattern, and guarantees ThreadLocal cleanup in `finally`.
4. **Refresh Token Rotation & Reuse Detection**: Refresh tokens are stored as SHA-256 hashes within session families. Presenting an already-revoked refresh token triggers automated compromise quarantine, revoking the entire token family.

---

## Getting Started

### Prerequisites
- Java 21 LTS
- Docker and Docker Compose
- Node.js 20+ and npm

### 1. Start PostgreSQL
```bash
docker compose -f docker/docker-compose.yml up -d
```
The database starts on port `5434` with user `savdocrm_user` and database `savdocrm`.

### 2. Run Backend
```bash
cd backend
./gradlew bootRun
```
Backend API runs on `http://localhost:8080`.
Swagger UI is accessible at `http://localhost:8080/swagger-ui/index.html`.

### 3. Run Backend Tests
```bash
cd backend
./gradlew clean test
```
Executes all 32 automated tests, including PostgreSQL 16 Testcontainers integration suites and ArchUnit structural rules.

### 4. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
Frontend runs on `http://localhost:3000` (proxies `/api` to port 8080).

### 5. Run Frontend Tests & Build
```bash
cd frontend
npm test
npm run build
```

---

## Documentation
- [System Architecture](file:///home/izzatbek/Desktop/crm/docs/architecture/system-overview.md)
- [Multi-Tenancy Foundation](file:///home/izzatbek/Desktop/crm/docs/architecture/multi-tenancy.md)
- [Authentication & Session Management](file:///home/izzatbek/Desktop/crm/docs/architecture/03-authentication-and-session.md)
- [Authentication API Specification](file:///home/izzatbek/Desktop/crm/docs/api/authentication-api.md)
- [Architecture Decision Records](file:///home/izzatbek/Desktop/crm/docs/architecture/architecture-decisions.md)
- [Module Status Matrix](file:///home/izzatbek/Desktop/crm/docs/modules/module-status.md)
