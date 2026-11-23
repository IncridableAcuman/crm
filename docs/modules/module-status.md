# SAVDOCRM — Module Implementation Status

This document tracks the implementation status of all business and technical modules across milestones.

| Module | Bounded Context | Target Milestone | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Foundation** | Core & Infrastructure | **Milestone 01** | **COMPLETED** | Monorepo structure, Spring Boot 4.2.0, Gradle Wrapper 9.8.2, Docker Compose PostgreSQL 16, Liquibase, React 19 FSD, Health check. |
| **Tenancy** | `com.savdocrm.modules.tenancy` | **Milestone 02** | PLANNED | Tenants, Branches, Multi-tenancy isolation (`TenantContextHolder`, `TenantSecurityFilter`, `TenantExecutionTemplate`, Composite FKs). |
| **Identity & RBAC** | `com.savdocrm.modules.identity` | **Milestone 03 & 04** | PLANNED | User authentication, JWT with refresh rotation, Tenant memberships, Scoped 4-tier roles & permissions. |
| **Catalog** | `com.savdocrm.modules.catalog` | Milestone 05 | PLANNED | Products, Matrix Variants, Units of Measure, Barcodes, Categories, Brands. |
| **Inventory** | `com.savdocrm.modules.inventory` | Milestone 06 | PLANNED | Materialized stock balances with optimistic locking (`@Version`), Immutable `inventory_transactions` ledger, Transfers. |
| **Sales & POS** | `com.savdocrm.modules.sales` | Milestone 07 | PLANNED | Cashier shifts (`PosSession`), atomic checkout transaction, Idempotency key pattern, split payments, returns. |
| **Purchases** | `com.savdocrm.modules.purchases` | Milestone 08 | PLANNED | Supplier purchase orders, Goods receipts, updating weighted average cost. |
| **Parties** | `com.savdocrm.modules.parties` | Milestone 07 & 08 | PLANNED | Customers, Customer credit limits & debts, Suppliers directory. |
| **Finance** | `com.savdocrm.modules.finance` | Milestone 09 | PLANNED | Financial accounts, Accounts Receivable (AR) ledger, Accounts Payable (AP) ledger, Cash registers, Expenses. |
| **Analytics** | `com.savdocrm.modules.analytics` | Milestone 10 | PLANNED | Sales turnover, COGS, Gross profit margin, low-stock warnings, executive dashboards. |
| **System** | `com.savdocrm.modules.system` | Milestone 10 | PLANNED | Audit logs, outbox events, system settings. |

---

## Current Milestone Summary: Milestone 01 — Project Foundation
- **Backend**: Spring Boot 4.2.0 on Java 21, Gradle Wrapper 9.8.2, Liquibase baseline changelog, Actuator, Springdoc OpenAPI 3.2.1, MapStruct 1.6.3, System Health endpoint (`/api/v1/health`).
- **Frontend**: React 19, TypeScript (strict mode), Vite 6, Tailwind CSS, shadcn/ui, Feature-Sliced Design (`app`, `pages`, `widgets`, `features`, `entities`, `shared`).
- **Database / Infra**: Docker Compose for PostgreSQL 16, `.env.example`, `.gitignore`.
- **Validation**:
  - `./gradlew clean test` $\rightarrow$ **BUILD SUCCESSFUL**
  - `./gradlew build` $\rightarrow$ **BUILD SUCCESSFUL**
  - `npm run build` $\rightarrow$ **BUILT IN 3.3s (0 errors)**
