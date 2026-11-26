# M06 — Inventory Management: REST API Contracts

> **Status**: REST Contract Specification (M06.1 — Design Only; Not Yet Implemented)  
> **Base Path**: `/api/v1/inventory`  
> **Contract Alignment**: Strictly matches implemented M04/M05 response wrappers (`ApiResponse<T>`, `PageResponse<T>`) and RFC 7807 `ProblemDetail` (`GlobalExceptionHandler`), superseding legacy `api-conventions.md` examples (`ADR-0016`).

---

## 1. Common Protocol Conventions

### 1.1 Authentication, Tenant & Correlation Headers
- `Authorization: Bearer <jwt>` (required)
- `X-Tenant-Id: <uuid>` (required tenant context header, validated against authenticated `TenantMembership`)
- `Idempotency-Key: <string>` (up to 128 chars; **required** on all ledger-posting, stock-mutating, transfer-transition, and reservation-mutating `POST`/`PUT` endpoints listed below)
- `X-Correlation-Id` (optional trace header propagated into logs and ledger context)

### 1.2 Standard Response Envelopes

**Single Resource / Command Success (`200 OK` / `201 Created`)**:
```json
{
  "success": true,
  "data": { "...": "..." },
  "timestamp": "2026-11-26T08:00:00Z"
}
```

**Paginated Query Response (`200 OK` — `ApiResponse<PageResponse<T>>`)**:
```json
{
  "success": true,
  "data": {
    "items": [ { "...": "..." } ],
    "page": 0,
    "size": 20,
    "totalElements": 145,
    "totalPages": 8,
    "first": true,
    "last": false
  },
  "timestamp": "2026-11-26T08:00:00Z"
}
```

**Error Response (RFC 7807 `ProblemDetail`)**:
```json
{
  "type": "https://savdocrm.com/errors/inventory/insufficient-available-stock",
  "title": "Insufficient Available Stock",
  "status": 409,
  "detail": "Requested 15.000 units of variant SKU-001 at location DEFAULT, but only 8.000 unreserved units are available.",
  "instance": "/api/v1/inventory/transfers/3a.../dispatch",
  "errorCode": "INSUFFICIENT_AVAILABLE_STOCK",
  "warehouseId": "...",
  "variantId": "...",
  "requested": "15.000",
  "available": "8.000",
  "timestamp": "2026-11-26T08:00:00Z"
}
```
Validation failures (`400 Bad Request`) include `"errors": { "field": "message" }` matching `GlobalExceptionHandler`.

### 1.3 `Idempotency-Key` Behavior Contract
1. Key scope = `(tenant_id, idempotency_key, canonical_endpoint)`.
2. Missing `Idempotency-Key` on endpoints marked **Required** returns `400 Bad Request` (`MISSING_IDEMPOTENCY_KEY`).
3. First request inserts `idempotency_records(status = 'IN_PROGRESS', request_hash = SHA256(body))`:
   - If another concurrent request arrives while `IN_PROGRESS`: returns `409 Conflict` (`IDEMPOTENCY_CONFLICT_IN_PROGRESS`).
   - If key already `COMPLETED` and `request_hash` matches: returns cached `responseStatus` + `responseBody` without re-executing stock/ledger changes.
   - If key exists with a **different** `request_hash`: returns `422 Unprocessable Entity` (`IDEMPOTENCY_PAYLOAD_MISMATCH`).
   - If original request failed with transient system error or rollback (`FAILED`): allows cleanup/retry semantics or returns failure consistently as configured per error class.

---

## 2. Endpoint Reference Catalog

### 2.1 Warehouses & Stock Locations

| Method & Path | Permission & Scope | Idempotency | Purpose & Query Filters |
|---|---|---|---|
| `GET /api/v1/inventory/warehouses` | `inventory.warehouses.read` (filtered to permitted warehouses unless `TENANT`) | N/A | Filters: `branchId`, `status`, `warehouseType`, `q`, `page`, `size`, `sort` |
| `POST /api/v1/inventory/warehouses` | `inventory.warehouses.manage` (`TENANT` scope) | Optional | Create warehouse + auto-create default `STORAGE` location |
| `GET /api/v1/inventory/warehouses/{warehouseId}` | `inventory.warehouses.read` (WH or `TENANT`) | N/A | Fetch warehouse details + location summary |
| `PATCH /api/v1/inventory/warehouses/{warehouseId}` | `inventory.warehouses.manage` (`TENANT` or WH) | N/A | Update `name`, `branchId`, `isDefaultForBranch`, `address`, `phone`, `notes` (requires `version`) |
| `POST /api/v1/inventory/warehouses/{warehouseId}/status` | `inventory.warehouses.manage` | Optional | Transition `ACTIVE` <-> `INACTIVE` or `ARCHIVED` (checks zero stock/in-transit/open docs) |
| `GET /api/v1/inventory/warehouses/{warehouseId}/locations` | `inventory.warehouses.read` (WH) | N/A | Filters: `locationType`, `status`, `q`, `page`, `size` |
| `POST /api/v1/inventory/warehouses/{warehouseId}/locations` | `inventory.warehouses.manage` (WH) | Optional | Create location (`STORAGE`, `PICKING`, `RECEIVING_DOCK`, `DAMAGED`, `QUARANTINE`) |
| `PATCH /api/v1/inventory/warehouses/{warehouseId}/locations/{locationId}` | `inventory.warehouses.manage` (WH) | N/A | Update name, locator fields (`zone/aisle/rack/bin`), `status`, or default flag |

### 2.2 Stock Balances, Valuation & Internal Location Moves

| Method & Path | Permission & Scope | Idempotency | Purpose & Query Filters |
|---|---|---|---|
| `GET /api/v1/inventory/stock` | `inventory.stock.read` (WH or `BRANCH` branch-WH read) | N/A | Paginated `WarehouseStockSummary` view; filters: `warehouseId`, `branchId`, `variantId`, `productId`, `lowStockOnly`, `q`. Cost fields (`unitCost`, `totalValue`) populated **only** if caller also holds `inventory.costs.read`, otherwise `null`! |
| `GET /api/v1/inventory/stock/locations` | `inventory.stock.read` (WH) | N/A | Location-level `StockBalance` breakdown (`warehouseId`, `locationId`, `locationType`, `variantId`) |
| `GET /api/v1/inventory/stock/variants/{variantId}` | `inventory.stock.read` | N/A | Cross-warehouse stock availability breakdown for one SKU/variant across caller's permitted warehouses |
| `GET /api/v1/inventory/costs` | `inventory.costs.read` (WH or `TENANT`) | N/A | Paginated WAC valuation report (`warehouseId`, `variantId`, `q`) |
| `POST /api/v1/inventory/moves` | `inventory.moves.create` (WH) | **Required** | Atomic internal move (`INTERNAL_MOVE_OUT` + `INTERNAL_MOVE_IN`) within the same warehouse (e.g., `RECEIVING_DOCK` -> `STORAGE`, `STORAGE` -> `DAMAGED`, `STORAGE` -> `QUARANTINE`) |

### 2.3 Immutable Stock Ledger & Compensating Reversals

| Method & Path | Permission & Scope | Idempotency | Purpose & Query Filters |
|---|---|---|---|
| `GET /api/v1/inventory/ledger` | `inventory.ledger.read` (WH) | N/A | Paginated `InventoryTransaction` audit trail; filters: `warehouseId`, `variantId`, `movementType`, `referenceType`, `referenceId`, `fromDate`, `toDate`, `page`, `size` |
| `GET /api/v1/inventory/ledger/{transactionId}` | `inventory.ledger.read` (WH of txn) | N/A | Full header + immutable `InventoryTransactionLine` items (unit cost fields omitted unless `inventory.costs.read`) |
| `POST /api/v1/inventory/ledger/{transactionId}/reverse` | `inventory.adjustments.approve` (WH) | **Required** | Posts compensating `REVERSAL` transaction referencing `reversedTransactionId` (rejects already-reversed or unsupported terminal transfer reversals) |

### 2.4 Stock Receipts

| Method & Path | Permission & Scope | Idempotency | Purpose |
|---|---|---|---|
| `GET /api/v1/inventory/receipts` | `inventory.receipts.read` (WH) | N/A | List receipts; filters: `warehouseId`, `status`, `sourceType`, `sourceReferenceId`, `fromDate`, `toDate` |
| `POST /api/v1/inventory/receipts` | `inventory.receipts.create` (WH) | Optional | Create `DRAFT` receipt with lines |
| `GET /api/v1/inventory/receipts/{receiptId}` | `inventory.receipts.read` (WH) | N/A | Get receipt header + lines |
| `PUT /api/v1/inventory/receipts/{receiptId}` | `inventory.receipts.create` (WH) | N/A | Replace lines / metadata while `DRAFT` (`version` required) |
| `POST /api/v1/inventory/receipts/{receiptId}/post` | `inventory.receipts.post` (WH) | **Required** | Post receipt -> creates `PURCHASE_RECEIPT` / `OPENING_BALANCE` ledger, updates balances & WAC |
| `POST /api/v1/inventory/receipts/{receiptId}/cancel` | `inventory.receipts.cancel` (WH) | **Required** | Cancel `DRAFT` receipt (`409` if already `POSTED`) |

### 2.5 Stock Counts

| Method & Path | Permission & Scope | Idempotency | Purpose |
|---|---|---|---|
| `GET /api/v1/inventory/counts` | `inventory.counts.read` (WH) | N/A | List stock counts (`warehouseId`, `status`, `countScope`) |
| `POST /api/v1/inventory/counts` | `inventory.counts.manage` (WH) | Optional | Create `DRAFT` count definition (`FULL_WAREHOUSE` or `PARTIAL_SCOPE`) |
| `GET /api/v1/inventory/counts/{countId}` | `inventory.counts.read` (WH) | N/A | Fetch count header + lines (blind-count option omits snapshot if needed by UI) |
| `POST /api/v1/inventory/counts/{countId}/start` | `inventory.counts.manage` (WH) | **Required** | Transition `DRAFT -> IN_PROGRESS`, freeze `systemQuantitySnapshot` per targeted balance |
| `PUT /api/v1/inventory/counts/{countId}/lines` | `inventory.counts.manage` (WH) | N/A | Batch record `countedQuantity` values while `IN_PROGRESS` |
| `POST /api/v1/inventory/counts/{countId}/submit` | `inventory.counts.manage` (WH) | **Required** | Verify all lines counted -> transition `IN_PROGRESS -> PENDING_APPROVAL` |
| `POST /api/v1/inventory/counts/{countId}/approve` | `inventory.counts.approve` (WH) | **Required** | Reconcile & post variances -> generates `StockAdjustment` + `InventoryTransaction` |
| `POST /api/v1/inventory/counts/{countId}/cancel` | `inventory.counts.manage` (WH) | **Required** | Cancel pre-approval count |

### 2.6 Stock Adjustments

| Method & Path | Permission & Scope | Idempotency | Purpose |
|---|---|---|---|
| `GET /api/v1/inventory/adjustments` | `inventory.adjustments.read` (WH) | N/A | List adjustments (`warehouseId`, `status`, `reason`, `fromDate`, `toDate`) |
| `POST /api/v1/inventory/adjustments` | `inventory.adjustments.create` (WH) | Optional | Create `DRAFT` (or auto-submit to `PENDING_APPROVAL`) adjustment |
| `GET /api/v1/inventory/adjustments/{adjustmentId}` | `inventory.adjustments.read` (WH) | N/A | Fetch header + lines |
| `POST /api/v1/inventory/adjustments/{adjustmentId}/submit` | `inventory.adjustments.create` (WH) | **Required** | Submit `DRAFT -> PENDING_APPROVAL` |
| `POST /api/v1/inventory/adjustments/{adjustmentId}/approve` | `inventory.adjustments.approve` (WH) | **Required** | Approve & post adjustment (`ADJUSTMENT_IN` / `ADJUSTMENT_OUT` / `WRITE_OFF`) |
| `POST /api/v1/inventory/adjustments/{adjustmentId}/reject` | `inventory.adjustments.approve` (WH) | **Required** | Reject `PENDING_APPROVAL -> REJECTED` with `rejectionReason` |
| `POST /api/v1/inventory/adjustments/{adjustmentId}/cancel` | `inventory.adjustments.create` (WH) | **Required** | Cancel `DRAFT` / `PENDING_APPROVAL` adjustment |

### 2.7 Stock Transfers

| Method & Path | Permission & Scope | Idempotency | Purpose |
|---|---|---|---|
| `GET /api/v1/inventory/transfers` | `inventory.transfers.read` (Source or Dest WH) | N/A | Filters: `sourceWarehouseId`, `destinationWarehouseId`, `status`, `fromDate`, `toDate` |
| `POST /api/v1/inventory/transfers` | `inventory.transfers.create` (Source or Dest WH) | Optional | Create `DRAFT` transfer |
| `GET /api/v1/inventory/transfers/{transferId}` | `inventory.transfers.read` (Source or Dest WH) | N/A | Fetch transfer header + lines (incl. remaining in-transit quantities) |
| `POST /api/v1/inventory/transfers/{transferId}/request` | `inventory.transfers.create` (Source or Dest WH) | **Required** | `DRAFT -> REQUESTED` |
| `POST /api/v1/inventory/transfers/{transferId}/approve` | `inventory.transfers.dispatch` (Source WH) | **Required** | `DRAFT` / `REQUESTED -> APPROVED` |
| `POST /api/v1/inventory/transfers/{transferId}/dispatch` | `inventory.transfers.dispatch` (Source WH) | **Required** | `APPROVED -> DISPATCHED` (writes `TRANSFER_OUT`, increments dest `quantity_in_transit`) |
| `POST /api/v1/inventory/transfers/{transferId}/receive` | `inventory.transfers.receive` (Dest WH) | **Required** | Partial or full receipt (`receivedGoodQuantity`, `receivedDamagedQuantity`, `shortageQuantity`, `varianceReason`) |
| `POST /api/v1/inventory/transfers/{transferId}/cancel` | `inventory.transfers.cancel` (Source WH) | **Required** | Cancel pre-dispatch only (`409` if `DISPATCHED` or later) |

### 2.8 Inventory Reservations

| Method & Path | Permission & Scope | Idempotency | Purpose |
|---|---|---|---|
| `GET /api/v1/inventory/reservations` | `inventory.reservations.read` (WH) | N/A | Filters: `warehouseId`, `status`, `sourceType`, `sourceReferenceId`, `expiringBefore` |
| `POST /api/v1/inventory/reservations` | `inventory.reservations.manage` (WH) | **Required** | Create active soft reservation against sellable stock |
| `GET /api/v1/inventory/reservations/{reservationId}` | `inventory.reservations.read` (WH) | N/A | Reservation details & remaining reserved lines |
| `POST /api/v1/inventory/reservations/{reservationId}/release` | `inventory.reservations.manage` (WH) | **Required** | Manually release remaining reserved stock (`RELEASED`) |

---

## 3. Sample Key Request / Response Contracts

### 3.1 `POST /api/v1/inventory/receipts/{receiptId}/post` (or Direct Create+Post)

**Create Receipt Request**:
```json
{
  "warehouseId": "8f1d45b2-0e4a-4e2f-9a21-6c7a9d31f001",
  "sourceType": "PURCHASE_ORDER",
  "sourceReferenceId": "44ab901c-3d12-4012-a111-000000000123",
  "receivedAt": "2026-11-26T09:15:00Z",
  "notes": "Morning supplier delivery",
  "lines": [
    {
      "variantId": "11111111-2222-3333-4444-555555555555",
      "locationId": null,
      "quantity": "24.000",
      "unitCost": "12500.0000"
    }
  ]
}
```
*(When `locationId` is `null`, the service assigns the warehouse's active `isDefault = true` `STORAGE` location.)*

### 3.2 `POST /api/v1/inventory/transfers/{transferId}/receive`

```json
{
  "version": 3,
  "receivedAt": "2026-11-26T14:30:00Z",
  "notes": "Courier batch #1",
  "lines": [
    {
      "transferLineId": "99999999-aaaa-bbbb-cccc-dddddddddddd",
      "destinationLocationId": null,
      "damagedLocationId": "77777777-8888-9999-0000-111111111111",
      "receivedGoodQuantity": "18.000",
      "receivedDamagedQuantity": "1.000",
      "shortageQuantity": "1.000",
      "varianceReason": "1 unit broken seal, 1 unit missing from carton"
    }
  ]
}
```

---

## 4. Standardized Error Code Matrix

| HTTP Status | `errorCode` (`ProblemDetail`) | Trigger Condition |
|---|---|---|
| `400 Bad Request` | `VALIDATION_ERROR` | Missing required fields, `quantity <= 0`, `unitCost < 0`, scale exceeds `VariantCatalogDto.decimalPrecision` or `allowDecimal=false` |
| `400 Bad Request` | `MISSING_IDEMPOTENCY_KEY` | Ledger/balance mutation invoked without `Idempotency-Key` header |
| `400 Bad Request` | `INVALID_TRANSFER_WAREHOUSES` | `sourceWarehouseId == destinationWarehouseId` or invalid location-warehouse ownership |
| `403 Forbidden` | `ACCESS_DENIED` | Caller lacks required permission or explicit `WAREHOUSE` / `TENANT` scope for target warehouse |
| `404 Not Found` | `RESOURCE_NOT_FOUND` | Entity ID (`warehouseId`, `variantId`, `transferId`, etc.) missing OR belongs to another tenant |
| `409 Conflict` | `INSUFFICIENT_AVAILABLE_STOCK` | `quantityOnHand - quantityReserved < requestedOutflow` |
| `409 Conflict` | `VARIANT_NOT_TRANSACTABLE` | Referenced variant is `DRAFT`, `ARCHIVED`, or `variantActive=false` for restricted operation |
| `409 Conflict` | `WAREHOUSE_NOT_ACTIVE` / `LOCATION_NOT_ACTIVE` | Attempted posting to `INACTIVE` / `ARCHIVED` warehouse or `INACTIVE` location |
| `409 Conflict` | `WAREHOUSE_DEACTIVATION_BLOCKED` | Warehouse still holds stock (`on_hand > 0`), reservations, `in_transit > 0`, or open operational docs |
| `409 Conflict` | `INVALID_DOCUMENT_STATE` | Illegal status transition (e.g., cancelling a `DISPATCHED` transfer or `POSTED` receipt) |
| `409 Conflict` | `COUNT_CONCURRENT_MOVEMENT` | Stock changed since `StockCount.snapshotAt` during strict reconciliation |
| `409 Conflict` | `OPTIMISTIC_LOCK_CONFLICT` | Stale document `version` submitted on `PATCH`/`PUT`/transition |
| `409 Conflict` | `IDEMPOTENCY_CONFLICT_IN_PROGRESS` | Same `Idempotency-Key` currently executing concurrently |
| `422 Unprocessable Entity` | `IDEMPOTENCY_PAYLOAD_MISMATCH` | Same `Idempotency-Key` reused with a different request payload hash |
