# SAVDOCRM — Milestone 05: Catalog REST API Contracts & RBAC Integration

- **Status**: API Contract Design Approved (M05.1)
- **Version**: 1.0.0
- **Base URI**: `/api/v1`
- **Target Implementation**: Milestone 05.4 (*not yet implemented*)

---

## 1. Cross-Cutting API & Security Conventions

1. **Tenant Resolution**: Every endpoint requires a tenant-scoped JWT (`JwtAuthenticationFilter` + `TenantContextHolder.getRequiredTenantId()`). Client-supplied `tenantId` parameters or headers are ignored/rejected.
2. **Response Envelopes**:
   - Single-item & mutation responses use `ApiResponse<T>` (`success`, `data`, `message`, `timestamp`).
   - Paginated list responses (`GET /api/v1/products`) return `ApiResponse<PageResponse<ProductSummaryResponse>>` with `{ content / items, page, size, totalElements, totalPages, first, last }` aligned with `docs/architecture/api-conventions.md`.
   - Errors return RFC 7807 `ProblemDetail` (`type`, `title`, `status`, `detail`, `instance`, `timestamp`, and `errors` map for `400` field validation).
3. **RBAC Integration & Branch-Scoped Reader Finding**:
   - Catalog uses the **6 existing canonical permissions** from M04 (`Permissions.java`) without duplicating permissions:
     - `catalog.products.read`
     - `catalog.products.create`
     - `catalog.products.update`
     - `catalog.products.delete`
     - `catalog.categories.read`
     - `catalog.categories.manage`
   - **Important M04 Authorization Gap Addressed for M05.4**: In M04 (`PermissionAuthorizationServiceImpl.hasPermission`), `@PreAuthorize("@authorizationService.hasPermission(...)")` evaluates **only `TENANT`-scoped assignments**. However, `SystemRoles` grants `catalog.products.read` and `catalog.categories.read` to `MANAGER`, `CASHIER`, and `WAREHOUSE_MANAGER` roles that are commonly assigned at **`BRANCH` scope**.
   - **Design Rule for M05.4**:
     - Introduce `authorizationService.hasAnyScopePermission(permissionCode)` (true if user holds the permission at `TENANT` scope OR on at least one active `BRANCH` in the active tenant) for **Catalog read endpoints** (`GET /products*`, `GET /categories*`, `GET /units`), so branch-scoped cashiers/managers can read the shared store catalog.
     - Keep **Catalog write endpoints** (`POST`, `PUT`, `PATCH`, `DELETE`) protected by `authorizationService.hasPermission(...)` (`TENANT` scope), because Catalog master data affects all branches in the tenant.
   - **Units of Measure RBAC**: `GET /api/v1/units` reuses `catalog.products.read` (`hasAnyScopePermission`); `POST`/`PUT`/`PATCH` on `/api/v1/units` reuses `catalog.categories.manage` (`hasPermission`). No 34th permission is required for M05 (an optional `catalog.units.manage` split is documented as deferred if finer granularity is requested later).

---

## 2. Endpoint Matrix

| Resource | Method & Path | Permission Check | Description |
| :--- | :--- | :--- | :--- |
| Products | `GET /api/v1/products` | `hasAnyScopePermission('catalog.products.read')` | Paginated product search & filtering |
| Products | `GET /api/v1/products/lookup` | `hasAnyScopePermission('catalog.products.read')` | Fast exact barcode or SKU lookup for POS |
| Products | `GET /api/v1/products/{productId}` | `hasAnyScopePermission('catalog.products.read')` | Product details with variants and barcodes |
| Products | `POST /api/v1/products` | `hasPermission('catalog.products.create')` | Create `SIMPLE` or `VARIABLE` product |
| Products | `PUT /api/v1/products/{productId}` | `hasPermission('catalog.products.update')` | Update product metadata (and default variant for `SIMPLE`) |
| Products | `PATCH /api/v1/products/{productId}/status` | `hasPermission('catalog.products.update')` | Transition product status (`DRAFT`/`ACTIVE`/`ARCHIVED`) |
| Products | `DELETE /api/v1/products/{productId}` | `hasPermission('catalog.products.delete')` | Delete unused `DRAFT` product or archive referenced product |
| Variants | `POST /api/v1/products/{productId}/variants` | `hasPermission('catalog.products.update')` | Add variant to `VARIABLE` product |
| Variants | `PUT /api/v1/products/{productId}/variants/{variantId}` | `hasPermission('catalog.products.update')` | Update variant SKU, name, options, prices, barcodes |
| Variants | `PATCH /api/v1/products/{productId}/variants/{variantId}/status` | `hasPermission('catalog.products.update')` | Activate/deactivate individual variant |
| Variants | `DELETE /api/v1/products/{productId}/variants/{variantId}` | `hasPermission('catalog.products.delete')` | Delete unused variant or soft-deactivate if referenced |
| Categories | `GET /api/v1/categories` | `hasAnyScopePermission('catalog.categories.read')` | List tenant categories (`flat` or `tree`) |
| Categories | `GET /api/v1/categories/{categoryId}` | `hasAnyScopePermission('catalog.categories.read')` | Get single category |
| Categories | `POST /api/v1/categories` | `hasPermission('catalog.categories.manage')` | Create category |
| Categories | `PUT /api/v1/categories/{categoryId}` | `hasPermission('catalog.categories.manage')` | Update category & parent (cycle-checked) |
| Categories | `PATCH /api/v1/categories/{categoryId}/status` | `hasPermission('catalog.categories.manage')` | Activate/deactivate category |
| Categories | `DELETE /api/v1/categories/{categoryId}` | `hasPermission('catalog.categories.manage')` | Delete unused category (409 if children/products exist) |
| Units | `GET /api/v1/units` | `hasAnyScopePermission('catalog.products.read')` | List tenant units of measure |
| Units | `POST /api/v1/units` | `hasPermission('catalog.categories.manage')` | Create custom unit of measure |
| Units | `PUT /api/v1/units/{unitId}` | `hasPermission('catalog.categories.manage')` | Update custom unit of measure |
| Units | `PATCH /api/v1/units/{unitId}/status` | `hasPermission('catalog.categories.manage')` | Activate/deactivate custom unit |

*Excluded endpoints in M05*: Standalone `/api/v1/brands` and `/api/v1/attributes` endpoints are omitted because `brand` is an attribute on `Product` and variant options are managed on `Product`/`ProductVariant`. Barcodes are managed inline via `barcodes: BarcodeInput[]` on variant payloads rather than separate chatty HTTP round-trips.

---

## 3. Products API Contracts

### 3.1 `GET /api/v1/products` (Search, Filter & Pagination)
- **Query Parameters**:
  - `search` (`String`, optional): Trimmed case-insensitive match against `product.name`, `variant.sku`, or `barcode.barcode`.
  - `sku` (`String`, optional): Exact/prefix uppercase normalized SKU filter.
  - `barcode` (`String`, optional): Exact barcode match.
  - `categoryId` (`UUID`, optional): Filter by category (with optional `includeSubcategories=true|false`, default `false`).
  - `brand` (`String`, optional): Case-insensitive brand filter.
  - `status` (`ProductStatus`, optional): `DRAFT | ACTIVE | ARCHIVED` (defaults to excluding `ARCHIVED` unless `status=ARCHIVED` or `includeArchived=true`).
  - `productType` (`ProductType`, optional): `SIMPLE | VARIABLE`.
  - `page` (`int`, default `0`, min `0`), `size` (`int`, default `20`, max `100`).
  - `sort` (`String`, default `createdAt,desc`; allowlisted fields: `name`, `createdAt`, `updatedAt`, `status`, `brand`).
- **Response (`200 OK`)**: `PageResponse<ProductSummaryDto>`
  - `id`, `name`, `brand`, `productType`, `status`, `categoryId`, `categoryName`, `unitId`, `unitSymbol`, `variantCount`, `defaultSku`, `primaryBarcode`, `minSellingPrice`, `maxSellingPrice`, `createdAt`, `updatedAt`.

### 3.2 `GET /api/v1/products/lookup` (POS & Scanner Lookup)
- **Query Parameters**: `code` (`String`, `@NotBlank` — checks exact `barcode` first, then exact uppercase `sku`).
- **Response (`200 OK`)**: `VariantLookupDto` (`productId`, `variantId`, `productName`, `variantName`, `sku`, `matchedBarcode`, `sellingPrice`, `unitSymbol`, `allowDecimal`, `decimalPrecision`, `sellable`).
- **Errors**: `404 Not Found` if no matching barcode/SKU exists in the active tenant.

### 3.3 `GET /api/v1/products/{productId}`
- **Response (`200 OK`)**: `ProductDetailDto`
  - `id`, `name`, `description`, `brand`, `productType`, `variantOptions` (`List<String>`), `status`, `category` (`id`, `code`, `name`), `unit` (`id`, `code`, `name`, `symbol`, `allowDecimal`, `decimalPrecision`), `variants` (`List<ProductVariantDto>`), `createdAt`, `updatedAt`, `version`.
  - Each `ProductVariantDto`: `id`, `sku`, `name`, `optionValues` (`Map<String, String>`), `costPrice`, `sellingPrice`, `isDefault`, `isActive`, `barcodes` (`List<ProductBarcodeDto>`), `version`.
- **Errors**: `404 Not Found` if missing or owned by another tenant.

### 3.4 `POST /api/v1/products`
- **Request DTO (`CreateProductRequest`)**:
  - `name` (`@NotBlank`, `@Size(max = 200)`)
  - `description` (`@Size(max = 4000)`, optional)
  - `brand` (`@Size(max = 100)`, optional)
  - `categoryId` (`UUID`, optional — must exist and be active in tenant)
  - `unitId` (`@NotNull UUID` — must exist and be active in tenant)
  - `productType` (`@NotNull ProductType`: `SIMPLE | VARIABLE`)
  - `status` (`ProductStatus`, optional, default `ACTIVE`)
  - `variantOptions` (`List<String>`, required empty `[]` for `SIMPLE`, `1..3` unique non-blank strings for `VARIABLE`)
  - For `SIMPLE`: `defaultVariant` (`@Valid CreateVariantInput`: `sku`, `costPrice >= 0`, `sellingPrice >= 0`, `barcodes[]`)
  - For `VARIABLE`: `variants` (`@NotEmpty`, `@Valid List<CreateVariantInput>`: each with `sku`, optional `name`, `optionValues`, `costPrice`, `sellingPrice`, `barcodes[]`)
- **Validation & Errors**:
  - `201 Created` returning `ProductDetailDto`
  - `400 Bad Request` (`Validation Failed` or invalid `variantOptions` / inactive `categoryId` / inactive `unitId`)
  - `409 Conflict` (duplicate `sku` or duplicate `barcode` in tenant, or duplicate `optionValues` combination within product)

### 3.5 `PUT /api/v1/products/{productId}`
- **Request DTO (`UpdateProductRequest`)**:
  - `name`, `description`, `brand`, `categoryId`, `unitId`, `version` (optional optimistic lock check)
  - For `SIMPLE` products, optionally updates the default variant's `sku`, `costPrice`, `sellingPrice`, `barcodes`.
  - Supports converting `SIMPLE` -> `VARIABLE` when `variantOptions` is supplied (retains existing default `variantId` with updated `optionValues`).
- **Errors**: `400`, `404`, `409` (SKU/barcode collision or optimistic lock conflict).

### 3.6 `PATCH /api/v1/products/{productId}/status`
- **Request DTO**: `{ "status": "DRAFT | ACTIVE | ARCHIVED" }` (`@NotNull`).
- **Errors**: `400` if activating a product with 0 active variants; `404` if not found.

### 3.7 `DELETE /api/v1/products/{productId}`
- **Behavior**: Deletes unused `DRAFT` product physically; archives (`status = ARCHIVED`, `variants.isActive = false`) any `ACTIVE` or historically referenced product.
- **Response (`200 OK`)**: `ApiResponse<ProductDetailDto>` or `ApiResponse<Void>` (recommend returning `ApiResponse<Void>` with message `"Product deleted"` or `"Product archived"` — or returning status summary).

---

## 4. Variants Sub-Resource Contracts (`VARIABLE` Products)

- `POST /api/v1/products/{productId}/variants`: Adds a new variant (`sku`, `name`, `optionValues`, `costPrice`, `sellingPrice`, `barcodes[]`). Rejects `SIMPLE` products (`400 Bad Request`). Returns `201 Created`.
- `PUT /api/v1/products/{productId}/variants/{variantId}`: Updates variant fields & replaces barcodes atomically. Enforces `(tenant_id, sku)`, `(tenant_id, barcode)`, and `(tenant_id, product_id, option_values)` uniqueness (`409 Conflict`).
- `PATCH /api/v1/products/{productId}/variants/{variantId}/status`: `{ "isActive": boolean }`. Rejects deactivating the last active variant of an `ACTIVE` product (`409 Conflict`).
- `DELETE /api/v1/products/{productId}/variants/{variantId}`: Physically deletes if unreferenced and not the last variant of an `ACTIVE` product; otherwise soft-deactivates (`isActive = false`) or returns `409 Conflict` if referenced by downstream Inventory/Sales.

---

## 5. Categories API Contracts

- `GET /api/v1/categories`:
  - Query params: `view=tree|flat` (default `flat`), `includeInactive=false`.
  - `CategoryDto`: `id`, `parentId`, `code`, `name`, `description`, `depth`, `sortOrder`, `isActive`, `children` (populated when `view=tree`), `createdAt`, `updatedAt`, `version`.
- `GET /api/v1/categories/{categoryId}`: Returns `CategoryDto` (`404` if missing/other tenant).
- `POST /api/v1/categories`:
  - Request: `code` (`^[A-Z0-9_-]{2,50}$`, required), `name` (`<= 120`, required), `parentId` (optional UUID), `description` (optional), `sortOrder` (default `0`).
  - Returns `201 Created`. Rejects inactive `parentId` (`400`), depth > 4 (`400`), duplicate `code` or sibling `name` (`409`).
- `PUT /api/v1/categories/{categoryId}`:
  - Request: `name`, `parentId`, `description`, `sortOrder`. (`code` immutable after creation or normalized; keeping `code` immutable matches `Role`).
  - Rejects self-parenting and ancestor cycles (`409 Conflict`), depth > 4 (`400`).
- `PATCH /api/v1/categories/{categoryId}/status`:
  - Request: `{ "isActive": boolean }`.
  - Rejects deactivating a category with active child categories (`409 Conflict`); rejects activating a child whose parent is inactive (`400 Bad Request`).
- `DELETE /api/v1/categories/{categoryId}`:
  - Deletes only if 0 child categories and 0 products reference it; otherwise returns `409 Conflict` (`Category has child categories or assigned products; deactivate it instead`).

---

## 6. Units of Measure API Contracts

- `GET /api/v1/units`: Query param `includeInactive=false`. Returns `List<UnitOfMeasureDto>` (`id`, `code`, `name`, `symbol`, `allowDecimal`, `decimalPrecision`, `isSystem`, `isActive`).
- `POST /api/v1/units`: Creates custom unit (`code` `^[A-Z0-9_-]{1,20}$`, `name`, `symbol`, `allowDecimal`, `decimalPrecision` `0..3`). Returns `201 Created` (`409` on duplicate `code`).
- `PUT /api/v1/units/{unitId}`: Updates custom unit `name` and `symbol` (`isSystem` units reject edits with `403 Forbidden` / `400 Bad Request`; `allowDecimal` / `decimalPrecision` cannot be narrowed if products reference the unit).
- `PATCH /api/v1/units/{unitId}/status`: Activates/deactivates custom unit (`isSystem` units cannot be deactivated).
