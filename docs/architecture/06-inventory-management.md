# M06 — Inventory Management: Architecture & Domain Design

> **Status**: Architecture Specification (M06.1 — Design Only; Not Yet Implemented)  
> **Module**: `com.savdocrm.modules.inventory`  
> **Bounded Context**: Inventory Management (Warehouses, Stock Locations, Dual-Entry Stock Ledger, Stock Balances, Receiving, Counting, Adjustments, Inter-Warehouse Transfers, Reservations, and WAC Costing)

---

## 1. Purpose & Bounded Context Responsibilities

The **Inventory** module is the authoritative system of record inside SAVDOCRM for physical stock quantities, warehouse storage topology, stock reservations, inter-warehouse movements, and moving-weighted-average inventory valuation (`WAC`).

### 1.1 Responsibilities Owned by Inventory

1. **Warehouse & Storage Topology**: Managing multi-tenant `Warehouse` facilities (both branch-attached store backrooms and unassigned central distribution hubs) and bounded `StockLocation` bins/zones (`STORAGE`, `PICKING`, `RECEIVING_DOCK`, `DAMAGED`, `QUARANTINE`).
2. **Immutable Double-Entry-Style Stock Ledger**: Recording every physical stock state mutation through append-only `InventoryTransaction` headers and `InventoryTransactionLine` records.
3. **Materialized Stock Balances**: Maintaining location-level `StockBalance` (`inventory_balances`) and warehouse-level `WarehouseStockSummary` (`inventory_warehouse_stocks`) projections with strict concurrency control and zero-negative-stock enforcement by default.
4. **Operational Stock Documents**: Governing the lifecycles of `StockReceipt`, `StockCount`, `StockAdjustment`, and two-step `StockTransfer` documents.
5. **Inventory Reservations**: Managing explicit soft stock holds (`InventoryReservation`) with TTL expiration and atomic consumption upon sale completion.
6. **Inventory Valuation (`WAC`)**: Computing Weighted Average Cost (`InventoryCost`) per `(tenant_id, warehouse_id, variant_id)` at `NUMERIC(15,4)` precision, strictly decoupled from Finance accounting journal postings.

### 1.2 Explicit Non-Responsibilities (Anti-Duplication Rules)

| Concern | Owning Module | Inventory Boundary Rule |
|---|---|---|
| Product definitions, SKUs, barcodes, categories, UOMs | `catalog` | Inventory stores `variant_id` (UUID) only and reads active metadata via `ProductQueryFacade`. Zero duplication of `Product`, `ProductVariant`, `Category`, or `UnitOfMeasure`. |
| Tenants and Store Branches | `tenancy` | Inventory references `tenant_id` and optional `branch_id`; `tenancy` owns `Tenant` and `Branch`, while `inventory` owns `Warehouse` (`ADR-0011`). |
| Purchase Orders, Supplier Invoices, PO Pricing | `purchases` | Inventory accepts generic `StockReceipt` requests with optional `(source_reference_type, source_reference_id)`; `purchases` orchestrates PO-to-receipt calls. |
| POS Sales Orders, Customer Returns, Checkout Pricing | `sales` | `sales` orchestrates reservation/consumption/deduction via Inventory facades without leaking inventory internals into POS cart entities. |
| Suppliers and Customers | `parties` | Inventory does not model supplier or customer master data. |
| General Ledger, Cashboxes, Payables, Receivables | `finance` | Inventory computes physical stock `unit_cost` (`WAC`) and emits valuation snapshots to callers/events; `finance` owns accounting journals. |
| System Roles & RBAC Enforcement | `identity` | `identity` enforces `TENANT`, `BRANCH`, and `WAREHOUSE` RBAC scopes (`ADR-0016`). |

---

## 2. Module Internal Package Structure

Following SAVDOCRM's Clean Architecture & DDD modular-monolith conventions (`backend-architecture.md` and `ArchitectureTest`), `com.savdocrm.modules.inventory` uses four strict internal layers:

```text
com.savdocrm.modules.inventory/
├── domain/
│   ├── model/              # Aggregates, entities, value objects, domain enums
│   ├── service/            # Pure domain services (WacCostCalculator, StockInvariantValidator, LedgerPostingService)
│   └── exception/          # Inventory domain exceptions mapped to RFC 7807 ProblemDetail
├── application/
│   ├── facade/             # Public cross-module interfaces & DTOs (StockQueryFacade, StockMovementFacade, StockReservationFacade, WarehouseQueryFacade)
│   ├── service/            # Application use cases & transaction boundaries (@Transactional + Lock ordering)
│   ├── job/                # Scheduled domain maintenance (reservation TTL expiry)
│   └── mapper/             # MapStruct mappers (Domain <-> Application/Web DTOs)
├── infrastructure/
│   ├── persistence/        # Spring Data JPA repositories extending TenantAwareRepository
│   └── adapter/            # Facade adapters wrapping catalog/tenancy/identity queries
└── web/
    ├── controller/         # @RestController endpoints under /api/v1/inventory/**
    └── dto/                # Request/Response payloads with Jakarta Bean Validation
```

**Dependency Direction Rule (Enforced by ArchUnit)**:
- `domain` has zero dependencies on Spring Web, Spring Data JPA, or external modules (`catalog`, `sales`, `purchases`).
- `application` calls `catalog` and `tenancy` **only** via their public `application.facade` packages (`ProductQueryFacade`, `BranchQueryFacade`/read service).
- External modules (`sales`, `purchases`, `analytics`) may only import `inventory.application.facade` and `inventory.domain.model` enums/value objects exposed in public facade signatures — never `inventory.infrastructure` or `inventory.web`.

---

## 3. Domain Model & Aggregate Boundaries

### 3.1 Warehouse & Location Topology (`ADR-0011`, `ADR-0012`)

#### `Warehouse` (Aggregate Root — `warehouses`)
Represents a physical or logical inventory holding facility within a tenant.

| Attribute / Concept | Type / Constraints | Domain Invariant |
|---|---|---|
| `id`, `tenantId`, audit, `version` | `BaseTenantEntity` | Immutable `tenantId`; optimistic lock `@Version` |
| `code` | `VARCHAR(32)`, NOT NULL | Unique per tenant (`uq_warehouses_tenant_code`), normalized uppercase `[A-Z0-9_-]` |
| `name` | `VARCHAR(120)`, NOT NULL | Unique case-insensitively per tenant or required human-readable label |
| `warehouseType` | `WarehouseType` (`RETAIL_STORE`, `CENTRAL_HUB`, `TRANSIT_HUB`, `RETURNS_CENTER`) | Immutable or restricted once stock movements exist |
| `branchId` | `UUID`, nullable | Nullable to support `CENTRAL_HUB` facilities not tied to a single store branch; when present, validated via composite FK `(tenant_id, branch_id) -> branches(tenant_id, id)` |
| `isDefaultForBranch` | `BOOLEAN`, NOT NULL, default `false` | Can only be `true` when `branchId != null`; partial unique index enforces at most one active default warehouse per branch |
| `status` | `WarehouseStatus` (`ACTIVE`, `INACTIVE`, `ARCHIVED`) | Transitions governed strictly by stock & open-document invariants |
| `allowNegativeStock` | `BOOLEAN`, NOT NULL, default `false` | MVP enforces `false` on all write paths; column reserved for future controlled policy configuration (`ADR-0013`) |
| `address`, `phone`, `notes` | Optional metadata fields | Facility operational metadata |

**Warehouse Lifecycle & Invariants**:
1. **Multi-warehouse per branch**: Multiple warehouses may reference the same `branch_id` (e.g., store backroom + overflow annex).
2. **Central unassigned warehouse**: `branchId = null` is valid (typically `CENTRAL_HUB`); access to unassigned central warehouses is granted **only** via `TENANT`-scoped roles or explicit `WAREHOUSE`-scoped role assignments (`06-inventory-security.md`).
3. **Default Location Auto-Provisioning**: Creating a `Warehouse` atomically creates one active default `StockLocation` (`code = "DEFAULT"`, `locationType = STORAGE`, `isDefault = true`).
4. **Deactivation / Archival Guard**:
   - A `Warehouse` cannot transition to `INACTIVE` or `ARCHIVED` while any `StockBalance` has `quantity_on_hand > 0`, `quantity_reserved > 0`, or any `WarehouseStockSummary` has `quantity_in_transit > 0`, or while open non-terminal operational documents (`DRAFT`, `REQUESTED`, `APPROVED`, `DISPATCHED`, `PARTIALLY_RECEIVED`, `COUNTING`) reference it.
   - `INACTIVE` warehouses reject new receipts, reservations, positive adjustments, and outgoing/incoming transfers, while still allowing historical read queries, ledger inspection, and emergency reconciliation if re-activated or explicitly settled.
5. **No Physical Delete**: Warehouses are never hard-deleted after creation if referenced by stock balances, locations, RBAC assignments, or ledger entries (`ON DELETE RESTRICT`). Use `ARCHIVED` status.

#### `StockLocation` (Aggregate Root within Warehouse Scope — `stock_locations`)
Represents a concrete sub-warehouse storage bin/zone without deep recursive tree overhead (`ADR-0012`).

| Attribute | Type / Constraints | Domain Invariant |
|---|---|---|
| `id`, `tenantId`, audit, `version` | `BaseTenantEntity` | Composite parent FK `(tenant_id, warehouse_id) -> warehouses(tenant_id, id)` |
| `warehouseId` | `UUID`, NOT NULL | Owning warehouse |
| `code` | `VARCHAR(48)`, NOT NULL | Unique per `(tenant_id, warehouse_id, code)` |
| `name` | `VARCHAR(120)`, NOT NULL | Human-readable label (e.g., `Main Shelf A1`, `Damaged Cage`) |
| `locationType` | `LocationType` (`STORAGE`, `PICKING`, `RECEIVING_DOCK`, `DAMAGED`, `QUARANTINE`) | Determines whether stock contributes to sellable availability |
| `status` | `LocationStatus` (`ACTIVE`, `INACTIVE`) | Cannot deactivate if `quantity_on_hand > 0` or `quantity_reserved > 0` or if `isDefault == true` |
| `isDefault` | `BOOLEAN`, NOT NULL | At most one `isDefault = true` per warehouse (partial unique index); must have `locationType == STORAGE` |
| `zone`, `aisle`, `rack`, `bin` | Optional `VARCHAR(32)` | Structured flat locator metadata for retail/hub picking & cycle counts |

**Location Pool Rules (`LocationType.stockPool()`)**:
- `STORAGE` and `PICKING` -> **Sellable Pool**: Contributes to `quantity_sellable` and `quantity_available` (`sellable - reserved`). Only locations in the Sellable Pool may hold active `InventoryReservation`s or fulfill customer sales.
- `RECEIVING_DOCK` -> **Staging Pool**: Physical stock held prior to internal putaway; contributes to `quantity_on_hand` and `quantity_staging`, **not** `quantity_available`.
- `DAMAGED` -> **Damaged Pool**: Non-sellable physical stock pending write-off, repair, or return to supplier; contributes to `quantity_damaged`.
- `QUARANTINE` -> **Quarantined Pool**: Hold-for-inspection stock (recall, expiry check, count discrepancy hold); contributes to `quantity_quarantined`.

> **Design Note**: All stock movements reference a concrete `location_id`. Callers using simplified warehouse-level APIs omit `location_id` and automatically resolve to the warehouse's active default `STORAGE` location (`isDefault = true`). This keeps simple store POS/receiving flows zero-friction while supporting full bin-level putaway, damage segregation, and cycle counting (`ADR-0012`).

---

### 3.2 Immutable Inventory Ledger (`ADR-0013`)

The ledger is strictly **append-only**. Posted `InventoryTransaction` and `InventoryTransactionLine` rows are **never updated or deleted** (`ON DELETE RESTRICT` across all FKs). Corrections require an offsetting compensating transaction linking `reversed_transaction_id`.

#### `InventoryTransaction` (Aggregate Root — `inventory_transactions`)
- `id`, `tenantId`, `createdAt`, `createdBy` (immutable append-only audit fields; `version = 0`)
- `transactionNumber`: unique per tenant (`INV-TXN-YYYYMMDD-NNNNNN`)
- `movementType`: `InventoryMovementType`
- `warehouseId`: primary warehouse of this ledger entry (note: transfers emit two distinct transactions — `TRANSFER_OUT` at source warehouse and `TRANSFER_IN` at destination warehouse — correlated by `reference_id = transfer.id`)
- `referenceType`: `InventoryReferenceType` (`STOCK_RECEIPT`, `PURCHASE_ORDER`, `SALES_ORDER`, `SALES_RETURN`, `PURCHASE_RETURN`, `STOCK_ADJUSTMENT`, `STOCK_COUNT`, `STOCK_TRANSFER`, `INTERNAL_MOVE`, `REVERSAL`, `OPENING_BALANCE`)
- `referenceId`: `UUID` (ID of originating operational or external document)
- `reversedTransactionId`: nullable `UUID` (self-referencing composite FK when `movementType == REVERSAL`)
- `idempotencyKey`: nullable `VARCHAR(128)`
- `occurredAt`: `Instant` (business posting timestamp)
- `postedBy`: `UUID`
- `notes`: `TEXT`

#### `InventoryTransactionLine` (Entity within `InventoryTransaction` — `inventory_transaction_lines`)
- `id`, `tenantId`, `transactionId`, `lineNumber`
- `warehouseId`, `locationId`, `variantId`
- `quantityDelta` (`NUMERIC(12,3)`, non-zero: positive for inflow, negative for outflow)
- `quantityBefore` (`NUMERIC(12,3)` — location `quantity_on_hand` immediately before posting)
- `quantityAfter` (`NUMERIC(12,3)` — `quantityBefore + quantityDelta >= 0`)
- `unitCost` (`NUMERIC(15,4)` — unit cost applied to this movement)
- `totalCost` (`NUMERIC(18,4)` — signed cost impact `ROUND(quantityDelta * unitCost, 4)`)
- `wacBefore` (`NUMERIC(15,4)`) & `wacAfter` (`NUMERIC(15,4)`) — warehouse-variant WAC snapshot at posting time

#### Canonical `InventoryMovementType` Semantics Matrix

| Movement Type | Direction (`quantityDelta`) | Physical (`on_hand`) Effect | Available Effect | Reserved Effect | In-Transit Effect (Dest WH) | Damaged / Quarantined Effect | WAC Effect | External Reference | Reversible via Compensating Entry? |
|---|---|---|---|---|---|---|---|---|---|
| `OPENING_BALANCE` | `+` | `+qty` | `+qty` (if sellable loc) | `0` | `0` | `+qty` if non-sellable loc | Recalculates WAC using supplied unit cost | Initial import / receipt | Yes (`REVERSAL` / `ADJUSTMENT_OUT`) |
| `PURCHASE_RECEIPT` | `+` | `+qty` | `+qty` (if sellable loc) or `0` (if `RECEIVING_DOCK`) | `0` | `0` | `0` | **Recalculates WAC** using landed/receipt unit cost | `STOCK_RECEIPT` / `PURCHASE_ORDER` | Yes (`PURCHASE_RETURN` or `REVERSAL`) |
| `PURCHASE_RETURN` | `-` | `-qty` | `-qty` (if from sellable loc) | `0` | `0` | `-qty` if from `DAMAGED`/`QUARANTINE` | Revalues at current WAC or original cost if supplied (`totalValue -= totalCost`) | `PURCHASE_RETURN` / `STOCK_ADJUSTMENT` | Yes (`REVERSAL`) |
| `SALE_ISSUE` | `-` | `-qty` | `-qty` (net `0` change to `available` when consuming an existing reservation, `-qty` when unreserved) | `-consumedReservedQty` | `0` | `0` | Preserves WAC (`totalValue -= qty * currentWac`) | `SALES_ORDER` | Yes (`SALE_RETURN` or `REVERSAL`) |
| `SALE_RETURN` | `+` | `+qty` | `+qty` (if returned to sellable loc) | `0` | `0` | `+qty` if returned to `DAMAGED`/`QUARANTINE` | Uses original sale unit cost (or current WAC fallback); recalculates WAC | `SALES_RETURN` | Yes (`REVERSAL`) |
| `ADJUSTMENT_IN` | `+` | `+qty` | `+qty` (if sellable loc) | `0` | `0` | `+qty` if non-sellable loc | Preserves WAC if `unitCost` omitted; recalculates WAC if explicit positive `unitCost` supplied | `STOCK_ADJUSTMENT` / `STOCK_COUNT` | Yes (`ADJUSTMENT_OUT` or `REVERSAL`) |
| `ADJUSTMENT_OUT` | `-` | `-qty` | `-qty` (if sellable loc) | `0` | `0` | `-qty` if non-sellable loc | Preserves WAC (`totalValue -= qty * currentWac`) | `STOCK_ADJUSTMENT` / `STOCK_COUNT` | Yes (`ADJUSTMENT_IN` or `REVERSAL`) |
| `WRITE_OFF` | `-` | `-qty` | `-qty` (if sellable) | `0` | `0` | `-qty` (typically from `DAMAGED`/`QUARANTINE`) | Preserves WAC (`totalValue -= qty * currentWac`) | `STOCK_ADJUSTMENT` | Yes (`REVERSAL` prior to period close) |
| `TRANSFER_OUT` | `-` | `-qty` (source WH) | `-qty` (source WH) | `0` (requires unreserved source stock) | `+qty` (destination WH) | `0` | Preserves source WAC; snapshots `dispatchUnitCost` onto transfer line | `STOCK_TRANSFER` | No direct edit once dispatched; short/return handled via transfer receipt/variance or return transfer |
| `TRANSFER_IN` | `+` | `+qty` (dest WH) | `+qty` (if dest loc sellable) | `0` | `-dispatchedResolvedQty` (dest WH) | `+qty` (if received directly to `DAMAGED`) | **Recalculates dest WAC** blending dest stock with `dispatchUnitCost` | `STOCK_TRANSFER` | Yes via return transfer / adjustment |
| `INTERNAL_MOVE_OUT` | `-` | `-qty` (source loc) | `-qty` if leaving sellable loc | `0` (cannot move reserved qty) | `0` | `-qty` if leaving `DAMAGED`/`QUARANTINE` | Preserves warehouse WAC (`totalValue` unchanged) | `INTERNAL_MOVE` (putaway / quarantine / damage seg.) | Yes (reverse `INTERNAL_MOVE`) |
| `INTERNAL_MOVE_IN` | `+` | `+qty` (dest loc) | `+qty` if entering sellable loc | `0` | `0` | `+qty` if entering `DAMAGED`/`QUARANTINE` | Preserves warehouse WAC (`totalValue` unchanged) | `INTERNAL_MOVE` | Yes (reverse `INTERNAL_MOVE`) |
| `REVERSAL` | `+/-` (negation of target) | Negates target lines | Negates target impact | `0` (reservations are not auto-recreated unless specified) | Negates if unreceived transfer | Negates target impact | Restores/recalculates WAC consistently | `REVERSAL` referencing `reversedTransactionId` | Compensating only (cannot reverse a `REVERSAL` blindly without chain audit) |

> **Critical Reservation Rule**: Creating, updating, expiring, or releasing an `InventoryReservation` **does NOT** emit `InventoryTransaction` rows because physical stock has not moved. Only physical fulfillment (`SALE_ISSUE`) emits a ledger transaction while atomically decrementing `quantity_reserved`.

---

### 3.3 Materialized Stock Balances (`StockBalance` & `WarehouseStockSummary`)

To achieve both **location-level accuracy** and **O(1) POS/warehouse availability lookups**, Inventory maintains two synchronized materialized projections inside the same ACID posting transaction (`ADR-0013`):

#### `StockBalance` (`inventory_balances` — Authoritative Locking Row)
- **Key**: `(tenant_id, warehouse_id, location_id, variant_id)`
- **Columns**:
  - `quantityOnHand` (`NUMERIC(12,3)`, constraint `>= 0`)
  - `quantityReserved` (`NUMERIC(12,3)`, constraint `>= 0`)
  - `version` (`@Version BIGINT`)
- **Location-Level Invariants**:
  - `quantityAvailable` (computed property) = `location.isSellable() ? quantityOnHand - quantityReserved : 0`.
  - When `location.isSellable()`: `quantityReserved <= quantityOnHand`.
  - When `!location.isSellable()` (`DAMAGED`, `QUARANTINE`, `RECEIVING_DOCK`): `quantityReserved == 0`.
  - Transferring stock out of a location or adjusting out requires `newQuantityOnHand >= quantityReserved` (reserved stock cannot be silently eaten by unrelated adjustments or transfers!).

#### `WarehouseStockSummary` (`inventory_warehouse_stocks` — Warehouse Rollup Projection)
- **Key**: `(tenant_id, warehouse_id, variant_id)`
- **Columns** (all `NUMERIC(12,3) >= 0`, protected by `@Version`):
  - `quantityOnHand`: total physical stock across all locations (`quantitySellable + quantityStaging + quantityDamaged + quantityQuarantined`)
  - `quantitySellable`: sum of `quantityOnHand` across `STORAGE` and `PICKING` locations
  - `quantityReserved`: sum of `quantityReserved` across `STORAGE` and `PICKING` locations
  - `quantityAvailable`: derived invariant `quantitySellable - quantityReserved` (stored or database-generated `GENERATED ALWAYS AS (quantity_sellable - quantity_reserved) STORED`)
  - `quantityStaging`: sum of `quantityOnHand` across `RECEIVING_DOCK` locations
  - `quantityDamaged`: sum of `quantityOnHand` across `DAMAGED` locations
  - `quantityQuarantined`: sum of `quantityOnHand` across `QUARANTINE` locations
  - `quantityInTransit`: inbound stock currently dispatched to this `warehouse_id` via open `StockTransfer`s (`DISPATCHED` or `PARTIALLY_RECEIVED`) and not yet received or closed as variance.

**Ledger Reconciliation Guarantee**:
Both `inventory_balances` and `inventory_warehouse_stocks` are deterministic projections of `inventory_transaction_lines`, open `inventory_reservations`, and open `stock_transfers`. An administrative reconciliation check/rebuild service recomputes expected location `quantity_on_hand` by summing `inventory_transaction_lines.quantity_delta`, expected `quantity_reserved` from active `inventory_reservation_lines`, and expected `quantity_in_transit` from open `stock_transfer_lines`, verifying zero drift.

---

### 3.4 Product Catalog Status & UOM Rules (`ProductQueryFacade`)

Every stock mutation validates all referenced `variantId`s via `ProductQueryFacade`:

| Catalog State | New Receipt (`PURCHASE_RECEIPT`) | Positive Adj / Opening Balance | Sale Issue / New Reservation | Transfer Dispatch | Transfer Receipt (Already In-Transit) | Return (`SALE_RETURN` / `PURCHASE_RETURN`) | Negative Adj / Write-Off / Count Reconciliation | Historical Reads & Ledger |
|---|---|---|---|---|---|---|---|---|
| **Unknown `variantId` / wrong tenant** | Reject `404` | Reject `404` | Reject `404` | Reject `404` | Reject `404` | Reject `404` | Reject `404` | N/A |
| `ProductStatus.DRAFT` | Reject `409` | Reject `409` | Reject `409` | Reject `409` | Reject `409` | Reject `409` | Reject `409` | Reject / none |
| `ProductStatus.ACTIVE` & `variantActive=true` | Allowed | Allowed | Allowed | Allowed | Allowed | Allowed | Allowed | Allowed |
| `ProductStatus.ACTIVE` & `variantActive=false` | Reject `409` | Reject `409` | Reject `409` | Reject `409` (cannot start new transfer) | **Allowed** (must close already in-transit stock) | **Allowed** (for existing orders) | **Allowed** (must clear/reconcile remaining physical stock) | Allowed |
| `ProductStatus.ARCHIVED` | Reject `409` | Reject `409` | Reject `409` | Reject `409` | **Allowed** (close in-transit stock) | **Allowed** (customer returns of sold items) | **Allowed** (clear/count/write-off remaining stock) | Allowed |

**Unit of Measure (UOM) Precision Validation**:
- Every quantity (`> 0`) must match `VariantCatalogDto`:
  - If `!variant.allowDecimal()`, `quantity.stripTrailingZeros().scale() <= 0` (must be whole integer units).
  - If `variant.allowDecimal()`, `quantity.stripTrailingZeros().scale() <= variant.decimalPrecision()` (capped at `<= 3` DB scale).
  - Violations fail fast with HTTP `400 Bad Request` / `ProblemDetail`.

---

### 3.5 Stock Receiving (`StockReceipt` & `StockReceiptLine`)

Models physical intake of goods into a warehouse without coupling Inventory to Purchases entities.

- **Status Lifecycle**:
  - `DRAFT` -> `POSTED` (validates warehouse/location active, catalog status & UOM precision, non-negative `unitCost`, writes `PURCHASE_RECEIPT` / `OPENING_BALANCE` ledger entry, increments `StockBalance`, recalculates `InventoryCost` WAC).
  - `DRAFT` -> `CANCELLED` (soft terminal cancellation, no ledger impact).
  - `POSTED` documents are **immutable**. Any correction requires an explicit reversal transaction or `PURCHASE_RETURN` / `STOCK_ADJUSTMENT`.
- **Fields**: `receiptNumber` (`RCP-YYYYMMDD-NNNNNN`), `warehouseId`, `sourceType` (`PURCHASE_ORDER`, `MANUAL`, `OPENING_BALANCE`, `RETURN`), `sourceReferenceId` (nullable `UUID`), `receivedAt`, `status`, `notes`.
- **Line Fields**: `variantId`, `locationId` (defaults to warehouse default `STORAGE` location; may target `RECEIVING_DOCK` for two-step putaway), `quantity` (`> 0`), `unitCost` (`>= 0`, `NUMERIC(15,4)` — falls back to `VariantCatalogDto.referenceCostPrice` only if explicitly requested on manual non-PO receipt, otherwise required).

---

### 3.6 Stock Counting & Stock Adjustments

Cycle/physical counting and stock adjustments are modeled as **distinct aggregates** (`StockCount` vs `StockAdjustment`) so operational counting workflows remain clean and maker-checker auditable:

#### `StockCount` (`stock_counts` & `stock_count_lines`)
- **Scope**: `FULL_WAREHOUSE` or `PARTIAL_SCOPE` (specific `locationId`s or specific `variantId`s).
- **Lifecycle**: `DRAFT` -> `IN_PROGRESS` -> `PENDING_APPROVAL` -> `APPROVED_AND_POSTED` | `CANCELLED`.
- **Snapshot-Without-Freeze Concurrency Design**:
  1. When starting (`DRAFT -> IN_PROGRESS`), `StockCountLine` captures `systemQuantitySnapshot` (`quantity_on_hand` at start time) and `snapshotAt`. Store/POS operations are **NOT** frozen warehouse-wide.
  2. Operators record `countedQuantity` per line (must satisfy UOM precision). Variance preview = `countedQuantity - systemQuantitySnapshot`.
  3. On `APPROVE_AND_POST` (`APPROVED_AND_POSTED`): inside an ACID transaction acquiring canonical pessimistic locks on each `(warehouse_id, location_id, variant_id)`, the system inspects **current** `quantityOnHand`:
     - **Case A (No concurrent movement since `snapshotAt`)**: Delta = `countedQuantity - systemQuantitySnapshot`.
     - **Case B (Concurrent sale/receipt occurred while counting)**: Default strict mode raises `409 CONFLICT` (`COUNT_CONCURRENT_MOVEMENT`) reporting affected lines so the counter can either `RECOMPUTE` (`countedQuantity` reconciled against current system balance or re-counted) OR authorize with `reconciliationPolicy = APPLY_VARIANCE_DELTA` (applies `countedQuantity - systemQuantitySnapshot` only if resulting balance remains `>= quantityReserved`, otherwise rejects!).
  4. Non-zero lines generate a linked `StockAdjustment` (reason `CYCLE_COUNT`) and an immutable `InventoryTransaction` (`ADJUSTMENT_IN` / `ADJUSTMENT_OUT`) per variance direction. Zero-variance lines record `approved` with zero ledger write.

#### `StockAdjustment` (`stock_adjustments` & `stock_adjustment_lines`)
- **Lifecycle**: `DRAFT` -> `PENDING_APPROVAL` -> `APPROVED` (posts ledger & balances atomically) | `REJECTED` | `CANCELLED`.
  - Direct create+post is permitted only for users holding **both** `inventory.adjustments.create` and `inventory.adjustments.approve` when single-step auto-submit is requested, preserving strict maker-checker separation when desired (`ADR-0016`).
- **Reasons (`AdjustmentReason`)**: `CYCLE_COUNT`, `DAMAGE`, `EXPIRY`, `THEFT_SHRINKAGE`, `FOUND_STOCK`, `QUALITY_HOLD`, `CORRECTION`, `OTHER`.
- **Fields per Line**: `locationId`, `variantId`, `adjustmentDirection` (`INCREASE`, `DECREASE`), `quantity` (`> 0`), `unitCost` (optional override for `INCREASE`; `DECREASE` / `WRITE_OFF` strictly uses current warehouse WAC), `targetMovementType` (`ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `WRITE_OFF`).
- **Validation**: `DECREASE` / `WRITE_OFF` must ensure `quantityOnHand - quantityReserved >= quantity` (`available` unreserved stock at that location cannot be violated!).

---

### 3.7 Inter-Warehouse Stock Transfers (`ADR-0014`)

Models physical shipment of stock between two distinct warehouses of the same tenant using explicit **two-step transit tracking**:

- **Status State Machine**:
  ```text
  DRAFT ──► REQUESTED ──► APPROVED ──► DISPATCHED ──► PARTIALLY_RECEIVED ──► RECEIVED
    │           │            │                                  │
    └───────────┴────────────┴──► CANCELLED                     └──► CLOSED_WITH_VARIANCE
  ```
- **Invariants**:
  1. `sourceWarehouseId != destinationWarehouseId`; both belong to the same `tenantId` and must be `ACTIVE`.
  2. **Dispatch (`APPROVED -> DISPATCHED`)**:
     - Acquires canonical pessimistic locks on source `(sourceWarehouseId, sourceLocationId, variantId)` and destination `WarehouseStockSummary(destinationWarehouseId, variantId)`.
     - Validates sufficient **unreserved** stock at source location (`quantityOnHand - quantityReserved >= dispatchedQty`).
     - Captures `unitCostSnapshot = sourceInventoryCost.unitCost` onto each `StockTransferLine`.
     - Emits `InventoryTransaction` (`TRANSFER_OUT`, negative delta) at source warehouse, decreases source `StockBalance.quantityOnHand` & `WarehouseStockSummary.quantitySellable`, and **increases destination `WarehouseStockSummary.quantityInTransit`**.
     - **Hard Rule**: Once `DISPATCHED`, a transfer **CANNOT** transition to `CANCELLED`. Reversing an erroneous shipment requires receiving it (or receiving to damage/short-variance and dispatching a return transfer).
  3. **Receipt (`DISPATCHED | PARTIALLY_RECEIVED -> PARTIALLY_RECEIVED | RECEIVED | CLOSED_WITH_VARIANCE`)**:
     - Supports partial receipts across multiple deliveries and line-level discrepancy classification:
       - `receivedGoodQty` -> credited to destination `STORAGE`/`PICKING` location via `TRANSFER_IN` ledger line (recalculates destination WAC using `unitCostSnapshot`).
       - `receivedDamagedQty` -> credited to destination `DAMAGED` location (auto-selected or specified) via `TRANSFER_IN` ledger line (isolated from `quantityAvailable`; included in `quantityDamaged` and WAC/costed accordingly).
       - `lostOrShortQty` -> decreases `quantityInTransit` at destination and records explicit shortage reason/resolution on the transfer line (`CLOSED_WITH_VARIANCE`), optionally triggering an audit discrepancy adjustment without losing track of the missing units!
     - Guarantees invariant: `line.receivedGoodQty + line.receivedDamagedQty + line.shortageQty <= line.dispatchedQty`. When all lines have `remainingInTransit == 0`, status transitions to `RECEIVED` (if `shortageQty == 0` across all lines) or `CLOSED_WITH_VARIANCE`.

---

### 3.8 Inventory Reservations (`ADR-0014`)

Models soft stock holds prior to physical fulfillment (e.g., pending online/POS orders, quoted bulk orders, layaway).

- **Aggregate**: `InventoryReservation` (`inventory_reservations`) + `InventoryReservationLine` (`inventory_reservation_lines`).
- **Status Lifecycle**: `ACTIVE` -> `PARTIALLY_CONSUMED` -> `CONSUMED` | `RELEASED` | `EXPIRED`.
- **Core Rules**:
  1. Reservations allocate strictly from **sellable locations** (`STORAGE` or `PICKING`).
  2. Creating a reservation increments `StockBalance.quantityReserved` and `WarehouseStockSummary.quantityReserved` (decreasing `quantityAvailable`), requiring `quantityAvailable >= requestedQty` under pessimistic lock.
  3. **No ledger entry is created on reserve, release, or expire.**
  4. **Consumption**: When `Sales` issues stock for a reserved order (`SALE_ISSUE`), `StockMovementFacade` validates the `reservationId`, atomically decrements both `quantityReserved` and `quantityOnHand` by the consumed quantity, updates `reservationLine.consumedQuantity`, transitions reservation status (`PARTIALLY_CONSUMED` / `CONSUMED`), and writes the `SALE_ISSUE` `InventoryTransaction`.
  5. **TTL Expiration**: Optional `expiresAt` (`Instant`) defaults per source policy (e.g., POS hold 15 min, sales quote 48h). A scheduled background job (`ReservationExpirationJob`) releases expired active reservations tenant-by-tenant via `TenantExecutionTemplate`, decrementing `quantityReserved` and freeing `quantityAvailable`.
  6. **Partial & Concurrent Fulfillment**: If an order partially ships, only the shipped quantity is consumed while remaining quantity stays `ACTIVE` (`PARTIALLY_CONSUMED`) until shipped or released.

---

### 3.9 Inventory Costing & Valuation (`WAC` — `ADR-0015`)

#### Chosen Method: Moving Weighted Average Cost (`WAC`) per `(tenant_id, warehouse_id, variant_id)`
- **Why WAC over FIFO for MVP**: Retail POS/CRM operations across multiple branches with frequent stock transfers, returns, and adjustments require deterministic, O(1) cost-of-goods-sold (`COGS`) calculation without maintaining multi-layer receipt queue fragmentation per bin or negative-layer ambiguities on rapid POS returns. FIFO/lot-layer costing can be introduced cleanly in a later phase via an `inventory_cost_layers` table because ledger lines already capture `unitCost` and `wacAfter`.

#### `InventoryCost` (`inventory_costs`)
- **Key**: `(tenant_id, warehouse_id, variant_id)`
- **Columns**:
  - `unitCost` (`NUMERIC(15,4)`, `>= 0`) — current weighted average unit cost
  - `totalValue` (`NUMERIC(18,4)`, `>= 0`) — current warehouse inventory valuation for this variant
  - `valuedQuantity` (`NUMERIC(12,3)`, `>= 0`) — synchronized physical quantity basis (`quantityOnHand`)
  - `lastCostUpdatedAt` (`Instant`), `version` (`@Version`)

#### Exact Valuation Formulas (`RoundingMode.HALF_UP`, scale `4`)

1. **Positive Inflow with Explicit Cost** (`PURCHASE_RECEIPT`, `OPENING_BALANCE`, `TRANSFER_IN`, costed `SALE_RETURN`, costed `ADJUSTMENT_IN`):
   $$\text{inflowValue} = \text{round}_4(\text{qty}_{\text{in}} \times \text{unitCost}_{\text{in}})$$
   $$\text{newTotalValue} = \text{prevTotalValue} + \text{inflowValue}$$
   $$\text{newQty} = \text{prevQty} + \text{qty}_{\text{in}}$$
   $$\text{newWac} = \begin{cases} \text{round}_4(\text{newTotalValue} / \text{newQty}) & \text{if } \text{newQty} > 0 \\ 0.0000 & \text{if } \text{newQty} = 0 \end{cases}$$
   *(Edge case: when receiving into a warehouse whose `prevQty == 0`, `newTotalValue = inflowValue` and `newWac = round_4(unitCost_in)`, preventing stale pre-zero WAC drift.)*

2. **Outflows** (`SALE_ISSUE`, `TRANSFER_OUT`, `ADJUSTMENT_OUT`, `WRITE_OFF`):
   - **Unit Cost Used**: Current `prevWac`.
   - **Value Deduction**:
     - If `newQty == 0` (full depletion): `outflowValue = prevTotalValue`, `newTotalValue = 0.0000` (absorbs any rounding residue so `totalValue` cleanly zeros out when stock reaches zero!).
     - Otherwise: `outflowValue = round_4(qty_out * prevWac)`, `newTotalValue = max(0.0000, prevTotalValue - outflowValue)`, and `unitCost` (`newWac`) remains `prevWac`.

3. **Purchase Return (`PURCHASE_RETURN`)**:
   - If original receipt cost matches or differs from current `WAC`: deduct `outflowValue = min(prevTotalValue, round_4(qty_out * returnUnitCost))`; if `newQty == 0`, reset `newTotalValue = 0` and `newWac = 0`, otherwise recalculate `newWac = round_4(newTotalValue / newQty)` so supplier invoice price variances do not permanently distort remaining inventory WAC.

4. **Customer Sale Return (`SALE_RETURN`)**:
   - Uses original `SALE_ISSUE` unit cost if supplied by `sales`, otherwise uses current warehouse `WAC` (or `VariantCatalogDto.referenceCostPrice` if current warehouse `WAC == 0`). Applies inflow WAC formula.

5. **Internal Location Moves (`INTERNAL_MOVE_OUT` / `INTERNAL_MOVE_IN`)**:
   - Zero impact on `(tenant_id, warehouse_id, variant_id)` total quantity or valuation; ledger lines record current `unitCost = prevWac` and `wacBefore == wacAfter`.

**Strict Decoupling from Finance**: Inventory stores only physical unit valuation snapshots (`InventoryCost`, `InventoryTransactionLine.unitCost`/`totalCost`). Inventory never creates general-ledger debit/credit journal rows, tax payables, or cashbox entries; `sales`, `purchases`, and `finance` read cost outputs synchronously or subscribe to committed domain events.
