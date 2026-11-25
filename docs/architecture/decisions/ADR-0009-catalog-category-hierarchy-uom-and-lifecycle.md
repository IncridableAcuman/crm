# ADR-0009: Category Hierarchy, Unit-of-Measurement Ownership & Product Lifecycle Strategy

- **Status**: Accepted (M05.1 Design)
- **Date**: 2026-11-25

---

## 1. Context & Problem

1. **Category Hierarchy**: Retailers organize products in multi-level categories (`Beverages -> Soft Drinks -> Carbonated`), requiring safe re-parenting, cycle prevention, tenant isolation, and clear rules for deactivation/deletion when products exist.
2. **Unit of Measurement (UOM)**: Products are measured in pieces, weight (`kg`, `g`), volume (`l`, `ml`), length (`m`), or packs (`box`, `pack`). The design must decide whether UOMs are global or tenant-owned, how decimal precision works, and whether pack-to-base conversion belongs in M05.
3. **Product Lifecycle & Deletion**: Deleting a product or category that has historical inventory or sales records would corrupt audit trails and accounting reports.

---

## 2. Considered Options

### Category Hierarchy Options
- **Nested Sets (`lft`/`rgt`) / Closure Table**: Rejected for M05. High write amplification or extra join tables for SMB category trees typically containing 10–500 nodes.
- **Adjacency List (`parent_id`) with Bounded Depth (`0..4`, max 5 levels)**: Selected. Supports composite FK `(tenant_id, parent_id) -> product_categories(tenant_id, id)` in PostgreSQL, loads a tenant's full tree in a single indexed query, and checks cycles in memory/transaction cleanly.

### Unit of Measurement Ownership Options
- **Global Shared UOM Table (`permissions`-style, no `tenant_id`)**: Rejected as sole source because tenants need custom retail units (`ROLL`, `BAG`, `PALLET`), and mixing global (`tenant_id IS NULL`) + tenant rows prevents strict composite FK enforcement `(tenant_id, unit_id)`.
- **Tenant-Owned Seeded UOMs (`tenant_id UUID NOT NULL`)**: Selected (mirroring M04 system roles). Standard system units (`PCS`, `KG`, `G`, `L`, `ML`, `M`, `BOX`, `PACK`) are seeded per tenant (`is_system = true`), tenants can add custom units (`is_system = false`), and `products` enforces `FOREIGN KEY (tenant_id, unit_id) REFERENCES units_of_measure(tenant_id, id) ON DELETE RESTRICT`.

### UOM Conversion Engine
- **Include Multi-Unit Conversion (`1 BOX = 24 PCS`) in M05**: Deferred. Stock ledgers and POS unit-of-sale conversion belong to `Inventory` (M06) and `Purchases` (M08); implementing conversion tables without movement consumers introduces untested dead code.

---

## 3. Decision

1. **Category Hierarchy**: Adjacency list (`parent_id`) bounded to `depth <= 4` (`CHECK` constraint), direct product assignment allowed on any active category, cycle prevention enforced transactionally in `CategoryService`, and `ON DELETE RESTRICT` on both `parent_id` and `products.category_id`.
2. **Unit of Measurement**: Tenant-owned `units_of_measure` with system-seeded standard units (`is_system = true`) + custom tenant units (`is_system = false`), `allow_decimal`, and `decimal_precision` (`0..3` aligned with `NUMERIC(12,3)`). Multi-unit conversions are deferred to M06/M08.
3. **Product Lifecycle & Deletion**:
   - Controlled `ProductStatus` (`DRAFT`, `ACTIVE`, `ARCHIVED`).
   - Physical deletion is permitted only for unreferenced `DRAFT` products; `ACTIVE` or downstream-referenced products/variants transition to `ARCHIVED` / `is_active = false` (`ON DELETE RESTRICT` enforced on downstream FKs in M06+).

---

## 4. Consequences & Deferred Alternatives

- **Positive**:
  - Composite FK guarantees zero cross-tenant category or unit references.
  - Historical inventory, purchase, and sales records remain intact even when categories/units are deactivated or products are archived.
  - Zero overengineered tree maintenance or unused UOM conversion math in M05.
- **Negative**:
  - Re-parenting a category with deep subtrees requires updating descendant `depth` values inside the transaction (bounded by max depth 5, so minimal impact).
  - Multi-unit purchasing (buy by `BOX`, stock/sell by `PCS`) requires adding `product_uom_conversions` in M06/M08.
- **Deferred Alternatives**:
  - Materialized path / `ltree` if catalogs exceed tens of thousands of categories.
  - Multi-UOM conversion table (`product_uom_conversions`) in Inventory/Purchases milestones.
