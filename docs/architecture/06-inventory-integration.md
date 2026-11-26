# M06 — Inventory Management: Integration, Concurrency, Roadmap & Test Strategy

> **Status**: Integration & Execution Specification (M06.1 — Design Only)  
> **Scope**: Cross-module public facades, deadlock-free lock ordering, idempotency, background jobs, M06.1–M06.11 implementation roadmap, and verification strategy.

---

## 1. Cross-Module Dependency Architecture

SAVDOCRM enforces acyclic module dependencies via ArchUnit (`ArchitectureTest.java`):

```text
          ┌───────────────┐         ┌───────────────┐
          │     sales     │         │   purchases   │
          └───────┬───────┘         └───────┬───────┘
                  │                         │
                  ▼                         ▼
        ┌───────────────────────────────────────────┐
        │   inventory (application.facade only)     │
        └──┬───────────────────┬─────────────────┬──┘
           │                   │                 │
           ▼                   ▼                 ▼
  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
  │     catalog     │ │     tenancy     │ │    identity     │
  │ (ProductQuery-  │ │ (Branch reads)  │ │ (PermissionAuth-│
  │     Facade)     │ │                 │ │    Service)     │
  └─────────────────┘ └─────────────────┘ └─────────────────┘
```

**Zero Circular Dependencies**:
- `catalog`, `tenancy`, and `identity` do **not** depend on `inventory`.
- `sales`, `purchases`, `finance`, and `analytics` only communicate with `inventory` through its `application.facade` interfaces (or committed Spring application events in `inventory.application.facade.event`).
- No module outside `inventory` ever accesses `inventory.infrastructure` JPA repositories or mutates `inventory_balances`, `inventory_warehouse_stocks`, `inventory_transactions`, or `inventory_costs` directly.

---

## 2. Inbound Dependencies Consumed by Inventory

### 2.1 From `catalog` (`ProductQueryFacade`)
Inventory consumes the already-implemented `com.savdocrm.modules.catalog.application.facade.ProductQueryFacade`:
- `findVariantById(UUID variantId)`
- `findVariantsByIds(Set<UUID> variantIds)` (batch validation for multi-line documents to eliminate N+1 queries)
- `findActiveVariantByBarcode(String barcode)` & `findActiveVariantBySku(String sku)` (for fast scanner lookup in stock counts & transfers)

Every returned `VariantCatalogDto` supplies `productStatus`, `variantActive`, `sellable`, `allowDecimal`, `decimalPrecision`, and `referenceCostPrice`.

### 2.2 From `tenancy` & `identity`
- Validate optional `branchId` exists in `currentTenantId` when creating/updating a `Warehouse` (`BranchService` / branch read facade).
- Authorize every operation via `PermissionAuthorizationService` (`hasWarehousePermission`, `getPermittedWarehouseIds`, `hasBranchStockReadPermission`).

---

## 3. Outbound Public Facades Exposed by Inventory

All facade interfaces and DTOs reside in `com.savdocrm.modules.inventory.application.facade` and operate strictly within the active `TenantContextHolder`.

### 3.1 `WarehouseQueryFacade`
- `Optional<WarehouseSummaryDto> findWarehouseById(UUID warehouseId)`
- `Optional<WarehouseSummaryDto> findDefaultWarehouseForBranch(UUID branchId)` — used by `Sales` POS terminal checkout when the cashier works at a branch and needs the branch's default fulfillment warehouse.
- `List<WarehouseSummaryDto> findActiveWarehousesForBranch(UUID branchId)`

### 3.2 `StockQueryFacade`
- `Optional<WarehouseStockDto> getWarehouseStock(UUID warehouseId, UUID variantId)`
- `Map<UUID, WarehouseStockDto> getWarehouseStocks(UUID warehouseId, Set<UUID> variantIds)` — O(1) batch POS availability check
- `List<WarehouseStockDto> getVariantAvailabilityAcrossWarehouses(UUID variantId, Set<UUID> permittedWarehouseIds)`
- `boolean hasAvailableStock(UUID warehouseId, UUID variantId, BigDecimal requiredQty)`

*(DTO fields include `quantityOnHand`, `quantitySellable`, `quantityReserved`, `quantityAvailable`, `quantityInTransit`, `quantityDamaged`, `quantityQuarantined`, and `currentUnitCost` gated/used internally by costing modules.)*

### 3.3 `StockMovementFacade`
Executed synchronously **within the caller's ACID `@Transactional` boundary** (e.g., `Sales` order completion commits sale invoice, inventory deduction, and finance cashbox receipt atomically, or rolls back together):
- `StockMovementResultDto receiveFromPurchase(PurchaseIntakeCommand cmd)` — invoked by `Purchases` on PO receiving; posts `StockReceipt` + `PURCHASE_RECEIPT` ledger, updates WAC.
- `StockMovementResultDto returnToSupplier(SupplierReturnCommand cmd)` — posts `PURCHASE_RETURN` outflow + revalues/adjusts WAC.
- `SaleIssueResultDto issueForSale(SaleIssueCommand cmd)` — validates/consumes optional `reservationId`, locks `StockBalance`, posts `SALE_ISSUE` ledger, and returns `List<IssuedLineCostDto>` containing exact `unitCost` & `totalCost` (`COGS`) to `Sales`/`Finance`.
- `StockMovementResultDto acceptSaleReturn(SaleReturnCommand cmd)` — posts `SALE_RETURN` to sellable or `DAMAGED` location using supplied original sale cost or current WAC.

### 3.4 `StockReservationFacade`
- `ReservationDto createReservation(CreateReservationCommand cmd)` — holds sellable stock with explicit `expiresAt` (`ACTIVE`).
- `ReservationDto releaseReservation(UUID reservationId, String reason)` — releases remaining held stock (`RELEASED`).
- `ReservationDto releaseBySource(ReservationSourceType sourceType, UUID sourceReferenceId, String reason)` — releases holds when a quote/pending order is cancelled.

### 3.5 Committed Domain Events (`TransactionalEventListener(phase = AFTER_COMMIT)`)
Inventory publishes internal Spring events after commit for asynchronous analytics/alerts without creating synchronous module coupling:
- `StockMovementCommittedEvent(tenantId, warehouseId, transactionId, movementType, occurredAt, lineValuationSummaries)` -> consumed by `Analytics` (and optional `Finance` reconciliation bridges if decoupled).
- `LowStockThresholdCrossedEvent(tenantId, warehouseId, variantId, quantityAvailable, reorderPoint)` -> consumed by notification/alert pipelines.

---

## 4. Concurrency Control, Canonical Lock Ordering & Idempotency

Multi-tenant retail POS systems experience simultaneous checkouts, returns, transfers, and receipts touching the same SKU rows. Deadlocks occur if Transaction A locks `(WH1, SKU-B)` then `(WH1, SKU-A)` while Transaction B locks `(WH1, SKU-A)` then `(WH1, SKU-B)`.

### 4.1 Hybrid Locking Model
1. **Pessimistic Row Locks (`@Lock(LockModeType.PESSIMISTIC_WRITE)`)**: Mandatory whenever posting physical movements (`SALE_ISSUE`, `PURCHASE_RECEIPT`, `TRANSFER_DISPATCH`, `TRANSFER_RECEIVE`, `ADJUSTMENT_POST`, `INTERNAL_MOVE`, `COUNT_APPROVE`, `REVERSAL`) or mutating `quantityReserved` (`createReservation`, `releaseReservation`, `expireReservation`).
2. **Optimistic Locking (`@Version`)**: Enforced on operational documents (`Warehouse`, `StockLocation`, `StockReceipt`, `StockCount`, `StockAdjustment`, `StockTransfer`, `InventoryReservation`) and balance tables as a secondary safeguard against stale UI submissions (`409 Conflict`).

### 4.2 Strict Canonical Lock Acquisition Order (Deadlock Prevention)

Every multi-line inventory mutation service MUST sort requested keys in memory **before issuing any `SELECT ... FOR UPDATE`** and acquire locks in this exact hierarchy:

1. **Pre-Validation Outside Locks**: Load and validate `ProductQueryFacade` variants and idempotency record (`IN_PROGRESS` lock/insert).
2. **Balance Rows (`inventory_balances`)**: Ensure missing `(tenant_id, warehouse_id, location_id, variant_id)` zero rows are safely created (`INSERT ... ON CONFLICT DO NOTHING` before locking!), then lock all touched `inventory_balances` sorted strictly lexicographically by **`(warehouseId ASC, locationId ASC, variantId ASC)`**.
3. **Warehouse Summary Rows (`inventory_warehouse_stocks`)**: Ensure missing rows exist (`INSERT ... ON CONFLICT DO NOTHING`), then lock all touched `inventory_warehouse_stocks` sorted strictly by **`(warehouseId ASC, variantId ASC)`**.
4. **Valuation Rows (`inventory_costs`)**: Ensure missing rows exist, then lock all touched `inventory_costs` sorted strictly by **`(warehouseId ASC, variantId ASC)`**.
5. **Reservation Rows (`inventory_reservations`)**: If consuming/releasing a reservation, lock `inventory_reservations` and its lines ordered by `(reservationId ASC, id ASC)` **before or strictly in documented order**—specifically: **operational document (`StockTransfer`/`InventoryReservation`/`StockCount`) FIRST (sorted by `id ASC`), THEN `inventory_balances`, `inventory_warehouse_stocks`, `inventory_costs` in canonical order**.

> **Zero Deadlock Guarantee**: Because `Transfer` dispatch locks source `inventory_balances` then `(sourceWh, destWh)` summaries/costs sorted by `warehouseId ASC`, two opposing transfers (`WH1 -> WH2` and `WH2 -> WH1` touching the same SKU) ALWAYS lock the lower `warehouseId` first and never form a wait-for cycle!

### 4.3 `INSERT ... ON CONFLICT DO NOTHING` Initialization Pattern
When receiving a new SKU into a location or warehouse for the first time under concurrent requests, two transactions could both see no balance row and collide on `UNIQUE (tenant_id, warehouse_id, location_id, variant_id)`. M06 solves this via an idempotent native upsert (`ensureRowExists`) scoped by `tenantId`, followed immediately by `SELECT ... FOR UPDATE`.

### 4.4 Idempotency Execution Lifecycle (`idempotency_records`)
Using the shared `IdempotencyService`:
1. Compute `requestHash = SHA-256(canonicalRequestJson)`.
2. Attempt atomic insert `(tenant_id, idempotency_key, endpoint, request_hash, status='IN_PROGRESS', expires_at=now+24h)`:
   - Conflict found -> read existing record:
     - Mismatched `requestHash` -> `422 IDEMPOTENCY_PAYLOAD_MISMATCH`.
     - `COMPLETED` -> return stored `responseStatus` + `responseBody`.
     - `IN_PROGRESS` -> `409 IDEMPOTENCY_CONFLICT_IN_PROGRESS`.
     - `FAILED` (if marked retryable) -> update to `IN_PROGRESS` or return failure.
3. On successful transaction commit, update `status = 'COMPLETED'`, `response_status`, `response_body` in the **same transaction** as the stock ledger posting! If the stock transaction rolls back due to domain failure (e.g., `409 INSUFFICIENT_AVAILABLE_STOCK`), record non-retryable terminal error or allow clean retry depending on failure semantics (business rejections with identical payload replay safely or release `IN_PROGRESS` lock cleanly via rollback/failure handler).

---

## 5. Background Jobs & Maintenance Architecture

| Job | Schedule | Isolation Mechanism | Responsibilities |
|---|---|---|---|
| `ReservationExpirationJob` | Every 1–5 minutes | Queries distinct `tenantId`s having expired `ACTIVE`/`PARTIALLY_CONSUMED` rows (`expires_at < NOW()`), then executes per-tenant batches via `TenantExecutionTemplate.executeInTenant(tenantId, ...)` | Locks expired reservations + balance rows in canonical order, sets `released_quantity = reserved_quantity - consumed_quantity`, decrements `quantityReserved`, marks `EXPIRED`. Idempotent and concurrency-safe via `SKIP LOCKED` or pessimistic lock. |
| `IdempotencyCleanupJob` | Daily off-peak | Batch `DELETE` on `idempotency_records WHERE expires_at < NOW()` (bounded batch size, e.g., 5,000 rows per loop) | Prevents unbounded table growth without long table locks. |
| `InventoryReconciliationService` | Admin-triggered / nightly verification job | Per-tenant read-only verification via `TenantExecutionTemplate` | Re-aggregates `inventory_transaction_lines`, active reservations, and open transfers; alerts if any `StockBalance` or `WarehouseStockSummary` drifts (must report `0` mismatches). |

---

## 6. Audit & Traceability Standards

1. **Every Ledger Entry Captures**: `transactionNumber`, `movementType`, `referenceType`, `referenceId`, `occurredAt`, `postedBy`, `quantityBefore`, `quantityDelta`, `quantityAfter`, `unitCost`, `totalCost`, `wacBefore`, `wacAfter`, `idempotencyKey`.
2. **Lifecycle Actor Tracking**:
   - `StockAdjustment`: `createdBy`, `submittedBy`, `approvedBy`, `approvedAt`, `rejectionReason`.
   - `StockTransfer`: `createdBy`, `approvedBy`, `dispatchedBy`, `receivedBy`, `varianceReason`.
   - `StockCount`: `createdBy`, `startedAt`, `submittedAt`, `approvedBy`, `approvedAt`.
3. **No Silent Mutability**: Once posted, documents and ledger records cannot be edited or deleted; corrections require documented `REVERSAL` or `StockAdjustment`.

---

## 7. Phased M06 Implementation Roadmap (`M06.1`–`M06.11`)

| Sub-Milestone | Scope & Deliverables | Key Dependencies |
|---|---|---|
| **M06.1** *(Current)* | Complete Architecture, Domain Model, Logical Schema, Security, API Contracts, ADRs | M01–M05.6 |
| **M06.2** | Database Migrations (`v1.0.0/23+`): `warehouses`, `stock_locations`, `inventory_balances`, `inventory_warehouse_stocks`, `inventory_costs`, `inventory_transactions`, `inventory_transaction_lines`, document & reservation tables, shared `idempotency_records`, RBAC `WAREHOUSE` scope migration + 20 new permissions | M06.1 |
| **M06.3** | RBAC `WAREHOUSE` Scope Extension (`RoleScopeType.WAREHOUSE`, `PermissionAuthorizationService.hasWarehousePermission`, `getPermittedWarehouseIds`, migration & security tests) + Shared `IdempotencyService` | M06.2 |
| **M06.4** | Inventory Core Domain, Warehouses, Stock Locations, Dual Projections (`StockBalance` & `WarehouseStockSummary`), Ledger Core, WAC Cost Calculator, Internal Moves (`INTERNAL_MOVE`) | M06.3 |
| **M06.5** | Stock Receiving (`StockReceipt`) Lifecycle, `ProductQueryFacade` validation, UOM precision enforcement & Ledger/WAC Integration Tests | M06.4 |
| **M06.6** | Stock Counting (`StockCount` snapshot/reconcile) & Stock Adjustments (`StockAdjustment` approval workflow + `WRITE_OFF`) | M06.4 |
| **M06.7** | Inter-Warehouse Two-Step Stock Transfers (`StockTransfer` dispatch, in-transit tracking, partial/damaged/short receipt) + Canonical Deadlock Prevention Tests | M06.4 |
| **M06.8** | Inventory Reservations (`InventoryReservation` hold/consume/release + `ReservationExpirationJob`) & Public Cross-Module Facades (`StockQueryFacade`, `StockMovementFacade`, `StockReservationFacade`, `WarehouseQueryFacade`) | M06.4, M06.5 |
| **M06.9** | Inventory REST API Controllers (`/api/v1/inventory/**`), MapStruct Mappers, Validation, `Idempotency-Key` enforcement, ArchUnit & RBAC MockMvc Tests | M06.4–M06.8 |
| **M06.10** | Inventory Frontend UI (React 19, FSD slices: Warehouses/Locations, Stock Overview & Valuation, Ledger Explorer, Receipts, Counts, Adjustments, Transfers, Reservations) | M06.9 |
| **M06.11** | M06 Integration, Concurrency, Security & Quality Audit (multi-tenant isolation, `WAREHOUSE` RBAC penetration tests, Testcontainers concurrent race-condition tests, frontend/backend contract audit) | M06.10 |

---

## 8. Comprehensive Test Strategy (Target Verification Gates for M06.2–M06.11)

1. **Pure Domain Unit Tests (`JUnit 5`)**:
   - WAC calculation precision (`HALF_UP`), zero-depletion reset, return/reversal valuation recovery.
   - UOM integer vs fractional precision checks (`allowDecimal`, `decimalPrecision`).
   - State-machine guards on `Warehouse`, `StockReceipt`, `StockCount`, `StockAdjustment`, `StockTransfer`, and `InventoryReservation`.
   - Invariant checks: non-negative stock, reservation never exceeds sellable stock, non-sellable pool isolation.
2. **Database & PostgreSQL Testcontainers Integration Tests**:
   - Composite FK rejection tests (proving SQL-layer tenant mismatch fails even bypassing service validation).
   - Concurrent multi-thread tests (`ExecutorService` + `CountDownLatch`) firing parallel checkouts, opposing bidirectional `WH1 <-> WH2` transfers, concurrent reservations, and simultaneous SKU initialization — verifying **zero deadlocks**, **zero overselling (`available >= 0`)**, and **exact ledger-to-balance parity**.
   - `StockCount` concurrent movement detection (`409 COUNT_CONCURRENT_MOVEMENT`) and reconciliation testing.
   - `Idempotency-Key` replay, concurrent `IN_PROGRESS` conflict (`409`), and payload-mismatch rejection (`422`).
3. **Multi-Tenancy & RBAC Security Integration Tests (`MockMvc`)**:
   - Prove `BRANCH`-scoped user **cannot** mutate or read restricted warehouse ops without `WAREHOUSE` or `TENANT` scope.
   - Prove `WAREHOUSE(A)` user receives `403`/`404` on `Warehouse(B)` and cannot create `BRANCH`-inherited access to `CENTRAL_HUB` (`branchId = null`).
   - Verify `inventory.costs.read` hides/redacts WAC unit cost fields from users holding only `inventory.stock.read`.
   - Verify cross-tenant UUID requests return `404` and zero data leakage.
   - Verify existing M04 custom roles remain untouched after Liquibase M06 permission migration.
4. **ArchUnit Rules (`ArchitectureTest.java`)**:
   - Enforce `inventory` only accesses `catalog.application.facade`, `tenancy`, `identity`, `shared`; zero cycles with `sales`/`purchases`/`finance`.
5. **Frontend Verification (`Vitest` + React Testing Library + Contract Tests)**:
   - API response unwrapping (`ApiResponse<PageResponse<T>>`), RFC 7807 `ProblemDetail` mapping, tenant-switch & warehouse-switch TanStack Query cache isolation, and permission-gated action buttons.
