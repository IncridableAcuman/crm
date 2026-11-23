# SAVDOCRM — Module Implementation Status

This document tracks the implementation status of all business and technical modules across milestones, clearly distinguishing **implemented behavior** from **planned behavior**.

| Module | Bounded Context | Target Milestone | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Foundation** | Core & Infrastructure | **Milestone 01** | **COMPLETED** | Monorepo structure, Spring Boot 4.2.0, Gradle Wrapper 9.9.1, Docker Compose PostgreSQL 16, Liquibase baseline, React 19 FSD, Health check. |
| **Tenancy** | `com.savdocrm.modules.tenancy` | **Milestone 02** | **COMPLETED** | Tenants, Branches, Multi-tenancy isolation (`TenantContextHolder`, `TenantExecutionTemplate`, `TenantContextTaskDecorator`, `TenantAwareRepository`, Composite FKs). Verified via PostgreSQL Testcontainers. |
| **Identity & Auth** | `com.savdocrm.modules.identity` | **Milestone 03** | PLANNED | User entity, registration, login, stateless JWT access tokens, refresh token rotation, revocation list. |
| **RBAC** | `com.savdocrm.modules.identity` | **Milestone 04** | PLANNED | 4-tier scoped permissions (Platform, Tenant, Branch, Warehouse), `BranchSecurityEvaluator`, method security. |
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
  - Automated tests: 16 tests executed across unit and Testcontainers PostgreSQL 16 integration suites verifying fail-closed behavior, cross-tenant read/write isolation, and composite constraints.
- **Authentication Boundary & Deferred Behavior**:
  - No client-provided `X-Tenant-Id` header is accepted.
  - No unauthenticated tenant management endpoints are exposed.
  - Authenticated user membership verification and JWT tenant extraction are explicitly deferred to Milestone 03.
