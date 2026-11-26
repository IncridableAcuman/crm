# M06 — Inventory Management: Multi-Tenancy, RBAC & Security Architecture

> **Status**: Security & Isolation Specification (M06.1 — Design Only)  
> **Builds Upon**: [multi-tenancy.md](multi-tenancy.md), [04-rbac-and-authorization.md](04-rbac-and-authorization.md), and [ADR-0007](decisions/ADR-0007-rbac-scope-and-concurrency-architecture.md)

---

## 1. Seven-Pillar Multi-Tenancy Defense in Inventory

Every Inventory query, mutation, cache entry, and background task enforces full tenant isolation:

| Pillar | Inventory Enforcement Mechanism |
|---|---|
| 1. Context Establishment | `JwtAuthenticationFilter` resolves `tenantId` exclusively from the tenant-scoped JWT and re-verifies it against the live `TenantMembership` before binding `TenantContextHolder`. No client header (`X-Tenant-Id` does not exist in the codebase) and no body/query `tenantId` is ever consulted. |
| 2. Repository Isolation | All Inventory Spring Data JPA repositories extend `TenantAwareRepository<T, UUID>` and accept explicit `tenantId` filters on every query, projection, and lock query (`@Lock(PESSIMISTIC_WRITE)`). |
| 3. Composite Foreign Keys | Cross-table references inside PostgreSQL enforce `(tenant_id, target_id) -> target(tenant_id, id) ON DELETE RESTRICT`, making cross-tenant foreign keys impossible at SQL commit time. |
| 4. Native Query Hygiene | Zero unscoped native SQL queries; any native query or bulk update (`ReservationExpirationJob` batch cleanup, reporting queries) enforces explicit `WHERE tenant_id = :tenantId`. |
| 5. Request-Time Cross-Tenant Rejection | Passing a `warehouseId`, `locationId`, `variantId`, `transferId`, `receiptId`, or `reservationId` belonging to another tenant returns `404 Not Found` (preventing tenant resource existence leakage), never `403` revealing another tenant's UUID. |
| 6. Async & Scheduled Isolation | `ReservationExpirationJob` iterates active tenant IDs and wraps each tenant's batch inside `TenantExecutionTemplate.executeInTenant(tenantId, ...)` so `TenantContextHolder` is properly set and cleared in `finally`. |
| 7. Cache Isolation | If read caching is introduced (e.g., warehouse metadata lookups), cache keys MUST include `{tenantId}:{warehouseId}`. Mutable stock quantities (`inventory_balances`, `inventory_warehouse_stocks`) are **never** cached in memory/Redis; they are read transactionally from PostgreSQL. |

---

## 2. RBAC Scope Expansion: Introducing `WAREHOUSE` Scope (`ADR-0016`)

### 2.1 Existing Implemented State (`M04`) vs Target State (`M06`)

In M04 (`RoleScopeType.java`, `v1.0.0/12`, `ADR-0007`), `WAREHOUSE` scope was intentionally deferred until the Inventory module existed. Implementing Inventory requires activating `WAREHOUSE` as a first-class `RoleScopeType`:

```java
public enum RoleScopeType {
    TENANT,
    BRANCH,
    WAREHOUSE
}
```

### 2.2 Scope Precedence & Non-Inheritance Rules

> **Critical Security Rule**: **Branch access does NOT automatically grant warehouse access.**

Warehouse operations handle high-value physical assets, adjustments, write-offs, and central hub inventory (`branch_id = NULL`). Granting `BRANCH` users implicit write/view rights over warehouses attached to that branch—or worse, central hubs—would violate least privilege.

| User Role Assignment Scope | Tenant-Wide Catalog / Global Ops | Branch Operations (`branchId`) | Warehouse Operations (`warehouseId`) | Central Hub (`warehouse.branchId == NULL`) |
|---|---|---|---|---|
| **`TENANT`** (`scope_type = 'TENANT'`) | **Granted** (if role holds permission) | **Granted** across all branches of tenant | **Granted** across all warehouses of tenant | **Granted** |
| **`BRANCH`** (`scope_type = 'BRANCH'`, `branch_id = B`) | Granted only for branch-scoped permissions if endpoint permits | **Granted** for branch `B` only | **NOT implicitly granted** for physical warehouse mutations/admin (see §2.3 exception for branch POS read projection if enabled) | **Denied** |
| **`WAREHOUSE`** (`scope_type = 'WAREHOUSE'`, `warehouse_id = W`) | No | No | **Granted** for warehouse `W` only | **Granted** if assigned to `W` |

### 2.3 Explicit `PermissionAuthorizationService` Contract Extensions

Extend `PermissionAuthorizationService` (`modules/identity`) with warehouse-aware checks matching existing `hasPermission` / `hasBranchPermission` signatures:

1. `boolean hasWarehousePermission(UUID warehouseId, String permission)`  
   Checks whether current authenticated principal holds `permission` via:
   - `OWNER` tenant bypass, **OR**
   - An active `TENANT`-scoped role assignment carrying `permission`, **OR**
   - An active `WAREHOUSE`-scoped role assignment where `assignment.warehouseId == warehouseId` carrying `permission`.
   - Active `BRANCH` assignments **never** satisfy `hasWarehousePermission`.
2. `Set<UUID> getPermittedWarehouseIds(String permission)`  
   - Returns all warehouse IDs (resolved by caller or tenant `Warehouse` scope) if user holds `TENANT` scope with `permission` (or `OWNER`); otherwise returns exact set of `warehouse_id` values from active `WAREHOUSE` assignments holding `permission`.
   - Used on list endpoints (`GET /api/v1/inventory/warehouses`, `GET /api/v1/inventory/stock`, `GET /api/v1/inventory/transfers`, etc.) when `?warehouseId=` is omitted: users without `TENANT` scope automatically see only their authorized warehouses (`403` if zero access or specific unauthorized `warehouseId` requested).
3. `boolean hasBranchStockReadPermission(UUID branchId, String permission)` *(POS / Catalog Bridge)*  
   - When `Sales`/POS or `Catalog` `?branchId=` queries stock availability for a branch's retail store warehouse, `BRANCH` roles holding `inventory.stock.read` (e.g., `CASHIER` / `MANAGER` at Branch `B`) may read stock availability for warehouses explicitly linked to `branch_id = B` (`Warehouse.branchId == B`). Write operations (`receipts`, `adjustments`, `counts`, `transfers`, `warehouses.manage`) **always** require `hasWarehousePermission` (`TENANT` or explicit `WAREHOUSE` assignment).

### 2.4 Denied-Access Behaviour (Fail Closed)

| Caller state | Warehouse-scoped read | Warehouse-scoped write |
|---|---|---|
| `TENANT` scope holding the permission, or `OWNER` | Allowed for every warehouse in the tenant | Allowed |
| Explicit `WAREHOUSE(W)` assignment holding the permission | Allowed for `W` only; list endpoints silently filter to `W` | Allowed for `W` only |
| `BRANCH(B)` assignment only | Allowed only on the POS/stock-read bridge for warehouses with `branch_id = B`; no ledger, receipt, count, adjustment, transfer, or warehouse-admin access | Denied (`403`) |
| Authenticated but no inventory permission | `403` | `403` |
| No active membership / suspended tenant | `401`/`403` from the M03 filter chain before Inventory is reached | Same |
| Warehouse UUID belonging to another tenant | `404` (never `403`, to avoid confirming existence) | `404` |
| Warehouse UUID in the same tenant but not assigned | `403` when explicitly requested; omitted from unfiltered list results | `403` |

**Correction guard**: `getPermittedWarehouseIds(permission)` returning an empty set must not be interpreted as "no filter". List queries with an empty permitted set return an empty page with `200 OK` (or `403` when the caller explicitly requested an unauthorized `warehouseId`), never the full tenant result.

### 2.5 Cross-Warehouse Operations (`StockTransfer`) Authorization

A `StockTransfer` touches two warehouses (`sourceWarehouseId` and `destinationWarehouseId`):

| Action | Required Authorization |
|---|---|
| `Create / Request Transfer` (`DRAFT` -> `REQUESTED`) | `hasWarehousePermission(sourceWarehouseId, "inventory.transfers.create")` OR `hasWarehousePermission(destWarehouseId, "inventory.transfers.create")` (destination replenishment requests require read/visibility + destination permission; dispatch always checks source) |
| `Approve & Dispatch Transfer` (`DISPATCHED`) | `hasWarehousePermission(sourceWarehouseId, "inventory.transfers.dispatch")` (and destination warehouse must belong to same tenant and be `ACTIVE`) |
| `Receive Transfer` (`RECEIVED` / `PARTIALLY_RECEIVED`) | `hasWarehousePermission(destinationWarehouseId, "inventory.transfers.receive")` |
| `Cancel Pre-Dispatch Transfer` | `hasWarehousePermission(sourceWarehouseId, "inventory.transfers.cancel")` |
| `Read Transfer` | User holds `"inventory.transfers.read"` on **either** `sourceWarehouseId` or `destinationWarehouseId` (or `TENANT` scope). |

---

## 3. Inventory Permission Catalog (`Permissions.java`)

> **Migration status note (verified)**: `v1.0.0/32-seed-inventory-permissions.xml` already inserts the 20 new permission rows into the `permissions` table, but `com.savdocrm.modules.identity.domain.Permissions` still declares only 33 constants (3 inventory). Until `M06.3` adds the 20 constants to `ALL_PERMISSIONS`, `OWNER`'s dynamic bypass will not include them and no `@PreAuthorize` can reference them. The doc and seed must be kept in lockstep; the seed alone is not a working authorization change.

Currently, `Permissions.java` defines 33 permissions, of which **3 are already present** for Inventory:
- `inventory.stock.read`
- `inventory.adjustments.create`
- `inventory.transfers.create`

All 3 existing codes are **preserved verbatim** to maintain backward compatibility with M04 seeds and custom roles. M06 adds **20 granular permissions** (bringing platform permissions from `33` to `53`):

| Permission Code | Category | Status | Description |
|---|---|---|---|
| `inventory.warehouses.read` | Warehouse | New (M06) | View warehouses and storage locations |
| `inventory.warehouses.manage` | Warehouse | New (M06) | Create, update, deactivate warehouses and `stock_locations` |
| `inventory.stock.read` | Stock Balances | **Existing (M04)** | View physical, reserved, available, damaged, quarantined, in-transit quantities |
| `inventory.costs.read` | Valuation | New (M06) | View `WAC` unit costs and inventory total valuation (hidden from cashiers) |
| `inventory.ledger.read` | Ledger | New (M06) | View immutable `inventory_transactions` and audit trail |
| `inventory.moves.create` | Internal Moves | New (M06) | Execute putaway, bin-to-bin moves, damage/quarantine segregation |
| `inventory.receipts.read` | Receiving | New (M06) | View `StockReceipt` documents |
| `inventory.receipts.create` | Receiving | New (M06) | Create/edit `DRAFT` receipts |
| `inventory.receipts.post` | Receiving | New (M06) | Post receipts to stock ledger and update WAC |
| `inventory.receipts.cancel` | Receiving | New (M06) | Cancel `DRAFT` receipts |
| `inventory.counts.read` | Counting | New (M06) | View cycle/physical stock counts |
| `inventory.counts.manage` | Counting | New (M06) | Create, start, record counted quantities, submit for approval |
| `inventory.counts.approve` | Counting | New (M06) | Approve & post count variances into adjustments + ledger |
| `inventory.adjustments.read` | Adjustments | New (M06) | View `StockAdjustment` documents |
| `inventory.adjustments.create` | Adjustments | **Existing (M04)** | Create/submit `StockAdjustment` drafts |
| `inventory.adjustments.approve` | Adjustments | New (M06) | Approve & post adjustments/write-offs (enables maker-checker separation) |
| `inventory.transfers.read` | Transfers | New (M06) | View incoming/outgoing `StockTransfer` documents |
| `inventory.transfers.create` | Transfers | **Existing (M04)** | Create/request stock transfers |
| `inventory.transfers.dispatch` | Transfers | New (M06) | Approve & dispatch stock from source warehouse (`TRANSFER_OUT`) |
| `inventory.transfers.receive` | Transfers | New (M06) | Receive/reconcile stock at destination warehouse (`TRANSFER_IN`) |
| `inventory.transfers.cancel` | Transfers | New (M06) | Cancel pre-dispatch stock transfers |
| `inventory.reservations.read` | Reservations | New (M06) | View stock holds and expiration status |
| `inventory.reservations.manage` | Reservations | New (M06) | Create, release, or manually manage stock reservations |

**Granularity rationale** (deviations from the candidate list in the milestone brief):

- **`warehouses.read` + `warehouses.manage`** instead of separate `create`/`update`/`manage`: a half-permissioned user who may create a warehouse but not rename it has no real workflow; one management permission per resource keeps role administration tractable. `manage` is still tenant/house-scoped and separate from `read`.
- **Document-scoped write permissions** (`receipts.post`, `counts.approve`, `adjustments.approve`, `transfers.dispatch`/`receive`) instead of generic `stock.receive`/`stock.issue`/`stock.adjust`: posting authority belongs to the business document that justifies the movement. `stock.read` remains the single read capability. Generic stock writes through an API would violate the no-arbitrary-mutation rule.
- **`transfers.approve` is intentionally absent**: approval and dispatch are one atomic decision in this design (a separate approval without dispatch adds ceremony without control); `transfers.dispatch` covers both transitions. Revisit only if a two-person dispatch control is requested.

---

## 4. Role Template Matrix & M04 Non-Destructive Migration Policy

### 4.1 System Role Default Matrix (`SystemRoles.java` Updates)

*Note on `WAREHOUSE_MANAGER` Default Scope*: In M04 (`SystemRoles.java`), `WAREHOUSE_MANAGER` temporarily defaulted to `RoleScopeType.BRANCH` (`ADR-0007`) before `WAREHOUSE` scope existed. Once `WAREHOUSE` scope is enabled in M06, new `WAREHOUSE_MANAGER` template definitions default to `WAREHOUSE` (while remaining assignable at `WAREHOUSE` or `TENANT` scope, or scoped per warehouse via assignment UI; existing M04 `BRANCH`-scoped `WAREHOUSE_MANAGER` assignments in live DBs stay untouched unless explicitly migrated by tenant admin!).

| Permission | `OWNER` (`TENANT`) | `ADMIN` (`TENANT`) | `WAREHOUSE_MANAGER` (`WAREHOUSE`) | `MANAGER` (`BRANCH` / `WAREHOUSE`) | `CASHIER` (`BRANCH`) | `ACCOUNTANT` (`TENANT`) | `VIEWER` (`TENANT`) |
|---|---|---|---|---|---|---|---|
| `inventory.warehouses.read` | Yes | Yes | Yes (assigned WH) | Yes (branch/WH read) | No | Yes | Yes |
| `inventory.warehouses.manage` | Yes | Yes | No *(tenant admin only)* | No | No | No | No |
| `inventory.stock.read` | Yes | Yes | Yes | Yes | Yes *(branch stock read)* | Yes | Yes |
| `inventory.costs.read` | Yes | Yes | Yes | Optional / No default | No | Yes | No |
| `inventory.ledger.read` | Yes | Yes | Yes | Yes | No | Yes | No |
| `inventory.moves.create` | Yes | Yes | Yes | No | No | No | No |
| `inventory.receipts.read/create/post/cancel` | Yes | Yes | Yes | Read only | No | Read only | No |
| `inventory.counts.read/manage` | Yes | Yes | Yes | Read only | No | Read only | No |
| `inventory.counts.approve` | Yes | Yes | Optional *(if single-person)* / Admin | No | No | No | No |
| `inventory.adjustments.read/create` | Yes | Yes | Yes | Read only | No | Read only | No |
| `inventory.adjustments.approve` | Yes | Yes | No *(maker-checker default)* | No | No | No | No |
| `inventory.transfers.read/create/dispatch/receive/cancel` | Yes | Yes | Yes | Read / Request | No | Read only | No |
| `inventory.reservations.read/manage` | Yes | Yes | Yes | Yes | Read/Reserve via Sales POS | No | No |

### 4.2 Existing-Tenant Safe Liquibase Migration Strategy (`04-rbac-and-authorization.md` §5.4)

Per `ADR-0007` and `04-rbac-and-authorization.md`:
1. **New Permissions Seed**: Liquibase inserts the 20 new `permissions` rows (`INSERT ... ON CONFLICT DO NOTHING`). Existing 33 codes remain untouched.
2. **`OWNER` Automatic Coverage**: `PermissionAuthorizationServiceImpl` grants `Permissions.ALL_PERMISSIONS` dynamically to `OWNER` AND Liquibase seeds new `role_permissions` onto system `OWNER` roles.
3. **Existing Tenants' Custom Roles Untouched**: Liquibase **NEVER** alters custom roles (`is_system = false`).
4. **Existing Tenants' Non-`OWNER` System Roles (`ADMIN`, `WAREHOUSE_MANAGER`, `ACCOUNTANT`, `VIEWER`)**: Following `ADR-0007` ("never silently modify custom roles; new permissions map to `OWNER` on existing tenants, and to system template defaults for newly created tenants"):
   - Newly onboarded tenants receive the updated `SystemRoles` default mappings automatically.
   - Existing tenants receive new `inventory.*` permissions automatically on `OWNER`, who retains Grant-What-You-Hold authority to assign them to `ADMIN` / `WAREHOUSE_MANAGER` / custom roles—OR an explicit documented additive seed may populate non-destructive new-module defaults onto system template roles if product policy approves; by default M06 preserves `ADR-0007`'s conservative `OWNER`-only guarantee for existing tenants so zero unintended privilege escalation occurs!
5. **Existing `RoleScopeType.BRANCH` Assignments for `WAREHOUSE_MANAGER`**: Preserved intact in `user_role_assignments` (`scope_type = 'BRANCH'`) so M04 RBAC integration tests and existing user sessions remain unbroken. Tenant Owners assign `WAREHOUSE`-scoped roles once warehouses are created.

---

## 5. Role Management Guards for `WAREHOUSE` Scope

All existing M04 guards extend symmetrically to `WAREHOUSE` scope:
- **Owner Protection**: `OWNER` remains strictly `TENANT`-scoped; cannot be assigned at `WAREHOUSE` scope (`400 Bad Request`).
- **Role Template Scope Validation**: Role assignment service validates `warehouseId` exists in `currentTenantId` and is not `ARCHIVED` before inserting `scope_type = 'WAREHOUSE'`; invalid/cross-tenant `warehouseId` returns `404` / `400`.
- **Grant-What-You-Hold**: A user delegating a `WAREHOUSE`-scoped role must hold all permissions of that role at `TENANT` scope or for that specific `warehouseId` (unless `OWNER`).
- **Cache Invalidation**: Modifying `WAREHOUSE`-scoped `user_role_assignments` or archiving a `Warehouse` bumps `user_permission_version` / evicts request-scoped memoized permission caches identically to `BRANCH` changes.
- **No Self-Approval on Maker-Checker Option**: Where dual control is enabled (e.g., `StockAdjustment`), `approvedBy != submittedBy` may be enforced optionally or via separate `inventory.adjustments.approve` permission.
