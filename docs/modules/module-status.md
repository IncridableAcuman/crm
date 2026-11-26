# SAVDOCRM — Module Implementation Status

This document tracks the implementation status of all business and technical modules across milestones, clearly distinguishing **implemented behavior** from **planned behavior**.

| Module | Bounded Context | Target Milestone | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Foundation** | Core & Infrastructure | **Milestone 01** | **COMPLETED** | Monorepo structure, Spring Boot 4.2.0, Gradle Wrapper 9.9.1, Docker Compose PostgreSQL 16, Liquibase baseline, React 19 FSD, Health check. |
| **Tenancy** | `com.savdocrm.modules.tenancy` | **Milestone 02** | **COMPLETED** | Tenants, Branches, Multi-tenancy isolation (`TenantContextHolder`, `TenantExecutionTemplate`, `TenantContextTaskDecorator`, `TenantAwareRepository`, Composite FKs). Verified via PostgreSQL Testcontainers. |
| **Identity & Auth** | `com.savdocrm.modules.identity` | **Milestone 03** | **COMPLETED** | User entity, TenantMembership, RefreshToken, BCrypt password hashing, dual-phase JWT, token rotation, token reuse detection, Spring Security filter chain, live membership status check, React 19 auth FSD slice. |
| **RBAC** | `com.savdocrm.modules.identity` | **Milestone 04** | **COMPLETED** | Scoped permissions (`TENANT` & `BRANCH`), 33-permission catalog, 7 system roles, Grant-What-You-Hold, Last Active OWNER protection, role/assignment REST APIs, and M04.5 React RBAC UI (171 backend tests, 56 frontend tests). |
| **Catalog** | `com.savdocrm.modules.catalog` | Milestone 05 | **IN PROGRESS** | Core Product Catalog implemented (`M05.1–M05.5` COMPLETED: schema, domain, facade, REST API/RBAC, React FSD UI); `M05.6` integration, security & quality audit PLANNED. Products, Matrix Variants, Units of Measure, Barcodes, Hierarchical Categories, optional `brand` attribute. |
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

### Milestone 04 — Scoped RBAC & Permission Management (Status: COMPLETED)
- **Implemented Behavior**:
  - Domain & Schema (`M04.1–M04.2`): Liquibase migrations `v1.0.0/09`–`v1.0.0/15` for `permissions` (33 canonical `<module>.<resource>.<action>` capabilities), `roles` (7 immutable tenant-scoped system roles + custom roles), `role_permissions`, and `user_role_assignments` with composite FKs and `ON DELETE RESTRICT` audit preservation.
  - Authorization Engine (`M04.3`): `PermissionAuthorizationService` (`TENANT` and `BRANCH` scopes, scope inheritance, live user/tenant/membership/branch/role checks, fail-closed evaluation), `RequestScopedPermissionMemoizer`, 6-predicate Last Active `OWNER` protection under pessimistic tenant row lock, deterministic `ORDER BY tenant_id ASC` lock ordering on suspension, atomic `TenantBootstrapService`, and `RoleAssignmentService` (Grant-What-You-Hold, Owner Guard, self-elevation prevention).
  - REST API & Method Security (`M04.4`): Role, permission, and role-assignment endpoints with `@PreAuthorize` enforcement and RFC 7807 `ProblemDetail` error responses.
  - Frontend RBAC UI (`M04.5`): Dynamic 33-permission catalog integration, read-only system roles, custom role lifecycle, fail-closed `PermissionGuard` / `PermissionRoute`, tenant-scoped query cache isolation, and `TENANT`/`BRANCH` assignment scope UI.
- **Verification**: 171 backend tests (including Testcontainers PostgreSQL 16 concurrency & security integration tests) and 56 frontend Vitest tests passing upon M04.5 completion.
- **Known Limitations**: `UserRoleAssignmentDto` exposes `membershipId` only; member directory search endpoint is not yet implemented in M04.

### Milestone 05 — Product Catalog & Category Management (Status: IN PROGRESS)
> **Scope Distinction**: Core Product Catalog implementation (**M05.1–M05.5**) is **COMPLETED** and verified. Entire M05 roadmap completion awaits **M05.6 — Integration, Security & Quality Audit** (**PLANNED**). **M06 Inventory** has **not started**.

| Sub-milestone | Scope | Status | Verification |
| :--- | :--- | :--- | :--- |
| **M05.1** | Architecture & Design | **COMPLETED** | Approved specs ([05-product-catalog.md](../architecture/05-product-catalog.md), [05-product-database-design.md](../architecture/05-product-database-design.md), [05-product-api-contracts.md](../architecture/05-product-api-contracts.md), [05-product-frontend-architecture.md](../architecture/05-product-frontend-architecture.md)) and ADRs ([ADR-0008](../architecture/decisions/ADR-0008-catalog-product-aggregate-and-variant-model.md)–[ADR-0010](../architecture/decisions/ADR-0010-catalog-pricing-ownership-and-module-integration.md)); `ae6102b` (refined in `ca400d2`). |
| **M05.2** | Database & Liquibase | **COMPLETED** | Liquibase `v1.0.0/16`–`v1.0.0/22` (`product_categories`, `units_of_measure`, 8-unit seed, `products`, `product_variants`, `product_barcodes`, `ON DELETE RESTRICT` tenant & business FKs); `CatalogDatabaseMigrationIntegrationTest` (25/25 passing) & `PostgresLiquibaseMigrationIntegrationTest` (Testcontainers PostgreSQL 16); `ae6102b`, `f4e617f`. |
| **M05.3** | Domain & Persistence | **COMPLETED** | `Product`, `ProductVariant`, `ProductBarcode`, `ProductCategory`, `UnitOfMeasure`, `TenantAwareRepository` extensions + tenant-scoped `ProductBarcodeRepository`, 5 application services, `CatalogTenantBootstrapListener`, `ProductQueryFacade`; 60/60 catalog domain/service/integration/concurrency tests + 9/9 `ArchitectureTest` rules passing; `e7d0217`, `f001f00`. |
| **M05.4** | REST API & Authorization | **COMPLETED** | `ProductController`, `ProductVariantController`, `ProductCategoryController`, `UnitOfMeasureController`, `CatalogQueryService`, MapStruct `CatalogWebMapper`, `PageResponse<T>`, RBAC `TENANT`/optional-`branchId` read & `TENANT` write guards; `CatalogWebApiIntegrationTest` (5/5) & `CatalogRbacSecurityIntegrationTest` (4/4) in 273/273 backend test run (`2026-11-25`, no backend edits since); `60383ff`, `ca400d2`. |
| **M05.5** | Frontend | **COMPLETED** | FSD slices `entities/{product,category,unit}`, `useCatalogReadBranchScope`, `features/catalog/*`, `widgets/{product-table,category-tree,app-sidebar}`, `pages/catalog/*` & routes; `vitest run` 19 files / 74/74 passing (18 new catalog tests) & `tsc && vite build` passing; `e95d3d6`. |
| **M05.6** | Integration, Security & Quality Audit | **PLANNED** | Pending post-M05.5 end-to-end live backend/frontend contract audit, full-stack multi-tenant & branch-scope cache isolation audit, and combined regression/ArchUnit closeout pass. |

- **Implemented Behavior (M05.1–M05.5)**:
  - Unified `Product` + `ProductVariant` (`SIMPLE` | `VARIABLE`) + `ProductBarcode` (`0..N`) aggregate with normalized uppercase SKU uniqueness `(tenant_id, sku)` and barcode uniqueness `(tenant_id, barcode)`.
  - Bounded 5-level Adjacency List `ProductCategory` hierarchy (`depth 0..4`) with tenant-locked cycle/depth guards and single-query tree assembly.
  - Tenant-owned `UnitOfMeasure` (`PCS`, `KG`, `G`, `L`, `ML`, `M`, `BOX`, `PACK` seeded at migration time and atomically on new-tenant creation via `TenantCreatedEvent`, plus custom units).
  - Paginated/filtered/allowlisted product search, barcode/SKU POS lookup, draft-only physical delete vs `ARCHIVED` lifecycle, `ProductQueryFacade`, and full React 19 FSD catalog management UI.
- **Known Limitations & Deferred Scope**:
  - M05.5 frontend unit/page tests verify behavior against mocked API clients (Vitest + jsdom); live backend/frontend E2E contract verification awaits M05.6.
  - Production frontend build reports a non-fatal Vite chunk size warning (`742.66 kB` single JS bundle; route-based code splitting deferred to later hardening), and no repository ESLint config is present (`npm run lint` has no project config).
  - `DELETE /api/v1/products/{productId}` deletes `DRAFT` products only (`ACTIVE` products use archive/status transitions); `ProductBarcodeRepository` does not extend `TenantAwareRepository` because `product_barcodes` omits `@Version`; UOM writes reuse `catalog.categories.manage`; branch-scoped readers must pass `?branchId=` on catalog `GET` requests.
  - Product image uploads (S3/MinIO), stock balances/WAC (`Inventory`), multi-unit UOM conversions (`Inventory`/`Purchases`), tax rules, and price books (`Sales`/`Finance`) remain deferred to M06+.
