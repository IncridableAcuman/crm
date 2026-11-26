package com.savdocrm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class InventoryDatabaseMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_inventory_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID branchAId;
    private UUID branchBId;
    private UUID whA1Id;
    private UUID whA2Id;
    private UUID whB1Id;
    private UUID locA1StorageId;
    private UUID locA1DamagedId;
    private UUID locA2StorageId;
    private UUID locB1StorageId;
    private UUID variantAId;
    private UUID variantBId;
    private UUID userId;
    private UUID membershipAId;
    private UUID roleAId;

    @BeforeEach
    void setUp() {
        cleanAllTables();

        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();
        branchAId = UUID.randomUUID();
        branchBId = UUID.randomUUID();
        whA1Id = UUID.randomUUID();
        whA2Id = UUID.randomUUID();
        whB1Id = UUID.randomUUID();
        locA1StorageId = UUID.randomUUID();
        locA1DamagedId = UUID.randomUUID();
        locA2StorageId = UUID.randomUUID();
        locB1StorageId = UUID.randomUUID();
        variantAId = UUID.randomUUID();
        variantBId = UUID.randomUUID();
        userId = UUID.randomUUID();
        membershipAId = UUID.randomUUID();
        roleAId = UUID.randomUUID();

        insertTenant(tenantAId, "Inv Tenant A", "inv-a-" + tenantAId.toString().substring(0, 6));
        insertTenant(tenantBId, "Inv Tenant B", "inv-b-" + tenantBId.toString().substring(0, 6));

        insertBranch(branchAId, tenantAId, "Branch A", "BR-A");
        insertBranch(branchBId, tenantBId, "Branch B", "BR-B");

        insertUser(userId, "inv@savdocrm.test");
        jdbcTemplate.update(
                "INSERT INTO tenant_memberships (id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                membershipAId, tenantAId, userId
        );
        jdbcTemplate.update(
                "INSERT INTO roles (id, tenant_id, code, name, is_system, is_active) VALUES (?, ?, 'WH_MGR_TEST', 'WH Test', false, true)",
                roleAId, tenantAId
        );

        insertProductAndVariant(tenantAId, variantAId, "SKU-INV-A");
        insertProductAndVariant(tenantBId, variantBId, "SKU-INV-B");

        insertWarehouse(whA1Id, tenantAId, branchAId, "WH-A1", "Store Warehouse A1", "RETAIL_STORE", true);
        insertWarehouse(whA2Id, tenantAId, null, "WH-HUB-A", "Central Hub A", "CENTRAL_HUB", false);
        insertWarehouse(whB1Id, tenantBId, branchBId, "WH-B1", "Store Warehouse B1", "RETAIL_STORE", true);

        insertLocation(locA1StorageId, tenantAId, whA1Id, "DEFAULT", "Main Storage", "STORAGE", true);
        insertLocation(locA1DamagedId, tenantAId, whA1Id, "DMG-01", "Damaged Cage", "DAMAGED", false);
        insertLocation(locA2StorageId, tenantAId, whA2Id, "DEFAULT", "Hub Storage", "STORAGE", true);
        insertLocation(locB1StorageId, tenantBId, whB1Id, "DEFAULT", "B1 Storage", "STORAGE", true);
    }

    private void cleanAllTables() {
        jdbcTemplate.execute("DELETE FROM inventory_reservation_lines");
        jdbcTemplate.execute("DELETE FROM inventory_reservations");
        jdbcTemplate.execute("DELETE FROM stock_transfer_lines");
        jdbcTemplate.execute("DELETE FROM stock_transfers");
        jdbcTemplate.execute("UPDATE stock_counts SET generated_adjustment_id = NULL");
        jdbcTemplate.execute("DELETE FROM stock_adjustment_lines");
        jdbcTemplate.execute("DELETE FROM stock_adjustments");
        jdbcTemplate.execute("DELETE FROM stock_count_lines");
        jdbcTemplate.execute("DELETE FROM stock_counts");
        jdbcTemplate.execute("DELETE FROM stock_receipt_lines");
        jdbcTemplate.execute("DELETE FROM stock_receipts");
        jdbcTemplate.execute("DELETE FROM inventory_transaction_lines");
        jdbcTemplate.execute("DELETE FROM inventory_transactions");
        jdbcTemplate.execute("DELETE FROM inventory_costs");
        jdbcTemplate.execute("DELETE FROM inventory_warehouse_stocks");
        jdbcTemplate.execute("DELETE FROM inventory_balances");
        jdbcTemplate.execute("DELETE FROM user_role_assignments");
        jdbcTemplate.execute("DELETE FROM role_permissions");
        jdbcTemplate.execute("DELETE FROM roles");
        jdbcTemplate.execute("DELETE FROM tenant_memberships");
        jdbcTemplate.execute("DELETE FROM stock_locations");
        jdbcTemplate.execute("DELETE FROM warehouses");
        jdbcTemplate.execute("DELETE FROM idempotency_records");
        jdbcTemplate.execute("DELETE FROM product_barcodes");
        jdbcTemplate.execute("DELETE FROM product_variants");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM product_categories");
        jdbcTemplate.execute("DELETE FROM units_of_measure");
        jdbcTemplate.execute("DELETE FROM branches");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Nested
    @DisplayName("1. Schema Completeness & M06 Tables")
    class SchemaCompletenessTests {

        @Test
        @DisplayName("All 18 new M06 tables exist and changesets v1.0.0-23..32 are recorded")
        void allInventoryTablesAndChangesetsExist() {
            List<String> expectedTables = List.of(
                    "warehouses", "stock_locations",
                    "inventory_balances", "inventory_warehouse_stocks", "inventory_costs",
                    "inventory_transactions", "inventory_transaction_lines",
                    "stock_receipts", "stock_receipt_lines",
                    "stock_counts", "stock_count_lines",
                    "stock_adjustments", "stock_adjustment_lines",
                    "stock_transfers", "stock_transfer_lines",
                    "inventory_reservations", "inventory_reservation_lines",
                    "idempotency_records"
            );
            for (String table : expectedTables) {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                        Integer.class, table
                );
                assertThat(exists).as("Table %s must exist", table).isEqualTo(1);
            }

            for (int i = 23; i <= 32; i++) {
                String prefix = String.format("v1.0.0-%02d-", i);
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE ?",
                        Integer.class, prefix + "%"
                );
                assertThat(count).as("Changeset %s must be applied", prefix).isEqualTo(1);
            }
        }
    }

    @Nested
    @DisplayName("2. Warehouse, Location & Tenant Composite FK Constraints")
    class WarehouseAndLocationConstraintTests {

        @Test
        @DisplayName("Rejects second default warehouse for the same branch and rejects default=true without branch")
        void enforcesSingleBranchDefaultAndBranchPresence() {
            assertThatThrownBy(() -> insertWarehouse(
                    UUID.randomUUID(), tenantAId, branchAId, "WH-A3", "Extra Default", "RETAIL_STORE", true
            )).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> insertWarehouse(
                    UUID.randomUUID(), tenantAId, null, "WH-HUB-2", "Invalid Default Hub", "CENTRAL_HUB", true
            )).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Rejects cross-tenant branch reference and non-STORAGE default location")
        void rejectsCrossTenantBranchAndInvalidDefaultLocation() {
            assertThatThrownBy(() -> insertWarehouse(
                    UUID.randomUUID(), tenantAId, branchBId, "WH-FORGED", "Cross Tenant", "RETAIL_STORE", false
            )).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> insertLocation(
                    UUID.randomUUID(), tenantAId, whA1Id, "DMG-DEF", "Bad Default", "DAMAGED", true
            )).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("3. Balances, Materialized Projection, Ledger & Operational Guards")
    class BalanceAndLedgerTests {

        @Test
        @DisplayName("Maintains generated quantity_available and rejects inconsistent bucket sums or reserved > on_hand")
        void verifiesBalanceAndSummaryConstraints() {
            UUID whStockId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO inventory_warehouse_stocks " +
                    "(id, tenant_id, warehouse_id, variant_id, quantity_on_hand, quantity_sellable, quantity_reserved, quantity_staging, quantity_damaged, quantity_quarantined, quantity_in_transit) " +
                    "VALUES (?, ?, ?, ?, 15.000, 10.000, 4.000, 2.000, 2.000, 1.000, 3.000)",
                    whStockId, tenantAId, whA1Id, variantAId
            );

            BigDecimal available = jdbcTemplate.queryForObject(
                    "SELECT quantity_available FROM inventory_warehouse_stocks WHERE id = ?",
                    BigDecimal.class, whStockId
            );
            assertThat(available).isEqualByComparingTo("6.000");

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO inventory_warehouse_stocks " +
                    "(id, tenant_id, warehouse_id, variant_id, quantity_on_hand, quantity_sellable, quantity_reserved, quantity_staging, quantity_damaged, quantity_quarantined, quantity_in_transit) " +
                    "VALUES (?, ?, ?, ?, 99.000, 10.000, 0.000, 0.000, 0.000, 0.000, 0.000)",
                    UUID.randomUUID(), tenantAId, whA2Id, variantAId
            )).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO inventory_balances (id, tenant_id, warehouse_id, location_id, variant_id, quantity_on_hand, quantity_reserved) " +
                    "VALUES (?, ?, ?, ?, ?, 5.000, 6.000)",
                    UUID.randomUUID(), tenantAId, whA1Id, locA1StorageId, variantAId
            )).isInstanceOf(DataIntegrityViolationException.class);

            // Cross-tenant variant FK must fail on inventory_balances
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO inventory_balances (id, tenant_id, warehouse_id, location_id, variant_id, quantity_on_hand, quantity_reserved) " +
                    "VALUES (?, ?, ?, ?, ?, 5.000, 0.000)",
                    UUID.randomUUID(), tenantAId, whA1Id, locA1StorageId, variantBId
            )).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Enforces ledger reversal uniqueness, zero-delta guard, and transfer/reservation line invariants")
        void enforcesLedgerTransferAndReservationConstraints() {
            UUID txnId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO inventory_transactions (id, tenant_id, transaction_number, warehouse_id, movement_type, reference_type, reference_id, occurred_at, posted_by) " +
                    "VALUES (?, ?, 'TXN-001', ?, 'PURCHASE_RECEIPT', 'STOCK_RECEIPT', ?, ?, ?)",
                    txnId, tenantAId, whA1Id, UUID.randomUUID(), Timestamp.from(Instant.now()), userId
            );

            UUID rev1Id = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO inventory_transactions (id, tenant_id, transaction_number, warehouse_id, movement_type, reference_type, reference_id, reversed_transaction_id, occurred_at, posted_by) " +
                    "VALUES (?, ?, 'TXN-002', ?, 'REVERSAL', 'REVERSAL', ?, ?, ?, ?)",
                    rev1Id, tenantAId, whA1Id, txnId, txnId, Timestamp.from(Instant.now()), userId
            );

            // Double reversal of same transaction blocked
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO inventory_transactions (id, tenant_id, transaction_number, warehouse_id, movement_type, reference_type, reference_id, reversed_transaction_id, occurred_at, posted_by) " +
                    "VALUES (?, ?, 'TXN-003', ?, 'REVERSAL', 'REVERSAL', ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantAId, whA1Id, txnId, txnId, Timestamp.from(Instant.now()), userId
            )).isInstanceOf(DataIntegrityViolationException.class);

            // Mismatched quantity_after blocked
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO inventory_transaction_lines (id, tenant_id, transaction_id, line_number, warehouse_id, location_id, variant_id, quantity_delta, quantity_before, quantity_after, unit_cost, total_cost, wac_before, wac_after) " +
                    "VALUES (?, ?, ?, 1, ?, ?, ?, 5.000, 1.000, 9.000, 10.0000, 50.0000, 0.0000, 10.0000)",
                    UUID.randomUUID(), tenantAId, txnId, whA1Id, locA1StorageId, variantAId
            )).isInstanceOf(DataIntegrityViolationException.class);

            // Self-transfer rejected
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO stock_transfers (id, tenant_id, transfer_number, source_warehouse_id, destination_warehouse_id, status) " +
                    "VALUES (?, ?, 'TRF-SELF', ?, ?, 'DRAFT')",
                    UUID.randomUUID(), tenantAId, whA1Id, whA1Id
            )).isInstanceOf(DataIntegrityViolationException.class);

            // Transfer line receipt exceeding dispatch rejected
            UUID trfId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO stock_transfers (id, tenant_id, transfer_number, source_warehouse_id, destination_warehouse_id, status) " +
                    "VALUES (?, ?, 'TRF-001', ?, ?, 'DISPATCHED')",
                    trfId, tenantAId, whA1Id, whA2Id
            );
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO stock_transfer_lines (id, tenant_id, transfer_id, line_number, variant_id, source_warehouse_id, source_location_id, destination_warehouse_id, destination_location_id, requested_quantity, dispatched_quantity, received_good_quantity, received_damaged_quantity, shortage_quantity) " +
                    "VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, 10.000, 10.000, 8.000, 2.000, 1.000)",
                    UUID.randomUUID(), tenantAId, trfId, variantAId, whA1Id, locA1StorageId, whA2Id, locA2StorageId
            )).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("4. Shared Idempotency & RBAC WAREHOUSE Scope (Changesets 30-32)")
    class IdempotencyAndRbacMigrationTests {

        @Test
        @DisplayName("Enforces tenant-scoped idempotency uniqueness and allows same key across distinct tenants")
        void verifiesIdempotencyIsolation() {
            String key = "idem-shared-123";
            String hash = "a".repeat(64);
            jdbcTemplate.update(
                    "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, endpoint, request_hash, status, expires_at) " +
                    "VALUES (?, ?, ?, 'POST /api/v1/inventory/receipts/{id}/post', ?, 'COMPLETED', ?)",
                    UUID.randomUUID(), tenantAId, key, hash, Timestamp.from(Instant.now().plusSeconds(3600))
            );
            jdbcTemplate.update(
                    "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, endpoint, request_hash, status, expires_at) " +
                    "VALUES (?, ?, ?, 'POST /api/v1/inventory/receipts/{id}/post', ?, 'COMPLETED', ?)",
                    UUID.randomUUID(), tenantBId, key, hash, Timestamp.from(Instant.now().plusSeconds(3600))
            );

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, endpoint, request_hash, status, expires_at) " +
                    "VALUES (?, ?, ?, 'POST /api/v1/inventory/receipts/{id}/post', ?, 'IN_PROGRESS', ?)",
                    UUID.randomUUID(), tenantAId, key, hash, Timestamp.from(Instant.now().plusSeconds(3600))
            )).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Supports WAREHOUSE scope assignments, rejects mixed scopes/cross-tenant warehouse, and protects referenced warehouse via RESTRICT")
        void verifiesWarehouseRbacScopeConstraints() {
            jdbcTemplate.update(
                    "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, warehouse_id, is_active) " +
                    "VALUES (?, ?, ?, ?, 'WAREHOUSE', NULL, ?, true)",
                    UUID.randomUUID(), tenantAId, membershipAId, roleAId, whA2Id
            );

            // Duplicate active WAREHOUSE assignment blocked by partial unique index
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, warehouse_id, is_active) " +
                    "VALUES (?, ?, ?, ?, 'WAREHOUSE', NULL, ?, true)",
                    UUID.randomUUID(), tenantAId, membershipAId, roleAId, whA2Id
            )).isInstanceOf(DataIntegrityViolationException.class);

            // WAREHOUSE with branch_id != NULL blocked
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, warehouse_id, is_active) " +
                    "VALUES (?, ?, ?, ?, 'WAREHOUSE', ?, ?, true)",
                    UUID.randomUUID(), tenantAId, membershipAId, roleAId, branchAId, whA1Id
            )).isInstanceOf(DataIntegrityViolationException.class);

            // Cross-tenant warehouse_id blocked by fk_assignments_warehouse
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, warehouse_id, is_active) " +
                    "VALUES (?, ?, ?, ?, 'WAREHOUSE', NULL, ?, true)",
                    UUID.randomUUID(), tenantAId, membershipAId, roleAId, whB1Id
            )).isInstanceOf(DataIntegrityViolationException.class);

            // Deleting warehouse referenced by user_role_assignments is blocked (RESTRICT)
            jdbcTemplate.update("DELETE FROM stock_locations WHERE warehouse_id = ?", whA2Id);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "DELETE FROM warehouses WHERE id = ?", whA2Id
            )).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Seeds 20 new inventory permissions (53 total) with valid module.resource.action format")
        void verifiesInventoryPermissionsSeed() {
            Integer inventoryPerms = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM permissions WHERE module = 'inventory'",
                    Integer.class
            );
            assertThat(inventoryPerms).isEqualTo(23); // 3 base + 20 new M06

            Integer totalPerms = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permissions", Integer.class);
            assertThat(totalPerms).isEqualTo(53);
        }
    }

    private void insertTenant(UUID id, String name, String code) {
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, code, currency_code, timezone, status) VALUES (?, ?, ?, 'USD', 'UTC', 'ACTIVE')",
                id, name, code
        );
    }

    private void insertBranch(UUID id, UUID tenantId, String name, String code) {
        jdbcTemplate.update(
                "INSERT INTO branches (id, tenant_id, name, code) VALUES (?, ?, ?, ?)",
                id, tenantId, name, code
        );
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, status) VALUES (?, ?, '$2a$10$hash', 'Inv User', 'ACTIVE')",
                id, email
        );
    }

    private void insertWarehouse(UUID id, UUID tenantId, UUID branchId, String code, String name, String type, boolean defaultForBranch) {
        jdbcTemplate.update(
                "INSERT INTO warehouses (id, tenant_id, branch_id, code, name, warehouse_type, status, is_default_for_branch, allow_negative_stock) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, false)",
                id, tenantId, branchId, code, name, type, defaultForBranch
        );
    }

    private void insertLocation(UUID id, UUID tenantId, UUID warehouseId, String code, String name, String type, boolean isDefault) {
        jdbcTemplate.update(
                "INSERT INTO stock_locations (id, tenant_id, warehouse_id, code, name, location_type, status, is_default) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                id, tenantId, warehouseId, code, name, type, isDefault
        );
    }

    private void insertProductAndVariant(UUID tenantId, UUID variantId, String sku) {
        UUID uomId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO units_of_measure (id, tenant_id, code, name, symbol, allow_decimal, decimal_precision, is_system, is_active) " +
                "VALUES (?, ?, 'PCS', 'Piece', 'pcs', false, 0, true, true)",
                uomId, tenantId
        );
        jdbcTemplate.update(
                "INSERT INTO products (id, tenant_id, name, product_type, status, unit_id) VALUES (?, ?, ?, 'SIMPLE', 'ACTIVE', ?)",
                productId, tenantId, "Product " + sku, uomId
        );
        jdbcTemplate.update(
                "INSERT INTO product_variants (id, tenant_id, product_id, sku, name, option_values, cost_price, selling_price, is_default, is_active) " +
                "VALUES (?, ?, ?, ?, ?, '{}'::jsonb, 10.0000, 15.0000, true, true)",
                variantId, tenantId, productId, sku, "Variant " + sku
        );
    }
}
