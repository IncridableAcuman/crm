# SAVDOCRM — Architecture Decision Records (ADRs)

This document records the key architectural decisions, rationale, context, and trade-offs evaluated during the design of SAVDOCRM.

---

## ADR-001: Modular Monolith vs Microservices Architecture
- **Status**: Accepted
- **Context**: Retail store management requires strict ACID transactional integrity across sales, inventory deductions, customer debt, and cash register balances. Microservices introduce distributed transactions (2PC/Saga), eventual consistency bugs, network latency, and heavy infrastructure management overhead for small and medium retail businesses.
- **Decision**: Adopt a **Modular Monolith** architecture using Java 21 and Spring Boot. Enforce strict bounded contexts using Java package visibility, module facades, and ArchUnit verification.
- **Consequences**:
  - *Positive*: Zero network latency between domain boundaries; straightforward ACID transactions; simplified local debugging; low deployment and hosting costs; rapid feature development.
  - *Negative*: Entire monolith must be redeployed together; requires developer discipline to prevent cross-module spaghetti (mitigated via automated ArchUnit tests).

---

## ADR-002: Multi-Tenancy Strategy: Shared Database with Shared Schema
- **Status**: Accepted
- **Context**: The long-term business model is SaaS supporting thousands of retail stores. Database-per-tenant exhausts connection pools and requires astronomical RAM. Schema-per-tenant causes catalog locking bottlenecks in PostgreSQL when scaling past 1,000 schemas and makes Liquibase migrations painfully slow.
- **Decision**: Implement a **Shared Database, Shared Schema** with a discriminator column (`tenant_id`) on all tenant-scoped tables. Data isolation is enforced using Hibernate `@Filter`, `TenantContextHolder`, and composite indexes prefixed with `tenant_id`.
- **Consequences**:
  - *Positive*: Maximizes hardware utilization; instant tenant onboarding; single migration run updates all tenants; straightforward cross-tenant SaaS metrics.
  - *Negative*: Extreme care required to prevent tenant data leakage (mitigated by zero-trust backend resolution and 7-pillar isolation defense).

---

## ADR-003: Immutable Double-Entry Inventory Ledger
- **Status**: Accepted
- **Context**: Naive inventory systems execute simple `UPDATE products SET stock = stock - 5`. This destroys audit history, prevents diagnosing inventory shrinkage or cashier theft, and fails during stock reconciliation.
- **Decision**: Adopt a dual-structure inventory model:
  1. An immutable append-only `inventory_transactions` ledger recording every physical movement with delta, timestamp, reason, and actor.
  2. A materialized `inventory_stocks` snapshot table protected by optimistic concurrency locking (`@Version`) for high-speed stock reads and checkout validations.
- **Consequences**:
  - *Positive*: Complete auditability; zero unexplained stock discrepancy; mathematical reconstruction of historical stock levels at any past date.
  - *Negative*: Additional disk storage for transaction logs (readily handled by PostgreSQL B-Trees and archival strategies).

---

## ADR-004: Arbitrary Precision Decimals for Financial & Quantity Data
- **Status**: Accepted
- **Context**: Standard floating-point types (`float`, `double`, `real`) introduce binary rounding errors (e.g., $0.1 + 0.2 = 0.30000000000000004$), which is unacceptable in legal accounting, taxation, and retail stock.
- **Decision**: Store all currency, price, discount, and tax figures as SQL `NUMERIC(15, 4)` and handle in Java via `BigDecimal` with `RoundingMode.HALF_UP`. Quantities supporting fractional units (kg, liters) are stored as `NUMERIC(12, 3)`.
- **Consequences**:
  - *Positive*: Zero rounding drift; absolute penny-exact financial accuracy; compliance with international accounting standards.
  - *Negative*: Minor memory and CPU overhead compared to primitive doubles (negligible in modern servers).

---

## ADR-005: Inter-Module Communication via Service Facades and Transactional Events
- **Status**: Accepted
- **Context**: Modules in a modular monolith must exchange information without tight coupling or importing each other's database entities.
- **Decision**: Synchronous data reads use explicit public Java interfaces (`Facade`) returning immutable DTOs. Side-effect workflows (e.g. notifications, analytics) utilize Spring Domain Events (`ApplicationEventPublisher`) handled by `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- **Consequences**:
  - *Positive*: Zero circular dependencies; modules can be extracted into standalone microservices in the future with minimal refactoring; failures in non-critical listeners do not abort the core transaction.
  - *Negative*: Requires developers to structure operations into commands, queries, and domain events rather than direct method calling.

---

## ADR-006: Dual-State Strategy on Frontend: TanStack Query vs Zustand
- **Status**: Accepted
- **Context**: Client applications frequently suffer from state synchronization bugs when remote server data is duplicated into Redux/Zustand stores and falls out of sync.
- **Decision**: Strictly separate state into two distinct technologies:
  - **Server State**: Managed exclusively by **TanStack Query v5** (caching, invalidation, background revalidation, optimistic updates, pagination).
  - **Client State**: Managed exclusively by **Zustand** for transient, browser-only state (active JWT session, tenant/branch switcher, POS active cart items, dark/light theme).
- **Consequences**:
  - *Positive*: Zero stale data duplication bugs; reduced boilerplate; automatic background synchronization.
  - *Negative*: Developers must deliberately categorize every state piece before implementation.

---

## ADR-007: Liquibase for Database Schema Evolution
- **Status**: Accepted
- **Context**: Database changes must be deterministic, versioned, reversible, and testable in CI/CD pipelines before production deployment.
- **Decision**: Use **Liquibase** with XML/YAML changelogs. Changelogs are modularized by domain module and executed automatically during Spring Boot application startup or CI test runs.
- **Consequences**:
  - *Positive*: Database schema state is versioned alongside application code; automated rollback scripts; native support across multiple environments.
  - *Negative*: Requires writing disciplined changelogs rather than relying on Hibernate `ddl-auto: update` (which is strictly forbidden in production).

---

## ADR-008: Feature-Sliced Design (FSD 2.0) for React Frontend
- **Status**: Accepted
- **Context**: Typical React projects grouped solely by technical types (`components/`, `hooks/`, `services/`) degenerate into spaghetti dependencies as business domains expand.
- **Decision**: Adopt **Feature-Sliced Design (FSD)** with strict layers (`app` $\rightarrow$ `pages` $\rightarrow$ `widgets` $\rightarrow$ `features` $\rightarrow$ `entities` $\rightarrow$ `shared`).
- **Consequences**:
  - *Positive*: Clear code ownership; predictable file discovery; no circular imports; ease of onboarding new frontend engineers.
  - *Negative*: Higher initial folder nesting and strict discipline needed when defining layer boundaries.

---

## ADR-009: Idempotency Key Pattern for Financial & POS Transactions
- **Status**: Accepted
- **Context**: In retail environments, spotty Wi-Fi or double-clicking the checkout button can trigger duplicate transactions, charging a customer twice or deducting stock twice.
- **Decision**: Enforce an optional/mandatory `Idempotency-Key` header on critical write endpoints (`POST /api/v1/sales/checkout`, `POST /api/v1/finance/payments`). Keys and hashed responses are cached and verified atomically against `idempotency_records`.
- **Consequences**:
  - *Positive*: Complete immunity against duplicate billing and duplicate inventory movement.
  - *Negative*: Requires client to generate UUID keys per checkout attempt and backend to store key execution status.

---

## ADR-010: Zero-Trust Frontend Tenant Resolution via Cryptographic JWT
- **Status**: Accepted
- **Context**: Multi-tenant apps that accept `X-Tenant-ID` header directly from frontend clients are vulnerable to tampering and privilege escalation.
- **Decision**: Resolve tenant identity solely from cryptographically signed JWT claims on the backend. Switching tenants requires calling an authenticated endpoint that validates membership before re-issuing a new signed token.
- **Consequences**:
  - *Positive*: Mathematically eliminates unauthorized cross-tenant data access from forged client headers.
  - *Negative*: Changing tenant context requires a token re-issue handshake.

---

## ADR-011: Spring Boot 3.4.x and Gradle Wrapper 8.10.2 Version Strategy
- **Status**: Accepted
- **Context**: Selecting framework versions for Java 21 requires evaluating ecosystem stability. Spring Boot 4.x is currently pre-alpha/in development targeting Spring Framework 7 and Jakarta EE 11, lacking production support for OpenAPI, Liquibase, and MapStruct. Spring Boot 3.5.x is still in development. Spring Boot 3.3.x is stable, but 3.4.x provides optimal Java 21 virtual thread runtime performance and enhanced Docker Compose integration.
- **Decision**: Adopt **Spring Boot 3.4.x** with OpenJDK 21 LTS, built via **Gradle Wrapper 8.10.2** (Kotlin DSL). Reject Spring Boot 4.x as prematurely risky.
- **Consequences**:
  - *Positive*: Battle-tested ecosystem compatibility; full support for Liquibase 4.29+, MapStruct 1.6+, and Springdoc 2.6+; optimal Java 21 virtual threading.
  - *Negative*: Requires adhering to Spring Boot 3.4.x deprecations.

---

## ADR-012: Database-Level Composite Foreign Keys for Multi-Tenant Integrity
- **Status**: Accepted
- **Context**: Standard single-column foreign keys (`FOREIGN KEY (parent_id) REFERENCES parent(id)`) allow a software bug to link Tenant B's child record to Tenant A's parent record if both IDs exist in the database.
- **Decision**: Require all tenant-scoped tables to declare composite unique constraints on `(tenant_id, id)` and require child tables to define foreign keys on `FOREIGN KEY (tenant_id, parent_id) REFERENCES parent(tenant_id, id)`.
- **Consequences**:
  - *Positive*: Database engine rejects cross-tenant references mathematically, even if ORM filters fail or native SQL contains a bug.
  - *Negative*: Marginally larger index sizes and composite key definitions.

---

## ADR-013: Synchronous Local ACID Transaction Boundary for POS Checkout
- **Status**: Accepted
- **Context**: Naive microservice or event-driven designs split sale creation, stock deduction, and payment collection into eventual consistency steps. If stock deduction fails after payment is accepted, the customer and cashier face an inconsistent, unresolvable state.
- **Decision**: Execute the core POS checkout within a **single synchronous local ACID database transaction**: Sale creation, Stock deduction (`InventoryMovementService`), Payment collection (`FinancePaymentService`), and Customer debt creation occur atomically. If any invariant fails, all changes roll back immediately.
- **Consequences**:
  - *Positive*: 100% consistent state; zero orphaned sales or phantom stock deductions; immediate cashier feedback.
  - *Negative*: Slightly longer database transaction lock duration (mitigated by optimistic locking on stock records).

---

## ADR-014: Fail-Safe Closed Architecture and Context Propagation for Background Jobs
- **Status**: Accepted
- **Context**: In multi-tenant systems, background jobs (`@Scheduled`) and `@Async` workers execute outside the HTTP request lifecycle. If `TenantContextHolder` is empty, code might inadvertently query across all tenants or throw unhandled null pointer exceptions.
- **Decision**: Implement a **Fail-Safe Closed Architecture**:
  1. Repositories throw `TenantContextMissingException` if invoked without a tenant context.
  2. Background schedulers must use `TenantExecutionTemplate.executeAsTenant(tenantId, runnable)` to iterate through tenants one-by-one.
  3. Async tasks use `TenantContextTaskDecorator` to copy security and MDC context across threads.
- **Consequences**:
  - *Positive*: Eliminates silent cross-tenant leaks in background threads; eliminates thread-pool contamination.
  - *Negative*: Developers must explicitly wrap background tasks in `TenantExecutionTemplate`.

---

## ADR-015: Strict Separation of Inventory Movement Ledger and Financial Debt Ledgers
- **Status**: Accepted
- **Context**: Conflating physical inventory movements with monetary accounts causes tight circular coupling between Inventory and Finance modules.
- **Decision**: Partition ledgers into independent bounded contexts:
  - `Inventory`: Tracks physical quantities and Weighted Average Cost in `inventory_transactions`. Zero financial account knowledge.
  - `Finance`: Tracks cash drawer balances, bank accounts, customer debt (AR), and supplier debt (AP). Zero physical warehouse knowledge.
  - `Sales` and `Purchases` act as upstream orchestrators invoking both leaf modules independently.
- **Consequences**:
  - *Positive*: Clean unidirectional dependencies; zero circular imports; independent auditable ledgers.
  - *Negative*: Requires Sales and Purchases to coordinate calls across both domain services.
