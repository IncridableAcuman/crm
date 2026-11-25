package com.savdocrm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class CatalogDatabaseMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_catalog_test")
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
    private UUID catAId;
    private UUID catBId;
    private UUID unitAId;
    private UUID unitBId;
    private UUID productAId;
    private UUID productBId;
    private UUID variantAId;
    private UUID variantBId;

    @BeforeEach
    void setUpBaseCatalogData() {
        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();
        catAId = UUID.randomUUID();
        catBId = UUID.randomUUID();
        unitAId = UUID.randomUUID();
        unitBId = UUID.randomUUID();
        productAId = UUID.randomUUID();
        productBId = UUID.randomUUID();
        variantAId = UUID.randomUUID();
        variantBId = UUID.randomUUID();

        // Bottom-up cleanup respecting ON DELETE RESTRICT on all 5 business FKs
        jdbcTemplate.execute("DELETE FROM product_barcodes");
        jdbcTemplate.execute("DELETE FROM product_variants");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM product_categories");
        jdbcTemplate.execute("DELETE FROM units_of_measure");
        jdbcTemplate.execute("DELETE FROM user_role_assignments");
        jdbcTemplate.execute("DELETE FROM role_permissions");
        jdbcTemplate.execute("DELETE FROM roles");
        jdbcTemplate.execute("DELETE FROM tenant_memberships");
        jdbcTemplate.execute("DELETE FROM branches");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenants");

        insertTenant(tenantAId, "Catalog Tenant A", "cat-a");
        insertTenant(tenantBId, "Catalog Tenant B", "cat-b");

        insertCategory(catAId, tenantAId, null, "ELEC-A", "Electronics", 0);
        insertCategory(catBId, tenantBId, null, "ELEC-B", "Electronics", 0);

        insertUnit(unitAId, tenantAId, "PCS", "Piece", "pcs", false, 0, true);
        insertUnit(unitBId, tenantBId, "PCS", "Piece", "pcs", false, 0, true);

        insertProduct(productAId, tenantAId, catAId, unitAId, "Phone X", "SIMPLE", "[]", "ACTIVE");
        insertProduct(productBId, tenantBId, catBId, unitBId, "Phone Y", "SIMPLE", "[]", "ACTIVE");

        insertVariant(variantAId, tenantAId, productAId, "SKU-A-001", "Phone X Default", "{}",
                new BigDecimal("100.0000"), new BigDecimal("150.0000"), true);
        insertVariant(variantBId, tenantBId, productBId, "SKU-B-001", "Phone Y Default", "{}",
                new BigDecimal("100.0000"), new BigDecimal("150.0000"), true);
    }

    private void insertTenant(UUID id, String name, String codePrefix) {
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, code, currency_code, timezone, status) VALUES (?, ?, ?, 'USD', 'UTC', 'ACTIVE')",
                id, name, codePrefix + "-" + id.toString().substring(0, 8)
        );
    }

    private void insertCategory(UUID id, UUID tenantId, UUID parentId, String code, String name, int depth) {
        jdbcTemplate.update(
                "INSERT INTO product_categories (id, tenant_id, parent_id, code, name, depth) VALUES (?, ?, ?, ?, ?, ?)",
                id, tenantId, parentId, code, name, depth
        );
    }

    private void insertUnit(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            String symbol,
            boolean allowDecimal,
            int decimalPrecision,
            boolean isSystem
    ) {
        jdbcTemplate.update(
                "INSERT INTO units_of_measure (id, tenant_id, code, name, symbol, allow_decimal, decimal_precision, is_system) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, code, name, symbol, allowDecimal, decimalPrecision, isSystem
        );
    }

    private void insertProduct(
            UUID id,
            UUID tenantId,
            UUID categoryId,
            UUID unitId,
            String name,
            String productType,
            String variantOptionsJson,
            String status
    ) {
        jdbcTemplate.update(
                "INSERT INTO products (id, tenant_id, category_id, unit_id, name, product_type, variant_options, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                id, tenantId, categoryId, unitId, name, productType, variantOptionsJson, status
        );
    }

    private void insertVariant(
            UUID id,
            UUID tenantId,
            UUID productId,
            String sku,
            String name,
            String optionValuesJson,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            boolean isDefault
    ) {
        jdbcTemplate.update(
                "INSERT INTO product_variants (id, tenant_id, product_id, sku, name, option_values, cost_price, selling_price, is_default) " +
                        "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                id, tenantId, productId, sku, name, optionValuesJson, costPrice, sellingPrice, isDefault
        );
    }

    private void insertBarcode(UUID id, UUID tenantId, UUID variantId, String barcode, String type, boolean isPrimary) {
        jdbcTemplate.update(
                "INSERT INTO product_barcodes (id, tenant_id, variant_id, barcode, barcode_type, is_primary) VALUES (?, ?, ?, ?, ?, ?)",
                id, tenantId, variantId, barcode, type, isPrimary
        );
    }

    private int countInTenant(String tableName, UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        return count == null ? 0 : count;
    }

    @Nested
    @DisplayName("1. Schema Creation, Columns, Constraints, and Indexes")
    class SchemaVerificationTests {

        @Test
        @DisplayName("Should create all 5 catalog tables and record changesets 16-22")
        void shouldCreateAllFiveCatalogTablesAndChangesets() {
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables " +
                            "WHERE table_schema = 'public' " +
                            "AND table_name IN ('product_categories', 'units_of_measure', 'products', 'product_variants', 'product_barcodes')",
                    String.class
            );
            assertThat(tables).containsExactlyInAnyOrder(
                    "product_categories",
                    "units_of_measure",
                    "products",
                    "product_variants",
                    "product_barcodes"
            );

            for (int i = 16; i <= 22; i++) {
                String prefix = String.format("v1.0.0-%02d-", i);
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE ?",
                        Integer.class,
                        prefix + "%"
                );
                assertThat(count).as("Changeset %s must exist in databasechangelog", prefix).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Should enforce expected column types including NUMERIC(15,4), JSONB, and barcode updated_at audit columns")
        void shouldHaveExpectedColumnTypes() {
            Map<String, Object> pricePrecision = jdbcTemplate.queryForMap(
                    "SELECT numeric_precision, numeric_scale FROM information_schema.columns " +
                            "WHERE table_name = 'product_variants' AND column_name = 'selling_price'"
            );
            assertThat(((Number) pricePrecision.get("numeric_precision")).intValue()).isEqualTo(15);
            assertThat(((Number) pricePrecision.get("numeric_scale")).intValue()).isEqualTo(4);

            List<String> barcodeColumns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'product_barcodes'",
                    String.class
            );
            assertThat(barcodeColumns).contains(
                    "id", "tenant_id", "variant_id", "barcode", "barcode_type", "is_primary",
                    "created_at", "updated_at", "created_by", "updated_by"
            );

            String variantOptionsType = jdbcTemplate.queryForObject(
                    "SELECT udt_name FROM information_schema.columns WHERE table_name = 'products' AND column_name = 'variant_options'",
                    String.class
            );
            assertThat(variantOptionsType).isEqualTo("jsonb");

            String decimalPrecisionType = jdbcTemplate.queryForObject(
                    "SELECT data_type FROM information_schema.columns WHERE table_name = 'units_of_measure' AND column_name = 'decimal_precision'",
                    String.class
            );
            assertThat(decimalPrecisionType).isEqualTo("smallint");
        }

        @Test
        @DisplayName("Should configure RESTRICT delete rules on all 5 tenant foreign keys and all 5 business foreign keys")
        void shouldHaveExpectedForeignKeyDeleteRules() {
            List<Map<String, Object>> fks = jdbcTemplate.queryForList(
                    "SELECT conname, confdeltype::text AS deltype " +
                            "FROM pg_constraint " +
                            "WHERE contype = 'f' " +
                            "AND conrelid IN ('product_categories'::regclass, 'units_of_measure'::regclass, " +
                            "'products'::regclass, 'product_variants'::regclass, 'product_barcodes'::regclass)"
            );

            // PostgreSQL pg_constraint.confdeltype: 'c' = CASCADE, 'r' = RESTRICT
            Map<String, String> fkRules = fks.stream().collect(
                    java.util.stream.Collectors.toMap(
                            row -> (String) row.get("conname"),
                            row -> (String) row.get("deltype")
                    )
            );

            assertThat(fkRules).containsEntry("fk_product_categories_tenant", "r");
            assertThat(fkRules).containsEntry("fk_units_of_measure_tenant", "r");
            assertThat(fkRules).containsEntry("fk_products_tenant", "r");
            assertThat(fkRules).containsEntry("fk_product_variants_tenant", "r");
            assertThat(fkRules).containsEntry("fk_product_barcodes_tenant", "r");

            assertThat(fkRules).containsEntry("fk_product_categories_parent", "r");
            assertThat(fkRules).containsEntry("fk_products_category", "r");
            assertThat(fkRules).containsEntry("fk_products_unit", "r");
            assertThat(fkRules).containsEntry("fk_product_variants_product", "r");
            assertThat(fkRules).containsEntry("fk_product_barcodes_variant", "r");
        }

        @Test
        @DisplayName("Should create all required indexes across all 5 catalog tables")
        void shouldCreateAllRequiredIndexes() {
            List<String> indexes = jdbcTemplate.queryForList(
                    "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' " +
                            "AND tablename IN ('product_categories', 'units_of_measure', 'products', 'product_variants', 'product_barcodes')",
                    String.class
            );

            assertThat(indexes).contains(
                    "uq_product_categories_tenant_id",
                    "uq_product_categories_tenant_code",
                    "uq_product_categories_root_name",
                    "uq_product_categories_child_name",
                    "idx_product_categories_tenant_parent",
                    "idx_product_categories_tenant_active",
                    "uq_units_of_measure_tenant_id",
                    "uq_units_of_measure_tenant_code",
                    "idx_units_of_measure_tenant_active",
                    "uq_products_tenant_id",
                    "idx_products_tenant_unit",
                    "idx_products_tenant_status_created",
                    "idx_products_tenant_category",
                    "idx_products_tenant_brand",
                    "idx_products_tenant_name_lower",
                    "uq_product_variants_tenant_id",
                    "uq_product_variants_tenant_sku",
                    "uq_product_variants_product_options",
                    "uq_product_variants_default_per_product",
                    "idx_product_variants_tenant_product_active",
                    "uq_product_barcodes_tenant_id",
                    "uq_product_barcodes_tenant_barcode",
                    "uq_product_barcodes_primary_per_variant",
                    "idx_product_barcodes_tenant_variant"
            );
        }
    }

    @Nested
    @DisplayName("2. Tenant Isolation via Composite Foreign Keys")
    class TenantIsolationTests {

        @Test
        @DisplayName("Should reject cross-tenant category parent assignment")
        void shouldRejectCrossTenantCategoryParent() {
            assertThatThrownBy(() ->
                    insertCategory(UUID.randomUUID(), tenantAId, catBId, "SUB-CROSS", "Sub Cross", 1)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should reject cross-tenant category on product")
        void shouldRejectCrossTenantProductCategory() {
            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catBId, unitAId, "Cross Cat Product", "SIMPLE", "[]", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should reject cross-tenant unit of measure on product")
        void shouldRejectCrossTenantProductUnit() {
            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitBId, "Cross Unit Product", "SIMPLE", "[]", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should reject cross-tenant product on variant")
        void shouldRejectCrossTenantVariantProduct() {
            assertThatThrownBy(() ->
                    insertVariant(UUID.randomUUID(), tenantAId, productBId, "SKU-CROSS-01", "Cross Variant", "{}",
                            BigDecimal.ONE, BigDecimal.TEN, false)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should reject cross-tenant variant on barcode")
        void shouldRejectCrossTenantBarcodeVariant() {
            assertThatThrownBy(() ->
                    insertBarcode(UUID.randomUUID(), tenantAId, variantBId, "4601234567890", "EAN13", true)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("3. Uniqueness & Partial Index Rules")
    class UniquenessTests {

        @Test
        @DisplayName("Should reject duplicate SKU within same tenant but allow across tenants")
        void shouldEnforceTenantScopedSkuUniqueness() {
            insertVariant(UUID.randomUUID(), tenantBId, productBId, "SKU-A-001", "Tenant B Same SKU",
                    "{\"color\":\"blue\"}", BigDecimal.ONE, BigDecimal.TEN, false);

            assertThatThrownBy(() ->
                    insertVariant(UUID.randomUUID(), tenantAId, productAId, "SKU-A-001", "Duplicate Tenant A SKU",
                            "{\"color\":\"red\"}", BigDecimal.ONE, BigDecimal.TEN, false)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should reject duplicate barcode within same tenant but allow across tenants")
        void shouldEnforceTenantScopedBarcodeUniqueness() {
            insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "4600000000017", "EAN13", true);
            insertBarcode(UUID.randomUUID(), tenantBId, variantBId, "4600000000017", "EAN13", true);

            UUID secondVariantA = UUID.randomUUID();
            insertVariant(secondVariantA, tenantAId, productAId, "SKU-A-002", "Phone X Second",
                    "{\"edition\":\"pro\"}", BigDecimal.TEN, BigDecimal.valueOf(20), false);

            assertThatThrownBy(() ->
                    insertBarcode(UUID.randomUUID(), tenantAId, secondVariantA, "4600000000017", "EAN13", true)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should reject duplicate category code and case-insensitive sibling category name within tenant")
        void shouldEnforceCategoryCodeAndSiblingNameUniqueness() {
            // Same category code in same tenant -> rejected
            assertThatThrownBy(() ->
                    insertCategory(UUID.randomUUID(), tenantAId, null, "ELEC-A", "Other Root", 0)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Same case-insensitive root name in same tenant -> rejected
            assertThatThrownBy(() ->
                    insertCategory(UUID.randomUUID(), tenantAId, null, "ELEC-A-2", "electronics", 0)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Child category name collision under same parent -> rejected, but allowed under another parent
            UUID childA1 = UUID.randomUUID();
            insertCategory(childA1, tenantAId, catAId, "PHONES", "Smartphones", 1);

            assertThatThrownBy(() ->
                    insertCategory(UUID.randomUUID(), tenantAId, catAId, "PHONES-2", "smartphones", 1)
            ).isInstanceOf(DataIntegrityViolationException.class);

            UUID rootOther = UUID.randomUUID();
            insertCategory(rootOther, tenantAId, null, "ACC", "Accessories", 0);
            insertCategory(UUID.randomUUID(), tenantAId, rootOther, "ACC-PHONES", "Smartphones", 1);
        }

        @Test
        @DisplayName("Should reject duplicate unit code within same tenant")
        void shouldEnforceTenantScopedUnitCodeUniqueness() {
            assertThatThrownBy(() ->
                    insertUnit(UUID.randomUUID(), tenantAId, "PCS", "Pieces Dup", "pc", false, 0, false)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should allow at most one default variant per product and reject key-reordered duplicate option_values")
        void shouldEnforceSingleDefaultVariantAndOptionCombinationUniqueness() {
            UUID varProduct = UUID.randomUUID();
            insertProduct(varProduct, tenantAId, catAId, unitAId, "T-Shirt", "VARIABLE", "[\"Size\",\"Color\"]", "ACTIVE");

            insertVariant(UUID.randomUUID(), tenantAId, varProduct, "TSHIRT-S-BLK", "T-Shirt S Black",
                    "{\"Size\":\"S\",\"Color\":\"Black\"}", BigDecimal.TEN, BigDecimal.valueOf(25), true);

            // Second default variant for same product -> rejected
            assertThatThrownBy(() ->
                    insertVariant(UUID.randomUUID(), tenantAId, varProduct, "TSHIRT-M-BLK", "T-Shirt M Black",
                            "{\"Size\":\"M\",\"Color\":\"Black\"}", BigDecimal.TEN, BigDecimal.valueOf(25), true)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Duplicate option_values with keys in reverse order -> rejected because JSONB equality ignores key order
            assertThatThrownBy(() ->
                    insertVariant(UUID.randomUUID(), tenantAId, varProduct, "TSHIRT-S-BLK-2", "T-Shirt S Black Dup",
                            "{\"Color\":\"Black\",\"Size\":\"S\"}", BigDecimal.TEN, BigDecimal.valueOf(25), false)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Multiple non-default distinct variants -> allowed
            insertVariant(UUID.randomUUID(), tenantAId, varProduct, "TSHIRT-M-BLK", "T-Shirt M Black",
                    "{\"Size\":\"M\",\"Color\":\"Black\"}", BigDecimal.TEN, BigDecimal.valueOf(25), false);
        }

        @Test
        @DisplayName("Should allow multiple secondary barcodes per variant but at most one primary barcode")
        void shouldEnforceAtMostOnePrimaryBarcodePerVariant() {
            insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "BAR-001", "EAN13", true);
            insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "BAR-002", "CODE128", false);
            insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "BAR-003", "INTERNAL", false);

            assertThatThrownBy(() ->
                    insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "BAR-004", "EAN13", true)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("4. CHECK Constraints & Data Integrity Validation")
    class ValidationConstraintTests {

        @Test
        @DisplayName("Should validate product_type, status, and variant_options JSONB consistency")
        void shouldValidateProductCheckConstraints() {
            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitAId, "Bad Type", "BUNDLE", "[]", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitAId, "Bad Status", "SIMPLE", "[]", "DELETED")
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitAId, "   ", "SIMPLE", "[]", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Object instead of array
            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitAId, "Obj Options", "VARIABLE", "{\"a\":1}", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);

            // SIMPLE product with non-empty variant_options -> rejected
            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitAId, "Simple With Opts", "SIMPLE", "[\"Color\"]", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);

            // VARIABLE product with empty variant_options -> rejected
            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitAId, "Variable Empty", "VARIABLE", "[]", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);

            // VARIABLE product with > 3 options -> rejected
            assertThatThrownBy(() ->
                    insertProduct(UUID.randomUUID(), tenantAId, catAId, unitAId, "Too Many Opts", "VARIABLE",
                            "[\"Size\",\"Color\",\"Material\",\"Fit\"]", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should validate variant SKU format, negative prices, and option_values JSONB type")
        void shouldValidateVariantCheckConstraints() {
            for (String badSku : List.of("", "   ", "a", "abc-123", "SKU WITH SPACE", "SKU$123")) {
                assertThatThrownBy(() ->
                        insertVariant(UUID.randomUUID(), tenantAId, productAId, badSku, "Bad SKU", "{}",
                                BigDecimal.ZERO, BigDecimal.ZERO, false)
                ).as("SKU '%s' must be rejected", badSku).isInstanceOf(DataIntegrityViolationException.class);
            }

            assertThatThrownBy(() ->
                    insertVariant(UUID.randomUUID(), tenantAId, productAId, "SKU-NEG-COST", "Neg Cost", "{}",
                            new BigDecimal("-0.0001"), BigDecimal.TEN, false)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    insertVariant(UUID.randomUUID(), tenantAId, productAId, "SKU-NEG-SELL", "Neg Sell", "{}",
                            BigDecimal.TEN, new BigDecimal("-1.0000"), false)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    insertVariant(UUID.randomUUID(), tenantAId, productAId, "SKU-BAD-JSON", "Bad JSON", "[\"not-object\"]",
                            BigDecimal.ZERO, BigDecimal.ZERO, false)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should validate barcode type and trimmed non-blank barcode check")
        void shouldValidateBarcodeCheckConstraints() {
            assertThatThrownBy(() ->
                    insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "123456", "QR_INVALID", false)
            ).isInstanceOf(DataIntegrityViolationException.class);

            for (String badBarcode : List.of("", "   ", " 4601234567890", "4601234567890 ")) {
                assertThatThrownBy(() ->
                        insertBarcode(UUID.randomUUID(), tenantAId, variantAId, badBarcode, "EAN13", false)
                ).as("Barcode '%s' must be rejected", badBarcode).isInstanceOf(DataIntegrityViolationException.class);
            }
        }

        @Test
        @DisplayName("Should validate unit decimal_precision bounds and allow_decimal consistency")
        void shouldValidateUnitCheckConstraints() {
            assertThatThrownBy(() ->
                    insertUnit(UUID.randomUUID(), tenantAId, "NEG", "Negative", "n", true, -1, false)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    insertUnit(UUID.randomUUID(), tenantAId, "FOUR", "Too Precise", "f", true, 4, false)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    insertUnit(UUID.randomUUID(), tenantAId, "INCONS", "Inconsistent", "i", false, 2, false)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should validate category depth range (0..4) and prevent direct self-parenting")
        void shouldValidateCategoryDepthAndSelfParent() {
            assertThatThrownBy(() ->
                    insertCategory(UUID.randomUUID(), tenantAId, null, "NEG-D", "Neg Depth", -1)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    insertCategory(UUID.randomUUID(), tenantAId, catAId, "DEEP-5", "Too Deep", 5)
            ).isInstanceOf(DataIntegrityViolationException.class);

            UUID selfId = UUID.randomUUID();
            assertThatThrownBy(() ->
                    insertCategory(selfId, tenantAId, selfId, "SELF", "Self Parent", 1)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Depth 4 must succeed
            insertCategory(UUID.randomUUID(), tenantAId, catAId, "DEPTH-4", "Leaf Depth 4", 4);
        }
    }

    @Nested
    @DisplayName("5. Referential Integrity (ON DELETE RESTRICT)")
    class ReferentialIntegrityTests {

        @Test
        @DisplayName("Should block physical deletion of referenced categories, units, products, and variants")
        void shouldBlockDeletionWhenReferenced() {
            UUID childCat = UUID.randomUUID();
            insertCategory(childCat, tenantAId, catAId, "CHILD-CAT", "Child Category", 1);

            insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "4609999999991", "EAN13", true);

            // Cannot delete parent category with child category
            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM product_categories WHERE id = ?", catAId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // After removing child category, still cannot delete catA because productA references it
            jdbcTemplate.update("DELETE FROM product_categories WHERE id = ?", childCat);
            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM product_categories WHERE id = ?", catAId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Cannot delete unit referenced by productA
            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM units_of_measure WHERE id = ?", unitAId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Cannot delete product referenced by variantA
            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM products WHERE id = ?", productAId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // Cannot delete variant referenced by barcode
            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM product_variants WHERE id = ?", variantAId)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("6. Tenant Deletion Guard (ON DELETE RESTRICT)")
    class TenantDeletionGuardTests {

        @Test
        @DisplayName("Should reject tenant deletion while catalog records exist and leave every record intact")
        void shouldRejectTenantDeletionAndPreserveCatalogRecords() {
            insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "4607777777775", "EAN13", true);

            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantAId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, tenantAId
            )).isEqualTo(1);
            assertThat(countInTenant("product_categories", tenantAId)).isEqualTo(1);
            assertThat(countInTenant("units_of_measure", tenantAId)).isEqualTo(1);
            assertThat(countInTenant("products", tenantAId)).isEqualTo(1);
            assertThat(countInTenant("product_variants", tenantAId)).isEqualTo(1);
            assertThat(countInTenant("product_barcodes", tenantAId)).isEqualTo(1);

            // The other tenant's catalog data must be untouched
            assertThat(countInTenant("product_categories", tenantBId)).isEqualTo(1);
            assertThat(countInTenant("units_of_measure", tenantBId)).isEqualTo(1);
            assertThat(countInTenant("products", tenantBId)).isEqualTo(1);
            assertThat(countInTenant("product_variants", tenantBId)).isEqualTo(1);
        }

        @Test
        @DisplayName("Should block tenant deletion when only a category or only a unit of measure remains")
        void shouldBlockTenantDeletionOnSingleCatalogRow() {
            UUID categoryOnlyTenantId = UUID.randomUUID();
            insertTenant(categoryOnlyTenantId, "Category Only Tenant", "cat-only");
            insertCategory(UUID.randomUUID(), categoryOnlyTenantId, null, "ONLY", "Only Category", 0);

            UUID unitOnlyTenantId = UUID.randomUUID();
            insertTenant(unitOnlyTenantId, "Unit Only Tenant", "unit-only");
            insertUnit(UUID.randomUUID(), unitOnlyTenantId, "PCS", "Piece", "pcs", false, 0, true);

            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", categoryOnlyTenantId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() ->
                    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", unitOnlyTenantId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            assertThat(countInTenant("product_categories", categoryOnlyTenantId)).isEqualTo(1);
            assertThat(countInTenant("units_of_measure", unitOnlyTenantId)).isEqualTo(1);
        }

        @Test
        @DisplayName("Should allow tenant deletion only after an explicit bottom-up catalog purge")
        void shouldAllowTenantDeletionAfterExplicitBottomUpPurge() {
            insertBarcode(UUID.randomUUID(), tenantAId, variantAId, "4607777777776", "EAN13", true);

            jdbcTemplate.update("DELETE FROM product_barcodes WHERE tenant_id = ?", tenantAId);
            jdbcTemplate.update("DELETE FROM product_variants WHERE tenant_id = ?", tenantAId);
            jdbcTemplate.update("DELETE FROM products WHERE tenant_id = ?", tenantAId);
            jdbcTemplate.update("DELETE FROM product_categories WHERE tenant_id = ?", tenantAId);
            jdbcTemplate.update("DELETE FROM units_of_measure WHERE tenant_id = ?", tenantAId);

            assertThat(jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantAId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, tenantAId
            )).isZero();
        }
    }

    @Nested
    @DisplayName("7. Standard Units of Measure Seeding (Changeset 18)")
    class StandardUnitSeedingTests {

        @Test
        @DisplayName("Should seed all 8 standard units idempotently per tenant without duplicating existing or custom units")
        void shouldSeedEightStandardUnitsIdempotentlyPerTenant() throws Exception {
            // Clear variants/products/units inserted by @BeforeEach so both tenants start with zero units
            jdbcTemplate.execute("DELETE FROM product_barcodes");
            jdbcTemplate.execute("DELETE FROM product_variants");
            jdbcTemplate.execute("DELETE FROM products");
            jdbcTemplate.execute("DELETE FROM units_of_measure");

            // Pre-insert a custom unit for Tenant A and an already-existing KG unit for Tenant B
            UUID customUnitId = UUID.randomUUID();
            insertUnit(customUnitId, tenantAId, "ROLL", "Roll", "roll", false, 0, false);

            UUID existingKgId = UUID.randomUUID();
            insertUnit(existingKgId, tenantBId, "KG", "Kilogram Custom", "kg", true, 3, true);

            String seedSql = readChangeset18Sql();

            // First execution
            jdbcTemplate.execute(seedSql);
            // Second execution (idempotency verification)
            jdbcTemplate.execute(seedSql);

            List<Map<String, Object>> tenantAUnits = jdbcTemplate.queryForList(
                    "SELECT id, tenant_id, code, is_system FROM units_of_measure WHERE tenant_id = ? ORDER BY code",
                    tenantAId
            );
            assertThat(tenantAUnits).hasSize(9); // 8 standard + 1 custom ROLL
            assertThat(tenantAUnits).extracting(row -> (String) row.get("code"))
                    .containsExactly("BOX", "G", "KG", "L", "M", "ML", "PACK", "PCS", "ROLL");

            long tenantASystemCount = tenantAUnits.stream()
                    .filter(row -> Boolean.TRUE.equals(row.get("is_system")))
                    .count();
            assertThat(tenantASystemCount).isEqualTo(8);

            List<Map<String, Object>> tenantBUnits = jdbcTemplate.queryForList(
                    "SELECT id, tenant_id, code, name, is_system FROM units_of_measure WHERE tenant_id = ? ORDER BY code",
                    tenantBId
            );
            assertThat(tenantBUnits).hasSize(8);
            assertThat(tenantBUnits).extracting(row -> (String) row.get("code"))
                    .containsExactly("BOX", "G", "KG", "L", "M", "ML", "PACK", "PCS");

            // Existing KG row for Tenant B was preserved (not duplicated or overwritten)
            Map<String, Object> tenantBKg = tenantBUnits.stream()
                    .filter(row -> "KG".equals(row.get("code")))
                    .findFirst()
                    .orElseThrow();
            assertThat(tenantBKg.get("id")).isEqualTo(existingKgId);
            assertThat(tenantBKg.get("name")).isEqualTo("Kilogram Custom");

            // Verify tenant-owned rows are completely independent between Tenant A and Tenant B
            UUID tenantAPcsId = jdbcTemplate.queryForObject(
                    "SELECT id FROM units_of_measure WHERE tenant_id = ? AND code = 'PCS'", UUID.class, tenantAId);
            UUID tenantBPcsId = jdbcTemplate.queryForObject(
                    "SELECT id FROM units_of_measure WHERE tenant_id = ? AND code = 'PCS'", UUID.class, tenantBId);
            assertThat(tenantAPcsId).isNotNull().isNotEqualTo(tenantBPcsId);
        }

        private String readChangeset18Sql() throws Exception {
            ClassPathResource resource = new ClassPathResource("db/changelog/v1.0.0/18-seed-standard-units-of-measure.xml");
            try (InputStream is = resource.getInputStream()) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
                return doc.getElementsByTagName("sql").item(0).getTextContent().trim();
            }
        }
    }
}
