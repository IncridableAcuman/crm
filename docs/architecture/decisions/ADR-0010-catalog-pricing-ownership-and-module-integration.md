# ADR-0010: Catalog Pricing Ownership, RBAC Read Scope & Downstream Module Integration

- **Status**: Accepted (M05.1 Design; RBAC Read Scope Refined in M05.4)
- **Date**: 2026-11-25

---

## 1. Context & Problem

1. **Pricing Ownership**: Product creation in retail POS requires entering a selling price and optional purchase/cost price, yet `Inventory` owns Weighted Average Cost (WAC), `Purchases` owns supplier invoice prices, and `Sales` owns final checkout line prices, discounts, and future branch price books.
2. **Catalog-to-Inventory/Sales Coupling**: Downstream modules need product metadata (`sku`, `barcode`, `unit`, `sellingPrice`, `status`) without breaking Modular Monolith encapsulation (`ArchitectureTest`).
3. **RBAC Branch-Scope Reader Mismatch**: In M04 (`PermissionAuthorizationServiceImpl`), `hasPermission(code)` checks only `TENANT`-scoped assignments. However, `SystemRoles` grants `catalog.products.read` and `catalog.categories.read` to `MANAGER`, `CASHIER`, and `WAREHOUSE_MANAGER` roles, which are typically assigned at **`BRANCH` scope**.

---

## 2. Considered Options

### Pricing Ownership Options
- **Keep Prices Out of Catalog Completely (Separate Pricing Module in M05)**: Rejected for M05 SMB UX. Merchants expect to enter a product's base selling price when creating a product, and creating a separate Pricing bounded context prematurely complicates M05 before `Sales` exists.
- **Catalog Owns Authoritative Financial Cost / Historical Sale Price**: Rejected. Violates `Inventory` WAC and `Finance` accounting invariants — editing a product price in Catalog must never alter historical `sale_items` or inventory valuation.
- **Catalog Owns Reference Baseline Prices on `ProductVariant` (`selling_price`, `cost_price`)**: Selected. `ProductVariant.sellingPrice` is the default list price; `ProductVariant.costPrice` is a reference/default purchase estimate only. `Inventory` remains authoritative for WAC, `Purchases` for PO line costs, and `Sales` snapshots `unit_price` onto `sale_items` at checkout.

### Module Integration Options
- **Direct Cross-Module Repository Injection**: Rejected (prohibited by Modular Monolith rules and ArchUnit).
- **Public `ProductQueryFacade` Returning Immutable DTOs**: Selected. `Inventory`, `Sales`, and `Purchases` query `ProductQueryFacade` using `variantId`, `sku`, or `barcode`.

### RBAC Evaluation Options for Tenant-Shared Catalog Reads
- **Require `TENANT`-scope `catalog.products.read` unconditionally**: Rejected because branch-scoped `CASHIER`, `MANAGER`, and `WAREHOUSE_MANAGER` users would have no path to read products or scan barcodes in their assigned branch.
- **Unconditional `hasAnyScopePermission` for Catalog Reads (M05.1 Initial Proposal — Superseded in M05.4)**: Originally proposed in M05.1 so any user with `catalog.products.read` / `catalog.categories.read` at `TENANT` scope or on at least one active `BRANCH` could call read endpoints without specifying a branch. Superseded during M05.4 security hardening because omitting `branchId` would grant branch-only users unrestricted tenant-wide catalog access without verifying a target branch boundary.
- **Conditional `hasPermission` (without `branchId`) / `hasBranchPermission` (with `branchId`) for Catalog Reads + `hasPermission` (`TENANT` Scope) for Catalog Writes (M05.4 Final Implementation)**: Selected. Catalog read endpoints require `TENANT`-scoped permission when `branchId` is omitted and verify the requested active branch via `hasBranchPermission(..., #branchId)` when `branchId` is provided, while catalog mutations remain strictly `TENANT`-scoped.

---

## 3. Decision

1. **`ProductVariant` holds `sellingPrice` (base list price) and `costPrice` (reference purchase estimate)** using `BigDecimal` / `NUMERIC(15,4)`. Neither field acts as a financial ledger or WAC source of truth.
2. **Downstream Integration via `ProductQueryFacade`** returning `VariantCatalogDto` and `VariantLookupDto` keyed by `(tenantId, variantId)`. `Catalog` has zero dependencies on `Inventory`, `Sales`, `Purchases`, or `Finance`.
3. **RBAC Read vs Write Scope (M05.4 Final Implementation Decision)**:
   - *Historical Context*: M05.1 initially proposed `hasAnyScopePermission` for catalog reads, and `PermissionAuthorizationService` / `PermissionAuthorizationServiceImpl` provides `hasAnyScopePermission(String permissionCode)` (and `hasAnyScopePermission(UUID, UUID, String)`) as an additive helper. However, the M05.4 Catalog REST controllers intentionally enforce stricter explicit branch scoping instead of `hasAnyScopePermission`.
   - **Catalog Read Endpoints (`GET /api/v1/products*`, `GET /api/v1/products/{productId}/variants*`, `GET /api/v1/categories*`, `GET /api/v1/units*`)**: Accept optional `@RequestParam(required = false) UUID branchId` and evaluate `#branchId == null ? @authorizationService.hasPermission(...) : @authorizationService.hasBranchPermission(..., #branchId)` using `catalog.products.read` (for products, lookup, variants, barcodes, and units) or `catalog.categories.read` (for categories):
     - **Without `branchId`**: Requires `TENANT`-scoped permission (`hasPermission`). Branch-only users (`BRANCH`-scoped `CASHIER`, `MANAGER`, `WAREHOUSE_MANAGER`) must not receive tenant-wide catalog access by omitting `branchId` and receive `403 Forbidden`.
     - **With `branchId`**: Evaluates `hasBranchPermission(..., #branchId)`, checking that the requested branch belongs to the active tenant and is active (`branch.isActive()`), and permitting either a valid `TENANT`-scoped permission (scope inheritance) or an active `BRANCH`-scoped permission for that exact branch.
     - **Fail-Closed Guarantees**: Cross-tenant `branchId`, unknown `branchId`, unassigned branches, and inactive branches fail closed with `403 Forbidden`.
   - **Catalog Write Endpoints (`POST`, `PUT`, `PATCH`, `DELETE`)**: Remain strictly `TENANT`-scoped via `@authorizationService.hasPermission(...)` using the 6 existing M04 `catalog.*` permissions (`catalog.products.create`, `catalog.products.update`, `catalog.products.delete` for product archive / `ARCHIVED` status transition / draft deletion, and `catalog.categories.manage` for categories and custom UOMs).

---

## 4. Consequences & Deferred Alternatives

- **Positive**:
  - Immediate POS-ready base pricing in M05 without prematurely building a standalone Pricing engine.
  - Zero circular dependencies across `Catalog`, `Inventory`, `Sales`, `Purchases`, and `Finance`.
  - Branch-scoped cashiers, warehouse managers, and store managers can browse products, categories, units, and scan barcodes for their assigned active branch by passing `?branchId=...`, while tenant-wide reads without `branchId` remain restricted to `TENANT`-scoped roles.
  - Cross-tenant and inactive branch parameters fail closed at the authorization boundary.
- **Negative**:
  - Branch-scoped UI/POS clients (M05.5+) must supply the user's active `branchId` query parameter on catalog read requests.
  - `PermissionAuthorizationService` retains the additive `hasAnyScopePermission` methods introduced alongside M05.4 preparation, even though the four Catalog REST controllers evaluate `hasPermission` / `hasBranchPermission`.
  - Branch-specific price overrides and tiered/promotional pricing require a dedicated `Pricing` / `PriceBook` capability in `Sales` in a later milestone.
- **Deferred Alternatives**:
  - Multi-branch `price_books`, customer-group wholesale pricing tiers, and tax-inclusive/exclusive pricing regimes (`Finance`/`Sales`).
