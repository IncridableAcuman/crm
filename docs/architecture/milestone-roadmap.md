# SAVDOCRM — Milestone Implementation Roadmap

This roadmap outlines the phased execution plan for implementing SAVDOCRM from blueprint to production deployment.

```mermaid
gantt
    title SAVDOCRM Engineering Roadmap
    dateFormat  YYYY-MM-DD
    section Phase 1: Foundation
    M1: Core Infra & Scoped Identity :done, m1, 2026-11-24, 7d
    section Phase 2: Catalog & Master Data
    M2: Multi-Branch & Product Catalog :active, m2, after m1, 7d
    section Phase 3: Inventory Ledger
    M3: Stock Ledger & Warehouses    :m3, after m2, 7d
    section Phase 4: Sales & POS
    M4: Point of Sale & Customer CRM :m4, after m3, 10d
    section Phase 5: Purchasing & AP
    M5: Purchases & Supplier Debts   :m5, after m4, 7d
    section Phase 6: Finance
    M6: Financial Accounts & Expenses :m6, after m5, 7d
    section Phase 7: Analytics & SaaS
    M7: Dashboards & Subscriptions   :m7, after m6, 7d
    section Phase 8: Production Readiness
    M8: Hardening & Container Deploy :m8, after m7, 7d
```

---

## Milestone 01: Project Foundation (Completed)
- **Backend Objectives**:
  - Gradle Wrapper **9.8.2** setup with Java 21 LTS toolchain and **Spring Boot 4.2.0**.
  - Liquibase master changelog and initial schema migrations (`system_metadata` baseline).
  - Health check endpoint `/api/v1/health` and Spring Boot Actuator `/actuator/health`.
  - Springdoc OpenAPI 3.2.1 integration (`/swagger-ui/index.html`).
  - Base domain foundations (`BaseEntity`, `ApiResponse<T>`).
  - Modular package structure under `com.savdocrm.modules.*`.
  - Baseline test `SavdoCrmApplicationTests` verifying Spring context loads.
- **Frontend Objectives**:
  - Vite 6 + React 19 + TypeScript (strict mode) setup with Tailwind CSS and shadcn/ui.
  - FSD folder structure: `app`, `pages`, `widgets`, `features`, `entities`, `shared`.
  - Axios client with environment base URL configuration.
  - TanStack Query provider and shadcn/ui baseline UI primitives.
  - Production build verification (`npm run build`).
- **Infrastructure Objectives**:
  - Docker Compose configuration for local PostgreSQL 16.
  - Root `.gitignore` and `.env.example`.
- **Deliverable**: Both backend and frontend building cleanly from scratch with zero compilation errors.

---

## Milestone 02: Multi-Tenancy Foundation
- **Backend Objectives**:
  - Tenancy domain (`Tenant`, `Branch`, `Warehouse`, `TenantSubscription`).
  - Multi-tenant isolation engine: `TenantContextHolder` (fail-safe closed), `TenantSecurityFilter` (guaranteed `finally` thread-pool cleanup), `TenantContextTaskDecorator` for `@Async`, `TenantExecutionTemplate` for background jobs.
  - Mandatory `TenantAwareRepository` base contract.
  - Liquibase migrations for `tenants`, `branches`, `warehouses` with **composite foreign keys** `(tenant_id, id)`.
  - Automated Multi-Tenant Isolation Integration Tests using **Testcontainers PostgreSQL 16**.
- **Frontend Objectives**:
  - Tenant context store (`useTenantStore`).
  - Store & Branch switcher component in header.
- **Deliverable**: Mathematically verified tenant data isolation at repository and database engine levels.

---

## Milestone 03: Authentication & Session Management
- **Backend Objectives**:
  - User entity, password hashing with BCrypt (work factor 12).
  - Stateless dual-token JWT architecture: Access Token (15 min) + Refresh Token (30 day rotation).
  - Endpoints: Registration, Login, Token Refresh, Logout.
  - Secure refresh token storage and revocation list in database.
- **Frontend Objectives**:
  - Zustand auth store (`useAuthStore`).
  - Axios request/response interceptors with token auto-refresh queue.
  - Login and Registration page views.
  - Protected route guard (`AuthGuard`).
- **Deliverable**: Complete end-to-end user authentication and persistent sessions.

---

## Milestone 04: Scoped RBAC & Permissions
- **Status**: Completed & Audited (M04.1–M04.5 Completed)
- **Sub-Milestones**:
  - **M04.1**: Architecture & Scoping Specification (Completed).
  - **M04.2**: Database Schema, Liquibase Migrations & Security Audit (Completed — migrations 09–15).
  - **M04.3**: RBAC Core Domain & Authorization Engine (Completed):
    - Domain entities: `Permission`, `Role`, `RolePermission`, `UserRoleAssignment`, `RoleScopeType`.
    - Authorization Engine: `PermissionAuthorizationService` enforcing TENANT and BRANCH scopes, live entity statuses, and fail-closed security.
    - Security Infrastructure: `RequestScopedPermissionMemoizer` for request-scoped zero-stale memoization.
    - Concurrency Safety: 6-predicate Last Active OWNER invariant with pessimistic locking on parent tenant row (`findByIdWithLock`).
    - Multi-Tenant Suspension: Deterministic UUID ordering (`ORDER BY tenant_id ASC`) preventing deadlocks during platform-wide user suspension in `UserService`.
    - Lifecycle & Bootstrap: Atomic tenant registration RBAC bootstrap and existing tenant bootstrap in `TenantBootstrapService`.
    - Role Management: `RoleAssignmentService` enforcing Grant-What-You-Hold, Owner Guard, self-elevation prevention, and historical audit preservation.
  - **M04.4**: REST Controllers, Role & Assignment APIs & Spring Security Integration (Completed & Verified).
  - **M04.5**: Frontend RBAC Guards & Administration UI (Completed & Audited — 171 backend tests, 56 frontend tests):
    - Dynamic 33-permission catalog integration via `GET /api/v1/permissions` with readable label mapping.
    - All 7 system roles rendered read-only and immutable; custom role lifecycle (create, edit details, edit permissions, delete/soft-deactivate).
    - Fail-closed `PermissionGuard` and route-level `PermissionRoute`, tenant-scoped query cache isolation, `TENANT`/`BRANCH` assignment scope UI, and RFC7807 ProblemDetail error handling.
    - Known limitation: `UserRoleAssignmentDto` exposes `membershipId` only; member directory search endpoint is not yet implemented in M04.
- **Backend Objectives**:
  - 2-tier active scope hierarchy in M04: Tenant and Branch (Warehouse deferred until inventory module).
  - Entities: `Role`, `Permission`, `RolePermission`, `UserRoleAssignment` (scoped to `branch_id`).
  - System role immutability in domain aggregate.
  - Fine-grained permission authorization engine.
- **Frontend Objectives**:
  - Permission-aware UI element guards (`PermissionGuard`).
  - Role management and user invitation views.
- **Deliverable**: Granular access control preventing unauthorized branch access, preserving historical audit records, and guaranteeing tenant owner invariants.

---

## Milestone 05: Product Catalog & Category Management
- **Status**: Completed — Pass With Limitations (M05.1–M05.6 Completed & Audited; M06 Not Started)
- **Sub-Milestones**:
  - **M05.1 — Architecture & Domain Design** *(Completed — Design-Only)*:
    - **Scope**: Bounded context definition, `Product`/`ProductVariant`/`ProductBarcode`/`Category`/`UnitOfMeasure` domain model, database schema, REST API & RBAC contracts, frontend FSD blueprint, and ADRs (`ADR-0008`–`ADR-0010`).
    - **Dependencies**: M02 Multi-Tenancy, M04 RBAC & 33-permission catalog.
    - **Deliverables**: `05-product-catalog.md`, `05-product-database-design.md`, `05-product-api-contracts.md`, `05-product-frontend-architecture.md`, `ADR-0008`–`ADR-0010`, updated `domain-modules.md` & `milestone-roadmap.md`.
    - **Exclusions**: Zero Java, TypeScript, Liquibase, or seed implementation.
  - **M05.2 — Database Schema & Liquibase** *(Completed — Schema & Migration Tests Only)*:
    - **Scope**: Append-only Liquibase migrations (`v1.0.0/16`–`v1.0.0/22`) for `product_categories`, `units_of_measure`, idempotent default 8-unit tenant seeding, `products`, `product_variants`, and `product_barcodes` (`ON DELETE RESTRICT` across all tenant and business FKs).
    - **Dependencies**: M05.1.
    - **Tests & Acceptance Criteria**: Testcontainers PostgreSQL 16 integration tests (`CatalogDatabaseMigrationIntegrationTest`, `PostgresLiquibaseMigrationIntegrationTest`) proving composite FKs, cross-tenant rejection, SKU/barcode/sibling uniqueness, partial default-variant & primary-barcode indexes, `CHECK` constraints, `RESTRICT` delete guards, and idempotent UOM seeding.
    - **Exclusions**: Java production code (`TenantBootstrapService` new-tenant UOM hook, entities, services, controllers) and frontend UI.
  - **M05.3 — Product & Category Domain, Repositories & Public Facade** *(Completed & Verified)*:
    - **Scope**: `Product`, `ProductVariant`, `ProductBarcode`, `ProductCategory`, `UnitOfMeasure` JPA entities, `TenantAwareRepository` extensions (`ProductCategoryRepository`, `UnitOfMeasureRepository`, `ProductRepository`, `ProductVariantRepository`) + tenant-scoped `ProductBarcodeRepository` (explicitly scoped because `product_barcodes` omits `@Version`), `ProductService` (including `archiveProduct` / `restoreProduct` and explicit historical vs active default semantics), `ProductVariantService`, `ProductBarcodeService`, `ProductCategoryService`, `UnitOfMeasureService`, single event-driven new-tenant standard UOM bootstrap (`TenantCreatedEvent` -> `CatalogTenantBootstrapListener`), uniform `Tenant -> ProductCategory` lock hierarchy, and `ProductQueryFacade`.
    - **Dependencies**: M05.2.
    - **Tests & Acceptance Criteria**: Domain unit tests, Mockito application service tests, Testcontainers PostgreSQL 16 integration & concurrency tests, UOM bootstrap rollback test, and ArchUnit module boundary checks.
    - **Exclusions**: HTTP controllers, REST pagination/search endpoints, RBAC endpoint wiring, and frontend UI.
  - **M05.4 — Product & Category REST API & RBAC Integration** *(Completed & Verified)*:
    - **Scope**: `ProductController`, `ProductVariantController`, `ProductCategoryController`, `UnitOfMeasureController`, `CatalogQueryService`, `PageResponse<T>`, validated request/response DTOs, `CatalogWebMapper` (MapStruct), strict `TENANT` (`hasPermission`) / optional-`branchId` (`hasBranchPermission`) enforcement for catalog reads, and `@PreAuthorize` `TENANT`-scope enforcement on all catalog writes (including `catalog.products.delete` for archive/delete).
    - **Dependencies**: M05.3.
    - **Tests & Acceptance Criteria**: MockMvc PostgreSQL 16 Testcontainers integration tests (`CatalogWebApiIntegrationTest`, `CatalogRbacSecurityIntegrationTest`, `PermissionAuthorizationServiceTest`) covering database pagination/filtering/allowlisted sorting, single-query category tree, lifecycle & aggregate ownership invariants, `400`/`401`/`403`/`404`/`409` RFC7807 errors, `TENANT`/`BRANCH`/wrong-branch/suspended/cross-tenant RBAC checks, and ArchUnit checks.
    - **Exclusions**: Frontend UI, Inventory stock, Sales/Purchases.
  - **M05.5 — Product & Category Frontend (FSD)** *(Completed & Verified — Commit `e95d3d6`)*:
    - **Scope**: `entities/{product,category,unit}`, `useCatalogReadBranchScope` (`entities/permission`), `features/catalog/{product-form,variant-editor,category-form,unit-manager}`, `widgets/{product-table,category-tree,app-sidebar}`, `pages/catalog/{ProductsListPage,ProductDetailPage,ProductFormPage,CategoriesPage}`, `HomePage` catalog summary card, `/catalog/products`, `/catalog/products/new`, `/catalog/products/:id`, `/catalog/products/:id/edit`, and `/catalog/categories` routes with `PermissionRoute` + `PermissionGuard` integration.
    - **Dependencies**: M05.4.
    - **Verification & Known Limitations**: Verified via Vitest (`19` test files, `74/74` tests passing including `18` new catalog tests against mocked API clients) and `tsc && vite build` (`PASS`, with non-fatal chunk-size warning); repository ESLint config is absent; live full-stack E2E verification remains for M05.6.
    - **Exclusions**: POS checkout, stock balances, image uploads.
  - **M05.6 — Integration, Security & Quality Audit** *(Completed — Pass With Limitations)*:
    - **Scope**: End-to-end backend/frontend contract audit, auth/tenant-spoofing & RBAC security verification, archived barcode invariant hardening (`ProductBarcodeService`), multi-tenant & branch cache isolation (`catalogCacheIsolation.test.tsx`), live Spring Boot + PostgreSQL 16 + Vite integration verification, and full regression (`276/276` backend tests, `9/9` ArchUnit rules, `76/76` frontend tests, `tsc && vite build`).
    - **Dependencies**: M05.5.
    - **Audit Report & Limitations**: See [m05-integration-security-quality-audit.md](../testing/m05-integration-security-quality-audit.md). Headless browser DOM E2E and ESLint were not executed due to missing host browser binary and project ESLint configuration.
    - **Exclusions**: M06 Inventory implementation (not yet started; next planned milestone upon explicit approval).

---

## Milestone 2 (Legacy Reference): Store Branches, Warehouses & Product Catalog
- **Backend Objectives**:
  - Tenancy module: Branch and Warehouse management endpoints with composite FK constraints.
  - Catalog module: Categories (hierarchical tree), Brands, Units of Measure (`pcs`, `kg`, `meter`, etc. with decimal configuration).
  - Product & Matrix Variant engine: `Product`, `ProductVariant`, dynamic attributes (`size`, `color`), multi-barcodes (`barcodes` table with EAN-13 validation).
  - Public facade: `ProductQueryFacade` for cross-module variant queries.
- **Frontend Objectives**:
  - Store and Branch management screens.
  - Category tree editor and Unit of Measure manager.
  - Rich Product Catalog data table with search, category filtering, and status toggling.
  - Multi-step Product Creation modal with dynamic variant generator.
  - Barcode label generator and thermal print preview.
- **Deliverable**: Complete catalog management capable of modeling grocery, fashion, and electronics products.

---

## Milestone 3: Inventory Engine & Double-Entry Stock Ledger
- **Backend Objectives**:
  - Inventory module: `InventoryStock` table with optimistic locking (`@Version`) for high-concurrency stock reads.
  - Immutable `inventory_transactions` ledger capturing delta, before/after balances, unit cost, and reference.
  - Stock adjustment workflows (surplus intake, shrinkage, write-offs).
  - Warehouse transfer workflow (`DRAFT` $\rightarrow$ `IN_TRANSIT` $\rightarrow$ `RECEIVED`).
  - Low-stock query alerts and warehouse stock summary endpoints.
- **Frontend Objectives**:
  - Real-time stock balance data table with branch/warehouse selector.
  - Low-stock badge alerts and stockout warnings.
  - Warehouse-to-warehouse stock transfer wizard with status tracking.
  - Manual stock adjustment and write-off dialogs with reason logging.
- **Deliverable**: Fully verifiable stock ledger with zero unexplained discrepancies.

---

## Milestone 4: Sales, Fast POS & Customer CRM
- **Backend Objectives**:
  - Parties module: Customer profiles, customer groups, credit limits, and loyalty balances.
  - POS Session / Cash Shift lifecycle (`OPEN`, `CLOSED`, opening float, closing discrepancy calculation).
  - Idempotency enforcement: `idempotency_records` table and `Idempotency-Key` validator.
  - Atomic POS Checkout: Single local synchronous ACID transaction spanning Sale, Stock deduction (`InventoryMovementService`), Payment (`FinancePaymentService`), and Customer Debt creation (AR).
  - Sales return and refund processing with atomic inventory restock.
- **Frontend Objectives**:
  - Dedicated, distraction-free **Point of Sale (POS) Interface**:
    - Global hardware barcode scanner listener (`useBarcodeScanner`).
    - Numeric keypad and fast touch grid for quick-tap categories and products.
    - Keyboard hotkeys (`Space` for tender, `F2` for search, `F4` for customer selection).
    - Split-tender checkout modal (Cash tender calculation with change return).
    - Thermal receipt layout formatted for 58mm/80mm ESC/POS printers.
  - Sales history view with printable receipts and return modal.
- **Deliverable**: Blazing fast, retail-grade POS checkout interface with guaranteed transaction consistency.

---

## Milestone 5: Purchases & Supplier Accounts Payable (AP)
- **Backend Objectives**:
  - Parties module: Supplier directory with contact details and current payable balance.
  - Purchases module: Purchase Orders (`DRAFT`, `ORDERED`, `PARTIALLY_RECEIVED`, `COMPLETED`).
  - Goods Receipt verification: Incrementing inventory stock ledger and automatically updating `weighted_average_cost`.
  - Recording supplier debt records (Accounts Payable) upon purchase receipt.
- **Frontend Objectives**:
  - Supplier management directory.
  - Purchase Order creation wizard with live cost calculations.
  - Goods Receipt receiving screen with barcode scanner verification against PO items.
  - Supplier debt ledger view showing outstanding invoices.
- **Deliverable**: End-to-end procurement and inventory restocking cycle.

---

## Milestone 6: Financial Operations & Cash Register Management
- **Backend Objectives**:
  - Finance module: Financial Accounts (Cash registers, Bank accounts, Safe).
  - Operating expense tracking with customizable `ExpenseCategory` (Rent, Payroll, Utilities, Maintenance).
  - Non-sale miscellaneous income logging.
  - Customer Debt (AR) repayment settlement endpoints.
  - Supplier Debt (AP) payment disbursement endpoints.
- **Frontend Objectives**:
  - Financial account overview (Total cash in registers, bank balances).
  - Expense logging modal with receipt attachment support.
  - Customer Debt collection dialog with partial repayment and payment allocation.
  - Supplier debt disbursement modal.
- **Deliverable**: Comprehensive cash flow, petty cash, and debt reconciliation.

---

## Milestone 7: Analytics, Executive Reports & SaaS Subscriptions
- **Backend Objectives**:
  - Analytics module: Real-time queries for sales turnover, revenue, COGS, gross profit margins, and inventory valuation (FIFO & Weighted Average).
  - Customer debt aging report (Current, 30 days overdue, 60+ days overdue).
  - Cashier sales performance and shift discrepancy auditing.
  - Multi-tenant subscription management: Tier enforcement (branch count, product count, employee limits).
- **Frontend Objectives**:
  - Executive Dashboard with interactive Recharts visualizations (daily revenue trends, top-selling products, sales by branch).
  - Inventory valuation and shrinkage reports with CSV/Excel export.
  - Subscription and billing management page in store settings.
- **Deliverable**: Executive visibility and actionable business intelligence.

---

## Milestone 8: Hardening, Performance Tuning & Production Deployment
- **Backend Objectives**:
  - ArchUnit test suite enforcing strict modular monolith rules and no cross-module leakage.
  - Testcontainers integration test suite verifying transactional rollback and database constraints.
  - Performance profiling: Query plan analysis, index optimization, connection pool tuning.
- **Frontend Objectives**:
  - Lighthouse performance optimization: Code-splitting via dynamic `React.lazy`, asset compression.
  - Full keyboard accessibility audit for POS.
- **Infrastructure Objectives**:
  - Multi-stage Docker build for backend (distroless Java 21 image).
  - Optimized Nginx Docker container for frontend SPA with Brotli/Gzip compression.
  - Production Docker Compose configuration with PostgreSQL, MinIO, and reverse proxy.
- **Deliverable**: Battle-tested, secure, production-ready retail CRM deployment.
