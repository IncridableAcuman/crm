# SAVDOCRM — Milestone 05: Catalog Frontend FSD Architecture

- **Status**: Frontend Architecture Design Approved (M05.1)
- **Version**: 1.0.0
- **Target Implementation**: Milestone 05.5 (*not yet implemented*)
- **FSD Stack**: React 19, TypeScript strict (`noUncheckedIndexedAccess`), Vite 6, Tailwind CSS, shadcn/ui, React Router, TanStack Query v5, React Hook Form + Zod, Axios.

---

## 1. Feature-Sliced Design (FSD) Layout

Following SAVDOCRM's strict unidirectional FSD rule ($\text{app} \rightarrow \text{pages} \rightarrow \text{widgets} \rightarrow \text{features} \rightarrow \text{entities} \rightarrow \text{shared}$):

```
frontend/src/
├── app/
│   └── App.tsx                          # Routes for /catalog/products, /catalog/products/new, /catalog/products/:id, /catalog/categories
├── pages/
│   └── catalog/
│       ├── ProductsListPage.tsx         # Paginated product catalog table, filters, status/archive actions
│       ├── ProductDetailPage.tsx        # Product summary, variants matrix, barcodes, edit/status controls
│       ├── ProductFormPage.tsx          # Create / Edit product flow (flat form for SIMPLE, matrix editor for VARIABLE)
│       └── CategoriesPage.tsx           # Hierarchical category tree + unit of measure management drawer/tab
├── widgets/
│   ├── app-sidebar/AppSidebar.tsx       # Enable Catalog & Categories nav links gated by catalog.* permissions
│   ├── product-table/                   # Searchable, paginated product data table widget
│   └── category-tree/                   # Indented hierarchical category viewer & selector widget
├── features/
│   └── catalog/
│       ├── product-form/                # Zod schemas + React Hook Form for SIMPLE & VARIABLE products
│       ├── variant-editor/              # Add/edit/toggle variant rows and inline barcode chips
│       ├── category-form/               # Create/edit category dialog with cycle-safe parent selector
│       └── unit-manager/                # View system units & create/toggle custom UOMs
└── entities/
    ├── product/
    │   ├── model/types.ts               # ProductSummary, ProductDetail, ProductVariant, ProductBarcode, ProductType, ProductStatus
    │   ├── model/productQueries.ts      # Tenant-scoped queryKey factory + TanStack Query hooks
    │   ├── api/productApi.ts            # Axios wrappers for /products and /products/{id}/variants
    │   └── index.ts
    ├── category/
    │   ├── model/types.ts               # Category, CreateCategoryRequest, UpdateCategoryRequest
    │   ├── model/categoryQueries.ts     # Tenant-scoped queryKey factory + tree/flat hooks
    │   ├── api/categoryApi.ts           # Axios wrappers for /categories
    │   └── index.ts
    └── unit/
        ├── model/types.ts               # UnitOfMeasure, CreateUnitRequest
        ├── model/unitQueries.ts         # Tenant-scoped queryKey factory + hooks
        ├── api/unitApi.ts               # Axios wrappers for /units
        └── index.ts
```

---

## 2. Layer Responsibilities & Boundaries

| Layer | Responsibility | Strict Rules |
| :--- | :--- | :--- |
| **`entities/product`, `category`, `unit`** | Owns domain TypeScript DTOs, Axios API functions, and TanStack Query factories/mutations. | Query keys MUST include `tenantId` (`['catalog', tenantId, 'products', ...]`). Mutations invalidate affected tenant queries cleanly. |
| **`features/catalog/*`** | Encapsulates user workflows (Zod validation, RHF forms, variant option matrix generation, barcode chip input, RFC7807 `parseApiError` field error mapping). | No direct `apiClient` calls; calls entity hooks only. |
| **`widgets/*`** | Composes reusable UI blocks (`ProductTable`, `CategoryTree`, `AppSidebar` navigation). | Domain-specific composition without owning router state. |
| **`pages/catalog/*`** | Route-level orchestration, URL search param sync (`?search=`, `?categoryId=`, `?page=`), loading/empty/error states. | Zero raw Axios calls; zero business logic duplication. |

---

## 3. Routing & RBAC Guard Integration

Consistent with M04.5 [PermissionRoute.tsx](file:///home/izzatbek/Desktop/crm/frontend/src/app/routes/PermissionRoute.tsx) and [PermissionGuard.tsx](file:///home/izzatbek/Desktop/crm/frontend/src/entities/permission/ui/PermissionGuard.tsx):

| Route | Route Guard (`PermissionRoute`) | Action Guards (`PermissionGuard`) |
| :--- | :--- | :--- |
| `/catalog/products` | `catalog.products.read` (`allowBranchScope={true}`) | Create (`catalog.products.create`), Edit/Status (`catalog.products.update`), Delete/Archive (`catalog.products.delete`) |
| `/catalog/products/new` | `catalog.products.create` (tenant scope) | — |
| `/catalog/products/:id` | `catalog.products.read` (`allowBranchScope={true}`) | Edit / Add Variant (`catalog.products.update`), Delete/Archive (`catalog.products.delete`) |
| `/catalog/categories` | `catalog.categories.read` (`allowBranchScope={true}`) | Create / Edit / Toggle / Delete Category & Custom Units (`catalog.categories.manage`) |

*Security Note*: Route and element guards remain strictly UX controls; the backend enforces `@PreAuthorize` on every endpoint.

---

## 4. State Management, Forms & UX States

1. **Server State (TanStack Query v5)**:
   - `staleTime`: `60_000` ms for product lists/details, `5 * 60_000` ms for categories & units.
   - **Tenant Isolation**: Every query key is prefixed with `activeTenant?.tenantId`; `TenantSelectPage` and `logout` continue executing `queryClient.cancelQueries()` + `queryClient.clear()`.
   - URL query parameters (`search`, `categoryId`, `status`, `page`) drive query keys on `ProductsListPage` so browser back/forward and deep links work cleanly.
2. **Client State (Zustand)**:
   - No Catalog server data is ever copied into Zustand. Only transient UI controls (e.g., expanded category tree nodes) use local React state.
3. **Forms & Validation (React Hook Form + Zod)**:
   - `SIMPLE` mode shows a single unified form (name, category, unit, SKU, barcodes, reference cost price, selling price).
   - `VARIABLE` mode exposes up to 3 option names (e.g. `Size`, `Color`) and an editable variant rows table with inline SKU, price, and barcode tags.
   - Decimal precision of price/quantity inputs respects selected `UnitOfMeasure` (`allowDecimal`, `decimalPrecision`) and monetary formatting (`BigDecimal`-compatible string/numeric validation).
   - RFC7807 `400` field errors and `409` SKU/barcode/option collision errors map via `parseApiError` (`src/shared/api/errorHandler.ts`) directly onto form fields / alert banners.
4. **Loading, Empty & Error States**:
   - All pages render skeleton/spinner loading states (never misleading `0` metrics), descriptive empty states with permission-gated CTA buttons, and Retry actions on error.
