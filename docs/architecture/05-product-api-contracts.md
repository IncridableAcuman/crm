# SAVDOCRM — Milestone 05: Catalog REST API Contracts & RBAC Integration

- **Status**: Implemented & Verified (M05.4)
- **Version**: 1.1.0
- **Base URI**: `/api/v1`
- **Controllers**: `ProductController`, `ProductVariantController`, `ProductCategoryController`, `UnitOfMeasureController` (`com.savdocrm.modules.catalog.web`)

---

## 1. Cross-Cutting API & Security Conventions

1. **Tenant Resolution**: Every endpoint requires a tenant-scoped JWT (`JwtAuthenticationFilter` + `TenantContextHolder.getRequiredTenantId()`). Client-supplied `tenantId` parameters are never accepted or trusted.
2. **Response Envelopes**:
   - Single-item, list, and mutation responses use `ApiResponse<T>` (`success`, `data`, `message`, `timestamp`).
   - Paginated list responses (`GET /api/v1/products`) return `ApiResponse<PageResponse<ProductSummaryResponse>>` where `PageResponse` contains `{ items, page, size, totalElements, totalPages, first, last }`.
   - Errors return RFC 7807 `ProblemDetail` (`type`, `title`, `status`, `detail`, `instance`, `timestamp`, and `errors` map for `400` field/parameter validation) via `GlobalExceptionHandler` (`400`, `401`, `403`, `404`, `409`).
3. **RBAC Integration & Scope Semantics**:
   - Uses the **6 existing canonical catalog permissions** from `Permissions.java` without introducing a new permission code:
     - `catalog.products.read`
     - `catalog.products.create`
     - `catalog.products.update`
     - `catalog.products.delete`
     - `catalog.categories.read`
     - `catalog.categories.manage`
   - **Catalog Reads (`GET /products*`, `GET /categories*`, `GET /units*`)**:
     - When optional `?branchId=` is **omitted**, evaluated strictly at `TENANT` scope via `PermissionAuthorizationService.hasPermission(permissionCode)`, ensuring branch-scoped assignments do not grant unrestricted tenant-wide access when `branchId` is absent.
     - When optional `?branchId=` is **provided**, evaluated via `PermissionAuthorizationService.hasBranchPermission(permissionCode, branchId)`, which succeeds for `TENANT`-scoped holders (scope inheritance) or users explicitly assigned that permission on that active branch (wrong, inactive, or cross-tenant branch IDs fail closed with `403 Forbidden`).
   - **Catalog Writes (`POST`, `PUT`, `PATCH`, `DELETE`)**:
     - Evaluated strictly at `TENANT` scope via `PermissionAuthorizationService.hasPermission(...)` because catalog master data is shared across all branches of the tenant.
     - Archiving (`POST /api/v1/products/{productId}/archive` and `PATCH /api/v1/products/{productId}/status` when target `status == ARCHIVED`) and draft deletion (`DELETE /api/v1/products/{productId}`) require `catalog.products.delete` (`OWNER`, `ADMIN`), whereas `MANAGER` holds `catalog.products.update` (create/edit/restore/variants/barcodes) without delete/archive authority.
   - **Units of Measure RBAC**:
     - `GET /api/v1/units*` requires `catalog.products.read` (`hasPermission` when `branchId` is omitted, or `hasBranchPermission` when `?branchId=` is provided).
     - `POST` / `PUT` / `PATCH` / `DELETE` on `/api/v1/units*` reuses `catalog.categories.manage` (`hasPermission`, `TENANT` scope), preserving the 33-permission catalog (`ADR-0010`).

---

## 2. Implemented Endpoint Matrix

| Resource | Method & Path | Permission & Scope | Success Status |
| :--- | :--- | :--- | :--- |
| Products | `GET /api/v1/products` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Products | `GET /api/v1/products/lookup` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Products | `GET /api/v1/products/{productId}` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Products | `POST /api/v1/products` | `catalog.products.create` (`TENANT`) | `201 Created` |
| Products | `PUT /api/v1/products/{productId}` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Products | `PATCH /api/v1/products/{productId}/status` | `catalog.products.delete` (`ARCHIVED`) / `catalog.products.update` (`DRAFT`/`ACTIVE`) (`TENANT`) | `200 OK` |
| Products | `POST /api/v1/products/{productId}/archive` | `catalog.products.delete` (`TENANT`) | `200 OK` |
| Products | `POST /api/v1/products/{productId}/restore` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Products | `POST /api/v1/products/{productId}/convert-to-variable` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Products | `DELETE /api/v1/products/{productId}` | `catalog.products.delete` (`TENANT`) | `200 OK` |
| Variants | `GET /api/v1/products/{productId}/variants` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Variants | `GET /api/v1/products/{productId}/variants/{variantId}` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Variants | `POST /api/v1/products/{productId}/variants` | `catalog.products.update` (`TENANT`) | `201 Created` |
| Variants | `PUT /api/v1/products/{productId}/variants/{variantId}` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Variants | `PATCH /api/v1/products/{productId}/variants/{variantId}/default` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Variants | `PATCH /api/v1/products/{productId}/variants/{variantId}/status` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Barcodes | `GET /api/v1/products/{productId}/variants/{variantId}/barcodes` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Barcodes | `POST /api/v1/products/{productId}/variants/{variantId}/barcodes` | `catalog.products.update` (`TENANT`) | `201 Created` |
| Barcodes | `PUT /api/v1/products/{productId}/variants/{variantId}/barcodes/{barcodeId}` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Barcodes | `PATCH /api/v1/products/{productId}/variants/{variantId}/barcodes/{barcodeId}/primary` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Barcodes | `DELETE /api/v1/products/{productId}/variants/{variantId}/barcodes/{barcodeId}` | `catalog.products.update` (`TENANT`) | `200 OK` |
| Categories | `GET /api/v1/categories` | `catalog.categories.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Categories | `GET /api/v1/categories/tree` | `catalog.categories.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Categories | `GET /api/v1/categories/{categoryId}` | `catalog.categories.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Categories | `POST /api/v1/categories` | `catalog.categories.manage` (`TENANT`) | `201 Created` |
| Categories | `PUT /api/v1/categories/{categoryId}` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Categories | `PATCH /api/v1/categories/{categoryId}/move` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Categories | `PATCH /api/v1/categories/{categoryId}/status` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Categories | `POST /api/v1/categories/{categoryId}/activate` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Categories | `POST /api/v1/categories/{categoryId}/deactivate` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Categories | `DELETE /api/v1/categories/{categoryId}` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Units | `GET /api/v1/units` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Units | `GET /api/v1/units/{unitId}` | `catalog.products.read` (`TENANT` if `branchId` omitted, else `BRANCH`) | `200 OK` |
| Units | `POST /api/v1/units` | `catalog.categories.manage` (`TENANT`) | `201 Created` |
| Units | `PUT /api/v1/units/{unitId}` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Units | `PATCH /api/v1/units/{unitId}/status` | `catalog.categories.manage` (`TENANT`) | `200 OK` |
| Units | `DELETE /api/v1/units/{unitId}` | `catalog.categories.manage` (`TENANT`) | `200 OK` |

---

## 3. Products API Contracts

### 3.1 `GET /api/v1/products` (Database-Level Search, Filtering & Pagination)
- **Query Parameters**:
  - `search` (`String`, optional): Trimmed case-insensitive `LIKE` (with `\`, `%`, `_` escaped) matching `product.name`, `product.brand`, `variant.sku`, or `barcode.barcode`.
  - `sku` (`String`, optional): Uppercase prefix match on `variant.sku`.
  - `barcode` (`String`, optional): Exact trimmed match on `barcode.barcode`.
  - `categoryId` (`UUID`, optional): Category filter (`includeSubcategories=true|false`, default `false`).
  - `unitId` (`UUID`, optional): Unit of measure filter.
  - `brand` (`String`, optional): Case-insensitive exact brand match.
  - `status` (`ProductStatus`, optional): `DRAFT | ACTIVE | ARCHIVED` (by default `ARCHIVED` is excluded unless `status=ARCHIVED`, `isActive=false`, or `includeArchived=true`).
  - `productType` (`ProductType`, optional): `SIMPLE | VARIABLE`.
  - `isActive` (`Boolean`, optional): `true` filters `status = ACTIVE`; `false` filters `status != ACTIVE`.
  - `includeArchived` (`boolean`, default `false`).
  - `branchId` (`UUID`, optional): Branch context check for branch-scoped readers.
  - `page` (`int`, default `0`, `>= 0`), `size` (`int`, default `20`, `1..100`).
  - `sort` (`String`, default `createdAt,desc`; allowlisted fields: `name`, `createdAt`, `updatedAt`, `status`, `brand`, `productType`, always tie-broken by `id ASC`; unsupported fields return `400 Bad Request`).
- **Response (`200 OK`)**: `ApiResponse<PageResponse<ProductSummaryResponse>>`:
  ```json
  {
    "success": true,
    "data": {
      "items": [
        {
          "id": "2d3d2f81-...",
          "name": "Plain Tee",
          "brand": "BasicCo",
          "productType": "SIMPLE",
          "status": "ACTIVE",
          "categoryId": "b8c4f1d2-...",
          "categoryName": "Apparel",
          "unitId": "7a1b2c3d-...",
          "unitCode": "PCS",
          "unitSymbol": "pcs",
          "variantCount": 1,
          "defaultSku": "TEE-001",
          "primaryBarcode": "4601000000011",
          "minSellingPrice": 12.5000,
          "maxSellingPrice": 12.5000,
          "createdAt": "2026-11-25T13:10:00Z",
          "updatedAt": "2026-11-25T13:10:00Z",
          "version": 0
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "first": true,
      "last": true
    },
    "message": "Products retrieved successfully",
    "timestamp": "2026-11-25T13:10:00Z"
  }
  ```

### 3.2 `GET /api/v1/products/lookup`
- **Query Parameters**: `code` (`String`, required non-blank — checks exact `barcode` first, then uppercase `sku` if matching SKU format), optional `branchId`.
- **Response (`200 OK`)**: `ApiResponse<VariantLookupResponse>` (`variantId`, `productId`, `productName`, `variantName`, `sku`, `primaryBarcode`, `optionValues`, `unitId`, `unitCode`, `unitSymbol`, `allowDecimal`, `decimalPrecision`, `referenceCostPrice`, `sellingPrice`, `productStatus`, `variantActive`, `activeDefault`, `sellable`).
- **Errors**: `400` if `code` is missing/blank; `404` if no active/sellable match exists in the active tenant.

### 3.3 `GET /api/v1/products/{productId}` & Mutations
- **Response (`ProductResponse`)**: `id`, `name`, `description`, `brand`, `productType`, `variantOptions`, `status`, `category` (`id`, `code`, `name`), `unit` (`id`, `code`, `name`, `symbol`, `allowDecimal`, `decimalPrecision`), `variants`, `activeDefaultVariantId`, `historicalDefaultVariantId`, `createdAt`, `updatedAt`, `version`.
- **`POST /api/v1/products`** (`CreateProductRequest`):
  ```json
  {
    "categoryId": "b8c4f1d2-...",
    "unitId": "7a1b2c3d-...",
    "name": "Plain Tee",
    "description": "Cotton",
    "brand": "BasicCo",
    "productType": "SIMPLE",
    "variantOptions": [],
    "status": "ACTIVE",
    "variants": [
      {
        "sku": "TEE-001",
        "name": "Default",
        "optionValues": {},
        "costPrice": 5.00,
        "sellingPrice": 12.50,
        "isDefault": true,
        "barcodes": [
          { "barcode": "4601000000011", "barcodeType": "EAN13", "isPrimary": true }
        ]
      }
    ]
  }
  ```
- **`PUT /api/v1/products/{productId}`** (`UpdateProductRequest`): updates `categoryId`, `unitId`, `name`, `description`, `brand`.
- **`PATCH /api/v1/products/{productId}/status`** (`UpdateProductStatusRequest`): `{ "status": "DRAFT | ACTIVE | ARCHIVED" }`.
- **`POST /api/v1/products/{productId}/archive`**, **`POST /api/v1/products/{productId}/restore`**, **`POST /api/v1/products/{productId}/convert-to-variable`** (`ConvertProductToVariableRequest`).
- **`DELETE /api/v1/products/{productId}`**: delegates to `ProductService.deleteDraftProduct` — physically deletes `DRAFT` products (`200 OK`); rejects `ACTIVE`/`ARCHIVED` products (`400 Bad Request`, directing callers to archive instead).

---

## 4. Variants & Barcodes API Contracts

- `POST /api/v1/products/{productId}/variants` (`CreateVariantRequest`): adds variant to `VARIABLE` product (`201 Created`; rejects `SIMPLE` or `ARCHIVED` with `400`).
- `PUT /api/v1/products/{productId}/variants/{variantId}` (`UpdateVariantRequest`): updates variant fields (`200 OK`; rejects `ARCHIVED` product with `400`).
- `PATCH /api/v1/products/{productId}/variants/{variantId}/default`: promotes an active variant to default (`200 OK`; rejects inactive target with `409 Conflict`).
- `PATCH /api/v1/products/{productId}/variants/{variantId}/status` (`UpdateActivationStatusRequest`): `{ "isActive": boolean }` (`200 OK`; rejects deactivating default or sole active variant with `409 Conflict`).
- Barcode endpoints enforce `(productId -> variantId -> barcodeId)` aggregate ownership (`404 Not Found` on mismatch) and `ProductBarcodeService` pessimistic variant/barcode locking (`201`/`200`/`409`).

---

## 5. Categories & Units API Contracts

- **Categories (`/api/v1/categories`)**:
  - `GET /api/v1/categories?view=flat|tree&includeInactive=false&branchId=...` and `GET /api/v1/categories/tree?includeInactive=false&branchId=...` build the full tree from a single flat sorted query (zero N+1 queries).
  - `POST`, `PUT`, `PATCH /move`, `PATCH /status`, `POST /activate`, `POST /deactivate`, `DELETE` delegate directly to `ProductCategoryService` (`201`/`200`/`400`/`404`/`409`).
- **Units of Measure (`/api/v1/units`)**:
  - `GET /api/v1/units?includeInactive=false&branchId=...`, `GET /api/v1/units/{unitId}`, `POST`, `PUT`, `PATCH /status`, `DELETE` delegate directly to `UnitOfMeasureService`.
  - System units reject `PUT`, `PATCH /status`, and `DELETE` with `403 Forbidden` (`SystemUnitImmutableException`).

---

## 6. Error Responses & Known Limitations

- **Error Mapping (`GlobalExceptionHandler`)**:
  - `400 Bad Request`: Bean Validation errors (`MethodArgumentNotValidException`, `ConstraintViolationException`), malformed JSON/enum (`HttpMessageNotReadableException`), invalid UUID/type or missing param (`MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`), invalid sort/pagination arguments (`IllegalArgumentException`), invalid catalog/variant/barcode configuration, inactive UOM/category, max category depth exceeded.
  - `401 Unauthorized`: Missing or invalid JWT (`RestAuthenticationEntryPoint`).
  - `403 Forbidden`: Missing permission, wrong/inactive branch scope, global token without tenant context, suspended user/membership/tenant, or system UOM mutation (`SystemUnitImmutableException`).
  - `404 Not Found`: Missing or cross-tenant product, variant, barcode, category, or unit ID.
  - `409 Conflict`: Duplicate SKU, barcode, category code/sibling name, unit code, duplicate variant option combination, invalid default/primary transition, category cycle, referenced category/unit conflict, optimistic lock failure.
- **Known Limitations**:
  - `DELETE /api/v1/products/{productId}` supports only `DRAFT` deletion per `ProductService.deleteDraftProduct`; `ACTIVE` products must be archived via `POST /archive` or `PATCH /status`.
  - No downstream Inventory/Sales/Purchases reference checks exist yet until M06–M08.
  - Frontend UI (`M05.5`) is implemented (`e95d3d6`), while end-to-end live backend/frontend contract verification (`M05.6`) remains planned.
