# ADR-0010: Catalog Pricing Ownership, RBAC Read Scope & Downstream Module Integration

- **Status**: Accepted (M05.1 Design)
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
- **Require `TENANT`-scope `catalog.products.read`**: Rejected because branch-scoped `CASHIER` and `MANAGER` users would receive `403 Forbidden` when viewing products or scanning barcodes.
- **`hasAnyScopePermission` for Catalog Reads + `hasPermission` (Tenant Scope) for Catalog Writes**: Selected. Any user holding `catalog.products.read` / `catalog.categories.read` at `TENANT` scope OR on at least one active `BRANCH` can read the shared store catalog, while mutations remain tenant-scoped.

---

## 3. Decision

1. **`ProductVariant` holds `sellingPrice` (base list price) and `costPrice` (reference purchase estimate)** using `BigDecimal` / `NUMERIC(15,4)`. Neither field acts as a financial ledger or WAC source of truth.
2. **Downstream Integration via `ProductQueryFacade`** returning `VariantCatalogDto` and `VariantLookupDto` keyed by `(tenantId, variantId)`. `Catalog` has zero dependencies on `Inventory`, `Sales`, `Purchases`, or `Finance`.
3. **RBAC Read vs Write Scope**: Catalog read endpoints (`GET /products*`, `GET /categories*`, `GET /units`) evaluate `hasAnyScopePermission(...)`; Catalog write endpoints (`POST`, `PUT`, `PATCH`, `DELETE`) evaluate tenant-scoped `hasPermission(...)` using the 6 existing M04 `catalog.*` permissions.

---

## 4. Consequences & Deferred Alternatives

- **Positive**:
  - Immediate POS-ready base pricing in M05 without prematurely building a standalone Pricing engine.
  - Zero circular dependencies across `Catalog`, `Inventory`, `Sales`, `Purchases`, and `Finance`.
  - Branch-scoped cashiers, warehouse managers, and store managers can seamlessly browse products and scan barcodes.
- **Negative**:
  - `PermissionAuthorizationService` requires adding `hasAnyScopePermission(String permissionCode)` in M05.4 (a small, non-breaking additive method alongside existing `hasPermission` and `hasBranchPermission`).
  - Branch-specific price overrides and tiered/promotional pricing require a dedicated `Pricing` / `PriceBook` capability in `Sales` in a later milestone.
- **Deferred Alternatives**:
  - Multi-branch `price_books`, customer-group wholesale pricing tiers, and tax-inclusive/exclusive pricing regimes (`Finance`/`Sales`).
