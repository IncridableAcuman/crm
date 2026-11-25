# ADR-0008: Catalog Product Aggregate, Variant Model, SKU/Barcode Uniqueness & Attribute Strategy

- **Status**: Accepted (M05.1 Design)
- **Date**: 2026-11-25

---

## 1. Context & Problem

SAVDOCRM serves diverse retail verticals (grocery, clothing/footwear, electronics, cosmetics, building materials, general retail). Modeling products faces four tightly coupled architectural challenges:

1. **Product vs. Variant Identity**: A grocery item needs a flat form (1 SKU), whereas a T-shirt or smartphone has multiple sellable combinations (Size/Color, Storage/Color). If `Inventory` and `Sales` link to `product_id` initially and variants are added later, downstream modules suffer a breaking foreign-key redesign.
2. **Attribute Representation**: Different industries need different variant axes (`Size`, `Color`, `Storage`, `Shade`, `Grade`) without creating vertical-specific Java classes or EAV join explosions.
3. **SKU & Barcode Uniqueness**: Retail items often have multiple barcodes (manufacturer EAN-13, inner-pack UPC, store label) and zero barcodes for weighed/bulk items. Empty strings or comma-separated barcode lists cause POS scan collisions.
4. **Aggregate Boundary**: Creating a `SIMPLE` product or validating `VARIABLE` option consistency requires transactional atomicity.

---

## 2. Considered Options

### Variant & Aggregate Options
- **Option A — Simple Products Only (`products` table only)**: Rejected. Forces clothing stores to create 12 unrelated product rows per T-shirt and breaks downstream Inventory/Sales foreign keys when variants are added later.
- **Option B — Unified `Product` + `ProductVariant` + `ProductBarcode` (`SIMPLE` | `VARIABLE`)**: Selected. Every `Product` owns `1..N` `ProductVariant` entities (`SIMPLE` auto-manages 1 default variant `is_default = true`; `VARIABLE` manages `1..N` option variants). Downstream modules (`Inventory`, `Sales`, `Purchases`) **always** reference `variant_id`.
- **Option C — Full Dynamic Attribute Definition & Matrix Configurator (`attribute_definitions`, `attribute_values`, `variant_attribute_values`)**: Deferred/Rejected for M05. Adds 4+ join tables and high admin setup friction before a store can add its first T-shirt.

### Attribute Storage Options
- **EAV Join Tables**: Rejected due to query overhead on list/POS lookups.
- **Unconstrained `JSONB` for all product fields**: Rejected because core searchable columns (`sku`, `barcode`, `brand`, `category_id`, `status`) must remain typed and B-Tree indexed.
- **Bounded `JSONB` (`variant_options` on `products`, `option_values` on `product_variants`)**: Selected. PostgreSQL `CHECK` constraints enforce `jsonb_typeof`, max 3 option axes, and `UNIQUE (tenant_id, product_id, option_values)` guarantees no duplicate variant combinations.

### Barcode Modeling Options
- **Single nullable `barcode` column on `product_variants`**: Rejected because grocery/retail SKUs often carry multiple valid supplier/pack barcodes.
- **Comma-separated `barcodes` text column**: Rejected because it breaks `UNIQUE (tenant_id, barcode)` constraints and B-Tree POS scan lookups.
- **Dedicated `product_barcodes` table (`0..N` per variant)**: Selected with `UNIQUE (tenant_id, barcode)` and partial unique index for `is_primary = true`.

---

## 3. Decision

1. **Aggregate Root**: `Product` is the aggregate root owning `ProductVariant` (`1..N`) and `ProductBarcode` (`0..N` per variant) within the `com.savdocrm.modules.catalog` bounded context.
2. **Variant Strategy (`SIMPLE` vs `VARIABLE`)**:
   - `SIMPLE`: Exactly 1 default `ProductVariant` (`isDefault = true`, `optionValues = {}`), presented as a flat single-screen form in the UI. Can be upgraded non-destructively to `VARIABLE` later while preserving `variant_id`.
   - `VARIABLE`: `1..3` option axes in `Product.variantOptions` and matching key-value maps in `ProductVariant.optionValues`.
3. **SKU & Barcode Uniqueness**:
   - Mandatory normalized uppercase `sku` per `ProductVariant` (`UNIQUE (tenant_id, sku)`).
   - Optional normalized non-blank barcodes in `product_barcodes` (`UNIQUE (tenant_id, barcode)`). Different tenants can share identical manufacturer EAN-13 codes; one tenant cannot bind the same barcode to two variants.
4. **Attributes**: `brand` is a typed optional `VARCHAR(100)` column on `products`; variant differentiating options use bounded validated `JSONB`.

---

## 4. Consequences & Deferred Alternatives

- **Positive**:
  - Zero `if (hasVariants)` branching in `Inventory`, `Sales`, or `Purchases` — all downstream tables link strictly to `(tenant_id, variant_id)`.
  - Sub-millisecond POS barcode lookup via B-Tree `UNIQUE (tenant_id, barcode)`.
  - Non-breaking upgrade path from `SIMPLE` to `VARIABLE`.
- **Negative**:
  - `SIMPLE` product writes touch both `products` and `product_variants` inside one transaction (negligible overhead).
  - Global faceted attribute filtering across arbitrary option values is not prioritized in M05.
- **Deferred Alternatives**:
  - Dedicated `brands`, `attribute_definitions`, and `attribute_values` normalized tables if future B2B/e-commerce storefront faceting requires cross-product attribute governance.
