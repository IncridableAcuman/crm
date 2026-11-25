# SAVDOCRM — Module Implementation Status

This document tracks the implementation status of all business and technical modules across milestones, clearly distinguishing **implemented behavior** from **planned behavior**.

| Module | Bounded Context | Target Milestone | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Foundation** | Core & Infrastructure | **Milestone 01** | **COMPLETED** | Monorepo structure, Spring Boot 4.2.0, Gradle Wrapper 9.9.1, Docker Compose PostgreSQL 16, Liquibase baseline, React 19 FSD, Health check. |
| **Tenancy** | `com.savdocrm.modules.tenancy` | **Milestone 02** | **COMPLETED** | Tenants, Branches, Multi-tenancy isolation (`TenantContextHolder`, `TenantExecutionTemplate`, `TenantContextTaskDecorator`, `TenantAwareRepository`, Composite FKs). Verified via PostgreSQL Testcontainers. |
| **Identity & Auth** | `com.savdocrm.modules.identity` | **Milestone 03** | **COMPLETED** | User entity, TenantMembership, RefreshToken, BCrypt password hashing, dual-phase JWT, token rotation, token reuse detection, Spring Security filter chain, live membership status check, React 19 auth FSD slice. |
| **RBAC** | `com.savdocrm.modules.identity` | **Milestone 04** | **COMPLETED** | Scoped permissions (`TENANT` & `BRANCH`), 33-permission catalog, 7 system roles, Grant-What-You-Hold, Last Active OWNER protection, role/assignment REST APIs, and M04.5 React RBAC UI (171 backend tests, 56 frontend tests). |
| **Catalog** | `com.savdocrm.modules.catalog` | Milestone 05 | PLANNED | Products, Matrix Variants, Units of Measure, Barcodes, Categories, Brands. |
| **Inventory** | `com.savdocrm.modules.inventory` | Milestone 06 | PLANNED | Materialized stock balances with optimistic locking (`@Version`), Immutable `inventory_transactions` ledger, Transfers. |
| **Sales & POS** | `com.savdocrm.modules.sales` | Milestone 07 | PLANNED | Cashier shifts (`PosSession`), atomic checkout transaction, Idempotency key pattern, split payments, returns. |
| **Purchases** | `com.savdocrm.modules.purchases` | Milestone 08 | PLANNED | Supplier purchase orders, Goods receipts, updating weighted average cost. |
| **Parties** | `com.savdocrm.modules.parties` | Milestone 07 & 08 | PLANNED | Customers, Customer credit limits & debts, Suppliers directory. |
| **Finance** | `com.savdocrm.modules.finance` | Milestone 09 | PLANNED | Financial accounts, Accounts Receivable (AR) ledger, Accounts Payable (AP) ledger, Cash registers, Expenses. |
| **Analytics** | `com.savdocrm.modules.analytics` | Milestone 10 | PLANNED | Sales turnover, COGS, Gross profit margin, low-stock warnings, executive dashboards. |
| **System** | `com.savdocrm.modules.system` | Milestone 10 | PLANNED | Audit logs, outbox events, system settings. |

---

## Detailed Milestone Status

### Milestone 01 — Project Foundation (Status: COMPLETED)
- **Implemented Behavior**:
  - Backend: Spring Boot 4.2.0 on Java 21 LTS, Gradle Wrapper 9.9.1, Liquibase baseline changelog (`system_metadata`), Actuator (`/actuator/health`), Springdoc OpenAPI 3.2.1 (`/swagger-ui/index.html`), System Health endpoint (`/api/v1/health`).
  - Frontend: React 19, TypeScript 5.7.2 (strict mode), Vite 6, Tailwind CSS 3.4.16, shadcn/ui, FSD structure (`app`, `pages`, `widgets`, `features`, `entities`, `shared`).
  - Infrastructure: Docker Compose PostgreSQL 16 container, `.env.example`, `.gitignore`.
  - Testing: Spring Boot context loading test (`SavdoCrmApplicationTests`), PostgreSQL 16 migration integration test (`PostgresLiquibaseMigrationIntegrationTest`).

### Milestone 02 — Multi-tenancy Foundation (Status: COMPLETED)
- **Implemented Behavior**:
  - `Tenant` entity with `ACTIVE`, `SUSPENDED`, `TRIAL` lifecycle, currency code, timezone, audit fields (`createdAt`, `updatedAt`), and optimistic locking (`@Version`).
  - `Branch` entity extending `BaseTenantEntity` with `(tenant_id, code)` unique constraint, address, phone, `isActive`, `isMain`, audit fields, and optimistic locking.
  - Fail-closed `TenantContextHolder` (ThreadLocal, throws `TenantContextMissingException` when context is absent).
  - `TenantExecutionTemplate` supporting supplier and runnable execution with guaranteed cleanup and nested context restoration.
  - `TenantContextTaskDecorator` for asynchronous task execution context propagation.
  - `TenantAwareRepository` contract providing `findByTenantIdAndId`, `findAllByTenantId`, `existsByTenantIdAndId`, `deleteByTenantIdAndId`, and contextual convenience methods.
  - Database-level isolation: Liquibase changesets `v1.0.0/01-create-tenants.xml` and `v1.0.0/02-create-branches.xml` with foreign key `fk_branches_tenant` on delete cascade, composite unique constraints `uq_branches_tenant_code` and `uq_branches_tenant_id`, and index `idx_branches_tenant`.
  - Automated tests: 17 total tests executed across unit and Testcontainers PostgreSQL 16 integration suites verifying fail-closed behavior, cross-tenant read/write isolation, and composite constraints.

### Milestone 03 — Authentication & Session Management (Status: COMPLETED & HARDENED)
- **Implemented Behavior**:
  - `User` global account entity (`users` table, unique case-insensitive email, unique username, BCrypt password hashing with work factor 12, optimistic locking).
  - `TenantMembership` entity (`tenant_memberships` table, unique composite `uq_memberships_tenant_user` and `uq_memberships_tenant_id`, statuses: `ACTIVE`, `INVITED`, `SUSPENDED`).
  - `RefreshToken` entity (`refresh_tokens` table, SHA-256 token hash storage, `token_family` tracking for compromise quarantine, revocation metadata, rotation lifecycle).
  - Strict Single-Use Rotation Protocol:
    - Each refresh token can be successfully consumed exactly once.
    - Revoking old token on rotation with `revoked_reason = 'Rotated'` and recording `successor_token_hash` for cryptographic audit lineage.
    - Zero grace window recovery: revoked refresh tokens are never accepted and never return their successor.
    - Automatic Compromise Quarantine: Any replay of a revoked token triggers immediate transactionally consistent invalidation of the entire `token_family` in PostgreSQL.
    - Lost response intentional behavior: retrying an old token after network loss revokes the family and requires re-login (security-first trade-off).
    - Database migration: Liquibase changeset `v1.0.0-07-remove-successor-token-encrypted` safely dropped obsolete `successor_token_encrypted` column while preserving `successor_token_hash`.
    - Retention & cleanup: Automated daily background job (`RefreshTokenCleanupTask` via `@EnableScheduling`, 3:00 AM daily, default 90-day retention cutoff) deletes expired revoked tokens, supported by composite index `idx_refresh_tokens_cleanup` on `(revoked, expires_at)`. Active tokens are strictly protected.
  - Token delivery architecture:
    - Access token: strictly in-memory (Zustand state); zero storage in `localStorage` or `sessionStorage`.
    - Refresh token: delivered via `HttpOnly`, `Secure` (prod), `SameSite=Lax` cookie (`savdocrm_refresh_token`) scoped to `/api/v1/auth`. Omitted from JSON response body.
  - Concurrency & Row-Level Locking: `RefreshTokenRepository.findByTokenHashWithLock` uses `PESSIMISTIC_WRITE` row locks (`SELECT ... FOR UPDATE`), serializing concurrent refresh attempts and guaranteeing transactional consistency (`noRollbackFor = BadCredentialsException.class`).
  - Frontend Cross-Tab Coordination:
    - Web Locks API (`savdocrm_refresh_lock`): Mutually exclusive refresh across all tabs. Crashed tabs automatically release browser locks.
    - BroadcastChannel (`savdocrm_auth_channel`): Propagates non-token `REFRESH_SUCCESS` and `AUTH_CLEARED` events. **Neither access tokens nor refresh tokens are EVER broadcast.**
    - Waiting Tab Synchronization: Waiting tabs acquire the Web Lock sequentially and perform their own HTTP refresh using the newly updated HttpOnly cookie, safely acquiring valid in-memory access tokens without sending consumed tokens.
    - Missed broadcast protection & storage events: Non-token session metadata in `localStorage` (`savdocrm_session_state`) prevents using stale in-memory tokens if a background tab missed a broadcast; `window.addEventListener('storage')` immediately syncs session termination across all tabs.
    - Synchronized Login/Logout Lifecycle: `authApi.login` and `authApi.register` restore `AUTHENTICATED` state, while `authApi.logout` atomically purges in-memory auth, clears React Query cache, sets `UNAUTHENTICATED`, and broadcasts `AUTH_CLEARED`.
    - Fallback: in-tab single-flight queue for environments without Web Locks API.
  - Trusted Reverse Proxy Resolution: `ClientIpResolver` ensures forwarded IP headers (`X-Forwarded-For`) are trusted only when direct connections originate from configured reverse proxies (`security.trusted-proxies`).
  - Dual-phase authentication flow:
    1. Global login (`POST /api/v1/auth/login`) -> verifies credentials, returns global access token + sets HttpOnly refresh cookie + returns available tenants.
    2. Tenant selection (`POST /api/v1/auth/select-tenant`) -> validates active membership and active store status, returns tenant-scoped access token + sets updated HttpOnly refresh cookie.
  - Live account verification: `JwtAuthenticationFilter` validates user `ACTIVE` status, tenant `ACTIVE` status, membership `ACTIVE` status, and membership ownership/claim consistency on every request.
  - Tenant context lifecycle: `TenantContextHolder` bound only after all live verifications succeed, guaranteed cleanup in `try ... finally { clear(); }`.
  - Global vs Tenant-Scoped separation: `BranchController` (`/api/v1/branches`) fails closed with `403 Forbidden` (`Tenant Context Missing`) when accessed with a Global Login Token. `X-Tenant-Id` header is never trusted.
  - Tenant switching cache isolation: `queryClient.cancelQueries()` and `queryClient.clear()` purge all cached query data from the old store, preventing stale data leakage across tenants.
  - Registration (`POST /api/v1/auth/register`) with atomic transaction boundaries and optional initial store setup.
  - Logout (`POST /api/v1/auth/logout`) with full session family revocation and cookie clearing (`Max-Age=0`).
  - CSRF Defense: Exact Origin allowlist with no wildcard origins, rejection of `Origin: null`, Referer fallback, and rejection of cookie requests missing both headers.
  - Current identity profile endpoint (`GET /api/v1/auth/me`).
  - Spring Security stateless filter chain (`SecurityConfig`, `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`, CORS configuration).
  - ArchUnit architecture enforcement: verifies controllers do not directly call repositories and domain does not depend on web.
  - Frontend FSD auth slice: In-memory Zustand store, TanStack Query for user profile and available tenants, Axios request/response interceptors with concurrent 401 refresh queue, `LoginPage`, `RegisterPage`, `TenantSelectPage`, `ProtectedRoute` route guards.
  - Automated test suite: 59 backend tests (PostgreSQL 16 Testcontainers) + 20 frontend Vitest tests, all passing with 100% success rate.
