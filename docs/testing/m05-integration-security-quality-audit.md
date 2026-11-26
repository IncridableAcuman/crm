# SAVDOCRM — Milestone 05: Integration, Security & Quality Audit (M05.6)

- **Milestone**: M05.6 — Integration, Security & Quality Audit
- **Audit Date**: 2026-11-26
- **Baseline Commit**: `5a677e0ffb412b874f84054dc7d4b79b8ad34ec6` (`main`)
- **Final Verdict**: **COMPLETED — PASS WITH LIMITATIONS**

---

## 1. Audit Scope

End-to-end verification of **Milestone 05 — Product Catalog** (`M05.1–M05.5`) across all architectural layers:

$$\text{React 19 FSD Frontend} \longrightarrow \text{Spring Boot REST API} \longrightarrow \text{Spring Security / RBAC} \longrightarrow \text{TenantContextHolder} \longrightarrow \text{PostgreSQL 16}$$

Covered areas:
1. Frontend/backend API contract compatibility (paths, HTTP methods, query parameters, request/response DTOs, pagination, RFC 7807 errors, enums).
2. Authentication, session lifecycle, CSRF Origin enforcement, and tenant switching.
3. Multi-tenant data isolation and tenant-spoofing resistance.
4. Branch-scoped read authorization (`?branchId=`) vs tenant-scoped write authorization.
5. RBAC enforcement across the 6 canonical `catalog.*` permissions.
6. End-to-end Product, Variant, Barcode, Category, and Unit of Measure business workflows.
7. Live HTTP/CORS/SPA/PostgreSQL full-stack verification.
8. TanStack Query tenant and branch cache isolation.
9. Full backend, frontend, Liquibase migration, and ArchUnit regression suites.

**Explicit Exclusions**: No Inventory, Warehouse, Stock Ledger, Sales, Purchases, or M06 functionality was implemented.

---

## 2. Environment & Versions

| Component | Verified Version / Configuration |
| :--- | :--- |
| **OS** | Linux |
| **Java** | OpenJDK 21.0.13.0.3 LTS |
| **Gradle Wrapper** | 9.9.1 |
| **Spring Boot** | 4.2.0 |
| **PostgreSQL (Testcontainers & Ephemeral Live Audit)** | `postgres:16-alpine` |
| **Node.js / npm** | Node `v24.13.0`, npm `11.6.2` |
| **Frontend Stack** | React `19.0.0`, TypeScript `5.7.2` (`strict: true`, `noUncheckedIndexedAccess: true`), Vite `6.0.3` / `6.4.6`, Vitest `5.1.1` |
| **Browser E2E Tooling** | None installed on host (`/usr/bin/firefox` is an uninstalled Ubuntu snap stub; Playwright/Chromium/Selenium not present) |

---

## 3. Test Infrastructure Used

- **Backend Unit & Mockito Suites**: JUnit 5 + Mockito (`Product*Test`, `*ServiceTest`, `ArchitectureTest`).
- **Backend PostgreSQL Integration Suites**: `@SpringBootTest` + `@Testcontainers` (`postgres:16-alpine`) + `MockMvc` + Spring Security (`CatalogDatabaseMigrationIntegrationTest`, `CatalogIntegrationTest`, `CatalogConcurrencyIntegrationTest`, `TenantUomBootstrapRollbackIntegrationTest`, `CatalogWebApiIntegrationTest`, `CatalogRbacSecurityIntegrationTest`, `CatalogAuditIntegrationTest`).
- **Frontend Unit, Hook & Component Suites**: Vitest `5.1.1` + React Testing Library + `jsdom` (`20` test files, `76` tests, including `catalogCacheIsolation.test.tsx` added in M05.6).
- **Live Full-Stack Verification**: Isolated ephemeral `postgres:16-alpine` container (port `15434`, leaving developer database untouched), compiled Spring Boot JAR (`savdocrm-backend-0.0.1-SNAPSHOT.jar` on port `18080`), Vite preview server (`127.0.0.1:13000`), and live Python HTTP/cookie/CORS client verification.

---

## 4. Backend Integration Results

| Area | Result | Evidence |
| :--- | :--- | :--- |
| **Authentication & Session Lifecycle** | **PASS** | `CatalogAuditIntegrationTest` & `AuthIntegrationTest`: valid login (`200`), invalid credentials (`401`), invalid/expired JWT (`401`), global token blocked on catalog (`403`), valid `select-tenant` (`200`), unauthorized/missing tenant on `select-tenant` (`403`/`400`), logout cookie revocation (`401` on subsequent `/auth/refresh`). |
| **Suspended Entities** | **PASS** | `CatalogAuditIntegrationTest` & `CatalogRbacSecurityIntegrationTest`: suspended tenant rejected on live requests & `select-tenant` (`403`); suspended user (`403`); suspended membership (`403`). |
| **Tenant Spoofing Defense** | **PASS** | `CatalogAuditIntegrationTest`: sending `X-Tenant-Id: <tenantB>`, `?tenantId=<tenantB>`, and JSON body `"tenantId": "<tenantB>"` while authenticated as Tenant A is ignored; resources resolve strictly from authenticated JWT `TenantContextHolder`. |
| **Validation & RFC 7807 Errors** | **PASS** | `CatalogWebApiIntegrationTest`: malformed JSON, invalid UUIDs, negative prices, `NUMERIC(15,4)` overflow, unsafe sort injection (`sort=tenantId,asc`), and oversized page (`size=101`) return `400 Bad Request` with `ProblemDetail`. |

---

## 5. Tenant Isolation Results

| Check | Result | Evidence |
| :--- | :--- | :--- |
| **Cross-Tenant Reads & Search** | **PASS** | `CatalogRbacSecurityIntegrationTest`, `CatalogAuditIntegrationTest`, `CatalogIntegrationTest`: Tenant B receives empty search/tree results for Tenant A data and `404 Not Found` on Tenant A product, variant, barcode, category, unit IDs, and barcode/SKU lookup. |
| **Cross-Tenant Mutations & Deletes** | **PASS** | `CatalogAuditIntegrationTest`: Tenant B `PUT`, `POST /archive`, `DELETE` against Tenant A products, variants, barcodes, categories, and custom units all fail closed with `404 Not Found` without mutating Tenant A state. |
| **Database Composite FK Isolation** | **PASS** | `CatalogDatabaseMigrationIntegrationTest`: PostgreSQL rejects cross-tenant category, unit, product, variant, and barcode FKs and enforces `ON DELETE RESTRICT` across all 10 catalog FKs. |

---

## 6. Branch Authorization Results

| Check | Result | Evidence |
| :--- | :--- | :--- |
| **TENANT-scoped Readers** | **PASS** | `CatalogRbacSecurityIntegrationTest`: `VIEWER` can read catalog GETs without `branchId` (`200`) and with active `branchId` (`200`), and is denied on inactive `branchId` (`403`). |
| **Authorized BRANCH Readers** | **PASS** | `CatalogRbacSecurityIntegrationTest` & `CatalogAuditIntegrationTest`: `BRANCH`-scoped `CASHIER` and `MANAGER` succeed (`200`) across `products`, `products/{id}`, `lookup`, `variants`, `barcodes`, `categories`, `categories/tree`, `categories/{id}`, `units`, and `units/{id}` when passing their assigned active `branchId`. |
| **Fail-Closed Branch Guards** | **PASS** | `CatalogRbacSecurityIntegrationTest`: omitting `branchId` (`403`), passing another active branch (`403`), passing an inactive branch (`403`), or passing another tenant's branch (`403`) all fail closed without fallback. |
| **Branch-Only Write Prevention** | **PASS** | `CatalogRbacSecurityIntegrationTest` & `CatalogAuditIntegrationTest`: `BRANCH`-scoped `MANAGER` is denied (`403`) on catalog writes (`POST`/`PUT`/`PATCH`/`DELETE`) for products, categories, and units. |

---

## 7. RBAC Results

| Check | Result | Evidence |
| :--- | :--- | :--- |
| **6 Canonical `catalog.*` Permissions** | **PASS** | `CatalogRbacSecurityIntegrationTest`: `catalog.products.{read,create,update,delete}` and `catalog.categories.{read,manage}` enforced on all endpoints; custom create-only role cannot update (`403`); `MANAGER` cannot archive/delete (`403`) or manage categories/units (`403`); `ADMIN`/`OWNER` can archive/restore (`200`). |
| **Edge Cases** | **PASS** | `CatalogAuditIntegrationTest`: inactive role assignment (`403`), no-role user (`403`), and unknown permission `catalog.unknown.action` (`false`) fail closed. Zero new permission codes added. |

---

## 8. Product Workflow Results

| Workflow | Result | Evidence |
| :--- | :--- | :--- |
| **5.1 Product Lifecycle** | **PASS** | `CatalogWebApiIntegrationTest` & `CatalogAuditIntegrationTest`: create `DRAFT SIMPLE` (`201`) → get (`200`) → update (`200`) → activate (`200`) → archive (`200`) → restore (`200`) → delete `DRAFT` (`200`); delete `ACTIVE` (`400`), `ARCHIVED → DRAFT` (`400`), `restore` on `ACTIVE` (`400`) rejected. |
| **5.2 Variants** | **PASS** | `CatalogIntegrationTest` & `CatalogAuditIntegrationTest`: create `VARIABLE` variants, reject >1 explicit default (`409`), update variant, change default atomically, reject deactivating sole active variant (`409`), convert `SIMPLE → VARIABLE` (`200`), reject invalid re-conversion (`400`). |
| **5.3 Barcodes** | **PASS** | `CatalogWebApiIntegrationTest`, `CatalogAuditIntegrationTest`, `ProductVariantAndBarcodeServiceTest`: add/list/update/primary-swap/remove (`201`/`200`), lookup by barcode (`200`), duplicate barcode (`409`), cross-tenant lookup (`404`), aggregate ownership mismatch (`404`), and archived product barcode mutation blocked (`400` — fixed in M05.6). |
| **5.4 Categories** | **PASS** | `CatalogIntegrationTest` & `CatalogAuditIntegrationTest`: 5-level hierarchy (`depth 0..4`), reject 6th level (`400`), tree retrieval, move, cyclic move (`409`), move under inactive parent (`400`), activate under inactive parent (`400`), delete with child or product (`409`), delete unused leaf (`200`). |
| **5.5 Units of Measure** | **PASS** | `CatalogWebApiIntegrationTest` & `CatalogAuditIntegrationTest`: 8 system units seeded per tenant, system unit mutations rejected (`403`), custom unit create/update/deactivate/reactivate/delete (`201`/`200`), precision narrowing on referenced unit rejected (`409`), delete of referenced unit rejected (`409`). |

---

## 9. Frontend/Backend Contract & Live Integration Results

- **API Contract Comparison**: **PASS** — Verified field-by-field parity between `ProductController`, `ProductVariantController`, `ProductCategoryController`, `UnitOfMeasureController`, `PageResponse<T>`, `GlobalExceptionHandler` (`ProblemDetail`), and frontend `productApi.ts`, `categoryApi.ts`, `unitApi.ts`, `product/model/types.ts`, `category/model/types.ts`, `unit/model/types.ts`, and `errorHandler.ts`.
- **Live Stack Execution (`127.0.0.1:13000` Vite preview + `127.0.0.1:18080` Spring Boot + `15434` PostgreSQL 16)**: **PASS**
  - Vite SPA HTML served (`200 OK`).
  - CORS preflight allows `http://localhost:3000` with credentials and rejects `https://evil.example`.
  - Live registration, `HttpOnly` refresh cookie handling (no `refreshToken` in JSON body), login → global token rejection (`403`) → `select-tenant` (`200`), custom unit & root category creation, `SIMPLE` product creation, search pagination, barcode lookup, archive + archived barcode lock (`400`), restore, cross-tenant `404`, and logout session revocation (`401` on refresh) all passed against live PostgreSQL 16.
- **Browser DOM E2E Automation**: **NOT TESTED** (no browser binary / Playwright / Selenium installed on host; UI behavior verified via Vitest + RTL + jsdom `76/76` tests).

---

## 10. Cache Isolation Results

- **Result**: **PASS** (verified in `catalogBranchScope.test.tsx`, `catalogCacheIsolation.test.tsx`, `tenantSwitching.test.ts`, and code review of [TenantSelectPage.tsx](file:///home/izzatbek/Desktop/crm/frontend/src/pages/auth/TenantSelectPage.tsx) and [authApi.ts](file:///home/izzatbek/Desktop/crm/frontend/src/features/auth/api/authApi.ts)).
- **Verified Behaviors**:
  - All catalog query keys include `tenantId` (`['catalog', tenantId, ...]`) and `branchId`.
  - Switching tenants runs `queryClient.cancelQueries()` + `queryClient.clear()`; late in-flight Tenant A responses cannot repopulate Tenant B's active cache (`catalogCacheIsolation.test.tsx`).
  - Branch switching separates query entries per `branchId`; unauthorized `branchId` fails closed without firing HTTP GETs (`catalogBranchScope.test.tsx`).
  - Logout cancels queries, clears Zustand in-memory auth, clears `queryClient`, broadcasts `AUTH_CLEARED`, and never persists tokens in `localStorage` or `sessionStorage`.

---

## 11. Regression Test Totals

| Suite | Command | Result |
| :--- | :--- | :--- |
| **Backend Full Build & Test Suite** | `cd backend && ./gradlew clean build` | **PASS** — `46` test suites, **`276/276` tests passing** (`0` failures, `0` errors, `0` skipped), including `9/9` `ArchitectureTest` rules and `25/25` `CatalogDatabaseMigrationIntegrationTest` checks. |
| **Frontend Test Suite** | `cd frontend && npm test -- --run` | **PASS** — `20` test files, **`76/76` tests passing** (`0` failures, `0` skipped). |
| **Frontend TypeScript & Build** | `cd frontend && npm run build` | **PASS** — `tsc && vite build` exited `0` (zero TS errors; non-fatal Vite chunk-size warning `742.66 kB`). |

---

## 12. Defects Found

1. **Defect M05.6-01 (Medium Severity): `ProductBarcodeService` permitted barcode mutations on `ARCHIVED` products**
   - **Reproduction**: Archive an active product (`POST /api/v1/products/{id}/archive`), then call `POST /api/v1/products/{id}/variants/{variantId}/barcodes` (or `PUT`, `PATCH /primary`, `DELETE`).
   - **Root Cause**: `ProductVariantService` enforced `assertNotArchived(product)` on all variant mutations, and the UI (`VariantEditorSection.tsx`) locked barcode actions for `ARCHIVED` products, but `ProductBarcodeService` only locked `ProductVariant` and never checked the parent `Product`'s `status`.
   - **Impact**: Direct REST API callers could mutate barcode assignments of frozen `ARCHIVED` products, bypassing the product archive invariant.

---

## 13. Fixes & Regression Tests

- **Fix for M05.6-01**: Updated [ProductBarcodeService.java](file:///home/izzatbek/Desktop/crm/backend/src/main/java/com/savdocrm/modules/catalog/application/ProductBarcodeService.java) to inject `ProductRepository`, acquire the parent `Product` lock (`Product -> ProductVariant -> ProductBarcode` order) via `resolveAndLockActiveProduct`, and throw `InvalidProductConfigurationException` (`400 Bad Request`) when `product.isArchived()`.
- **Regression Tests Added**:
  - Updated [ProductVariantAndBarcodeServiceTest.java](file:///home/izzatbek/Desktop/crm/backend/src/test/java/com/savdocrm/modules/catalog/application/ProductVariantAndBarcodeServiceTest.java) to verify archived barcode mutation rejection at the service layer.
  - Added [CatalogAuditIntegrationTest.java](file:///home/izzatbek/Desktop/crm/backend/src/test/java/com/savdocrm/modules/catalog/web/CatalogAuditIntegrationTest.java) (`3` comprehensive MockMvc + PostgreSQL 16 integration tests covering auth, spoofing, cross-tenant mutations, branch writes, lifecycle invariants, and archived barcode rejection).
  - Added [catalogCacheIsolation.test.tsx](file:///home/izzatbek/Desktop/crm/frontend/src/entities/permission/model/catalogCacheIsolation.test.tsx) (`2` Vitest tests verifying late in-flight tenant-switch cancellation and logout catalog cache purge).

---

## 14. Known Limitations

- **No Headless Browser E2E Automation**: No browser binary or Playwright/Selenium framework is installed in the host environment; browser DOM rendering and interactions remain verified via mocked Vitest + RTL (`76/76`), while live backend/CORS/PostgreSQL/Vite integration was verified via HTTP automation.
- **ESLint Config Absent**: `frontend/` does not include a repository ESLint config (`npm run lint` falls back to global ESLint); static type safety is enforced by `tsc` (`strict: true`, `noUncheckedIndexedAccess: true`) with zero `any` types in catalog code.
- **Vite Bundle Chunk Warning**: `vite build` produces a non-fatal chunk-size warning (`742.66 kB` JS bundle); route-based code splitting is deferred to performance hardening.
- **Deferred M06+ Scope**: Product media/image uploads (S3/MinIO), inventory stock balances & WAC (`Inventory`), multi-unit UOM conversions (`Inventory`/`Purchases`), and branch price books/taxes (`Sales`/`Finance`).

---

## 15. Tests Not Executed & Reasons

| Test / Check | Status | Reason |
| :--- | :--- | :--- |
| **Headless Browser DOM E2E (Playwright / Cypress / Selenium)** | **NOT TESTED** | No browser binary (`/usr/bin/firefox` is an uninstalled snap stub; Chromium/Chrome absent) or E2E framework installed on the host, and adding unrelated external browser dependencies is excluded by M05.6 scope. |
| **ESLint (`npm run lint`)** | **NOT TESTED** | No ESLint configuration file or ESLint devDependency exists in `frontend/`. |

---

## 16. Final Security Assessment

- **Authentication & Session Hygiene**: Strong. Dual-phase JWT + `HttpOnly` `SameSite=Lax` refresh cookie + strict single-use rotation + CSRF `Origin`/`Referer` check; zero token persistence in `localStorage`/`sessionStorage`.
- **Tenant & Branch Isolation**: Verified end-to-end. Server-side `TenantContextHolder` ignores `X-Tenant-Id` headers, query params, and body `tenantId`; PostgreSQL composite FKs enforce `(tenant_id, ...)` boundaries; branch-only readers fail closed (`403`) without valid active `?branchId=`, and catalog writes enforce `TENANT` scope.
- **Residual Risk**: Low for M05 scope. Only missing live browser-rendered UI E2E automation due to host browser absence.

---

## 17. Final M05.6 Verdict

**COMPLETED — PASS WITH LIMITATIONS**

All executed acceptance criteria across backend integration (`276/276`), frontend tests (`76/76`) & production build, live Spring Boot + PostgreSQL 16 + Vite integration, tenant isolation, branch authorization, RBAC, catalog workflows, cache isolation, and ArchUnit architecture integrity (`9/9`) passed after the final fix, with zero unresolved critical/high/medium defects. The sole limitation is that headless browser DOM E2E and ESLint could not execute due to missing host browser/ESLint tooling.
