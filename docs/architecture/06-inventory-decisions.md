# M06 — Inventory Management: Architecture Decision Records (ADRs)

> **Status**: Architecture Decisions for M06.1 (Continuing from `ADR-0010` in [docs/architecture/decisions/](file:///home/izzatbek/Desktop/crm/docs/architecture/decisions/))  
> **Scope**: Architectural trade-offs, bounded-context boundaries, concurrency guarantees, and verified reconciliation of legacy documentation vs actual implemented codebase (`M01`–`M05.6`).

---

## ADR-0011: Place `Warehouse` in `inventory` Module with Optional `Branch` Reference

- **Status**: Proposed (M06.1)
- **Context**: Legacy [domain-modules.md](file:///home/izzatbek/Desktop/crm/docs/architecture/domain-modules.md) §3.1 listed `Warehouse` under `Tenancy`, while actual `modules/tenancy` implemented only `Tenant` and `Branch` (`v1.0.0/01`–`02`), and [ADR-0007](file:///home/izzatbek/Desktop/crm/docs/architecture/decisions/ADR-0007-rbac-scope-and-concurrency-architecture.md) explicitly deferred `WAREHOUSE` concepts until the Inventory module is built. Additionally, retail businesses often operate both branch-attached store warehouses and central hub warehouses (`branch_id = NULL`).
- **Options Considered**:
  1. *Put `Warehouse` in `tenancy`*: Creates circular or blurred boundaries because warehouse lifecycle deactivation guards depend directly on `StockBalance`, `StockTransfer`, and `StockReceipt`.
  2. *Require `branch_id NOT NULL` on every `Warehouse`*: Forces creation of fake "virtual branches" for central distribution hubs and breaks POS branch filtering.
  3. *Own `Warehouse` inside `com.savdocrm.modules.inventory` with nullable `branch_id` FK (`branches(tenant_id, id) ON DELETE RESTRICT`)*: Clean leaf ownership without module cycles.
- **Decision**: Choose **Option 3**. `Warehouse` lives in `com.savdocrm.modules.inventory`. `branch_id` is nullable to support `CENTRAL_HUB` facilities alongside branch-linked `RETAIL_STORE` warehouses (`1:N` branch-to-warehouses allowed, at most one active default warehouse per branch).
- **Consequences**:
  - `inventory` depends unidirectionally on `tenancy` (`Branch`); `tenancy` remains completely unaware of inventory.
  - RBAC must enforce explicit `WAREHOUSE` scope without inferring access to `branch_id = NULL` central hubs from `BRANCH` roles (`ADR-0016`).

---

## ADR-0012: Flat Warehouse `StockLocation` Model with Auto-Provisioned Default Location

- **Status**: Proposed (M06.1)
- **Context**: Retail stores often hold all stock in a single room, whereas central hubs and multi-aisle stores require separating sellable shelves (`STORAGE`/`PICKING`), staging docks (`RECEIVING_DOCK`), damaged goods (`DAMAGED`), and inspection holds (`QUARANTINE`). Making locations optional (`nullable location_id`) fractures uniqueness constraints in PostgreSQL (`NULL != NULL` in standard unique indexes) and splits balance logic across nullable/non-nullable code paths. Meanwhile, deep recursive tree hierarchies (`parent_location_id` + recursive CTEs) add complexity disproportionate to retail CRM needs.
- **Decision**: Introduce a flat `StockLocation` entity per `(tenant_id, warehouse_id)` with optional flat locator attributes (`zone`, `aisle`, `rack`, `bin`) and `LocationType` enum (`STORAGE`, `PICKING`, `RECEIVING_DOCK`, `DAMAGED`, `QUARANTINE`). Every `Warehouse` automatically creates one `is_default = true` `STORAGE` location upon creation, and `location_id` is **mandatory (`NOT NULL`)** on `StockBalance` and `InventoryTransactionLine`.
- **Consequences**:
  - Simple store operations omit `locationId` in API requests and seamlessly resolve to the warehouse default `STORAGE` location.
  - Complex hubs get strict non-sellable isolation (`DAMAGED`, `QUARANTINE`, `RECEIVING_DOCK`) and bin-level cycle counts with zero `NULL` uniqueness bugs.
  - If deep multi-level warehouse topology (WMS) is ever needed post-MVP, `zone/aisle/rack/bin` or an optional `parent_location_id` can be added backward-compatibly.

---

## ADR-0013: Dual-Projection Balance Model (`StockBalance` + `WarehouseStockSummary`) with Immutable Header-Line Ledger

- **Status**: Proposed (M06.1)
- **Context**: Legacy [database-design.md](file:///home/izzatbek/Desktop/crm/docs/architecture/database-design.md) sketched a single `inventory_stocks(warehouse_id, variant_id)` projection and a flat `inventory_transactions` row without location granularity, in-transit counts, damage/quarantine buckets, or multi-line atomic documents. Summing `inventory_transaction_lines` on every POS checkout or catalog page load would degrade at scale, while maintaining only location rows would force `GROUP BY` scans on every warehouse availability check.
- **Decision**:
  1. Model the immutable ledger as header + line (`InventoryTransaction` + `InventoryTransactionLine`), strictly append-only (`ON DELETE RESTRICT`, compensating `REVERSAL` only).
  2. Maintain `StockBalance` (`inventory_balances`) keyed by `(tenant_id, warehouse_id, location_id, variant_id)` as the authoritative pessimistic-locking balance row.
  3. Maintain `WarehouseStockSummary` (`inventory_warehouse_stocks`) keyed by `(tenant_id, warehouse_id, variant_id)` in the same ACID posting transaction, materializing `quantity_on_hand`, `quantity_sellable`, `quantity_reserved`, `quantity_available`, `quantity_staging`, `quantity_damaged`, `quantity_quarantined`, and inbound `quantity_in_transit`.
  4. Enforce zero negative physical stock on all MVP posting paths (`allow_negative_stock = false` default policy).
- **Consequences**:
  - POS and Catalog queries read `WarehouseStockSummary` in O(1) index lookup.
  - Location moves (`INTERNAL_MOVE`) update two `StockBalance` rows and update `WarehouseStockSummary` pool buckets without modifying `total_value` (`WAC`).
  - A reconciliation verification job can deterministically validate balances against the immutable ledger at any time.

---

## ADR-0014: Separate `InventoryReservation` Aggregate (No Ledger Churn) and Two-Step `StockTransfer` In-Transit Accounting

- **Status**: Proposed (M06.1)
- **Context**: Sales holds and pending quotes frequently expire or get cancelled before checkout. Writing ledger rows on reserve/expire bloats the physical stock ledger with non-physical noise. Similarly, single-step transfers ignore real transport lead times and break when stock is short-received or damaged in transit.
- **Decision**:
  1. **Reservations**: Model `InventoryReservation` + `InventoryReservationLine` as a separate operational aggregate updating `quantity_reserved` (`StockBalance` & `WarehouseStockSummary`). Reservations emit **zero** physical ledger rows until consumed by `SALE_ISSUE`. An idempotent scheduled job expires TTL-elapsed reservations.
  2. **Transfers**: Model `StockTransfer` + `StockTransferLine` as a two-step physical workflow:
     - `DISPATCHED` writes `TRANSFER_OUT` at source, captures `unitCostSnapshot`, and increases destination `quantity_in_transit`.
     - `RECEIVED` / `PARTIALLY_RECEIVED` / `CLOSED_WITH_VARIANCE` writes `TRANSFER_IN` at destination (supporting good, damaged, and short-variance lines) and decrements destination `quantity_in_transit`.
     - Dispatched transfers cannot be cancelled; corrections use receipt variance or a return transfer.
- **Consequences**:
  - The immutable ledger reflects 100% genuine physical stock movements.
  - Stock cannot be double-spent across concurrent POS checkouts, reservations, adjustments, or transfers because all four lock `StockBalance` and respect `quantity_on_hand - quantity_reserved`.

---

## ADR-0015: Weighted Average Cost (`WAC`) Valuation per `(tenant_id, warehouse_id, variant_id)` with `NUMERIC(15,4)`

- **Status**: Proposed (M06.1)
- **Context**: Retail businesses transfer goods between branches, receive partial shipments at varying supplier costs, and process customer returns frequently. Full FIFO receipt-layer tracking introduces brittle layer depletion/re-opening semantics on returns, adjustments, and negative-layer edge cases. Meanwhile, sharing a single tenant-wide WAC ignores freight/branch cost differences across warehouses.
- **Decision**: Adopt **Moving Weighted Average Cost (`WAC`)** stored in `inventory_costs` per `(tenant_id, warehouse_id, variant_id)` using `NUMERIC(15,4)` (`unit_cost`) and `NUMERIC(18,4)` (`total_value`) with `RoundingMode.HALF_UP`, strictly decoupled from Finance journal postings.
- **Consequences**:
  - COGS and stock-valuation queries run in O(1) time.
  - Transfers preserve source `unitCostSnapshot` at dispatch and blend into destination warehouse WAC on receipt.
  - Full depletion (`newQty == 0`) explicitly resets `total_value = 0.0000` and `unit_cost = 0.0000` to eliminate fractional penny residue.

---

## ADR-0016: Explicit `WAREHOUSE` RBAC Scope Without Branch Inheritance, Shared `idempotency_records`, and Doc-vs-Code Reconciliation

- **Status**: Proposed (M06.1)
- **Context**: Auditing existing architecture docs against the live `M01`–`M05.6` codebase revealed concrete divergences that M06 must resolve consistently instead of copying stale specs:
  1. **RBAC (`RoleScopeType` & Permissions)**: Implemented `RoleScopeType` only has `TENANT` and `BRANCH` (`ADR-0007`), and `Permissions.java` currently has 33 permissions (including 3 placeholders: `inventory.stock.read`, `inventory.adjustments.create`, `inventory.transfers.create`). Existing M04 invariants forbid auto-mutating tenant custom roles during migrations (`04-rbac-and-authorization.md` §5.4).
  2. **API Envelope & Error Format**: Implemented M04/M05 controllers return `ApiResponse<PageResponse<T>>` (`data.items`, `totalElements`, etc.), `200 OK` (`ApiResponse<Void>`) on deletes, and RFC 7807 `ProblemDetail` (`errors` map in `GlobalExceptionHandler`), **not** the legacy `pagination`/`error.details`/`204` shapes sketched in [api-conventions.md](file:///home/izzatbek/Desktop/crm/docs/architecture/api-conventions.md).
  3. **Idempotency Persistence**: Documented in [database-design.md](file:///home/izzatbek/Desktop/crm/docs/architecture/database-design.md) §4.9 and [api-conventions.md](file:///home/izzatbek/Desktop/crm/docs/architecture/api-conventions.md), but `idempotency_records` does **not yet exist** in Liquibase `v1.0.0/00`–`22` or Java source.
  4. **Foreign Key Delete Policy**: Legacy `database-design.md` sketched `ON DELETE CASCADE` on tenant/catalog FKs, whereas M05 (`v1.0.0/22`) and M04 (`15-harden-rbac-foreign-keys.xml`) standardized strict `ON DELETE RESTRICT`.
  5. **Technology Baseline Drift**: Older docs (`backend-architecture.md`, `ADR-011`) mention Spring Boot `3.4.x` and Gradle `8.10.2`, whereas `build.gradle.kts` and `gradle-wrapper.properties` run **Spring Boot `4.2.0`** and **Gradle `9.9.1`** on Java 21.
- **Decision**:
  1. Extend `RoleScopeType` to `{TENANT, BRANCH, WAREHOUSE}`, add nullable `warehouse_id` to `user_role_assignments` with composite FK `(tenant_id, warehouse_id) -> warehouses(tenant_id, id) ON DELETE RESTRICT`, add `uq_assignments_active_warehouse`, and enforce **zero implicit `BRANCH -> WAREHOUSE` inheritance** (only `TENANT` scope spans all branches/warehouses; warehouse access requires `TENANT` or explicit `WAREHOUSE` assignment).
  2. Add 20 granular `inventory.*` permissions while keeping all 3 existing `inventory.*` permission codes intact; automatically enable all `Permissions.ALL_PERMISSIONS` for `OWNER`, explicitly seed default new permissions onto system template roles for new tenants (and follow an explicit additive policy without touching custom roles for existing tenants).
  3. Standardize M06 REST endpoints strictly on the live `ApiResponse<T>`, `PageResponse<T>`, and RFC 7807 `ProblemDetail` contracts.
  4. Introduce shared `idempotency_records` in `com.savdocrm.shared` during M06.2 so Inventory, Sales, and Purchases share one hardened SHA-256 `Idempotency-Key` infrastructure.
  5. Enforce `ON DELETE RESTRICT` on every Inventory composite foreign key, aligning with M04/M05 hardening.
- **Consequences**:
  - Zero runtime/contract surprises between M06 and M01–M05.
  - Legacy `api-conventions.md`, `backend-architecture.md`, and `database-design.md` drift is explicitly documented for cleanup without altering M05 code.
