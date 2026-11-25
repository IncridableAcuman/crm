package com.savdocrm.modules.catalog;

import com.savdocrm.modules.catalog.application.ProductBarcodeService;
import com.savdocrm.modules.catalog.application.ProductCategoryService;
import com.savdocrm.modules.catalog.application.ProductService;
import com.savdocrm.modules.catalog.application.ProductVariantService;
import com.savdocrm.modules.catalog.application.UnitOfMeasureService;
import com.savdocrm.modules.catalog.application.dto.AddBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.BarcodeInput;
import com.savdocrm.modules.catalog.application.dto.CategoryDto;
import com.savdocrm.modules.catalog.application.dto.CreateCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.CreateProductCommand;
import com.savdocrm.modules.catalog.application.dto.CreateUnitCommand;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.MoveCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.application.dto.ProductVariantDto;
import com.savdocrm.modules.catalog.application.dto.UnitOfMeasureDto;
import com.savdocrm.modules.catalog.application.dto.UpdateBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.UpdateProductCommand;
import com.savdocrm.modules.catalog.application.dto.UpdateVariantCommand;
import com.savdocrm.modules.catalog.application.facade.ProductQueryFacade;
import com.savdocrm.modules.catalog.application.facade.VariantCatalogDto;
import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.domain.exception.CategoryConflictException;
import com.savdocrm.modules.catalog.domain.exception.CategoryHierarchyCycleException;
import com.savdocrm.modules.catalog.domain.exception.CategoryNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateBarcodeException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateCategoryException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateSkuException;
import com.savdocrm.modules.catalog.domain.exception.InvalidDefaultVariantTransitionException;
import com.savdocrm.modules.catalog.domain.exception.InvalidProductConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.MaxCategoryDepthExceededException;
import com.savdocrm.modules.catalog.domain.exception.ProductNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.SystemUnitImmutableException;
import com.savdocrm.modules.catalog.domain.exception.UnitOfMeasureNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.modules.identity.application.AuthService;
import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.tenancy.application.TenantService;
import com.savdocrm.modules.tenancy.application.dto.CreateTenantCommand;
import com.savdocrm.modules.tenancy.application.dto.TenantDto;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.multitenancy.TenantContextMissingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class CatalogIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_catalog_app_test")
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
        registry.add("jwt.secret", () -> "testSecretKeyForJwtAuthenticationIntegrationTestingSavdoCrm1234567890!");
    }

    @Autowired
    private TenantService tenantService;
    @Autowired
    private AuthService authService;
    @Autowired
    private ProductCategoryService categoryService;
    @Autowired
    private UnitOfMeasureService unitService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductVariantService variantService;
    @Autowired
    private ProductBarcodeService barcodeService;
    @Autowired
    private ProductQueryFacade queryFacade;
    @MockitoSpyBean
    private ProductRepository productRepository;
    @MockitoSpyBean
    private ProductVariantRepository variantRepository;
    @Autowired
    private ProductBarcodeRepository barcodeRepository;
    @Autowired
    private UnitOfMeasureRepository unitRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID tenantAPcsUnitId;
    private UUID tenantBPcsUnitId;

    @BeforeEach
    void setUpTenants() {
        TenantContextHolder.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TenantDto tenantA = tenantService.createTenant(
                new CreateTenantCommand("Retail A " + suffix, "retail-a-" + suffix, "USD", "UTC")
        );
        TenantDto tenantB = tenantService.createTenant(
                new CreateTenantCommand("Retail B " + suffix, "retail-b-" + suffix, "USD", "UTC")
        );
        tenantAId = tenantA.id();
        tenantBId = tenantB.id();

        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        tenantAPcsUnitId = unitRepository.findByTenantIdAndCode(tenantAId, "PCS").orElseThrow().getId();

        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        tenantBPcsUnitId = unitRepository.findByTenantIdAndCode(tenantBId, "PCS").orElseThrow().getId();

        TenantContextHolder.setContext(TenantContext.of(tenantAId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("New tenant registration via AuthService seeds 8 independent standard UOMs idempotently")
    void newTenantRegistration_seedsIndependentStandardUnitsIdempotently() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse reg = authService.register(RegisterRequest.builder()
                .email("uom_" + unique + "@example.com")
                .username("uom_" + unique)
                .password("StrongPassword123!")
                .firstName("Unit")
                .lastName("Owner")
                .storeName("UOM Test Store " + unique)
                .build(), "127.0.0.1", "JUnit");

        UUID newTenantId = reg.getActiveTenant().getTenantId();
        TenantContextHolder.setContext(TenantContext.of(newTenantId));

        List<UnitOfMeasureDto> units = unitService.listUnits(true);
        assertThat(units).hasSize(8);
        assertThat(units).extracting(UnitOfMeasureDto::code)
                .containsExactlyInAnyOrder("PCS", "KG", "G", "L", "ML", "M", "BOX", "PACK");
        assertThat(units).allMatch(UnitOfMeasureDto::isSystem);

        // Idempotent re-invocation creates 0 duplicates
        unitService.initializeStandardUnitsForCurrentTenant();
        assertThat(unitService.listUnits(true)).hasSize(8);

        // Independent records from tenantA
        UUID newTenantPcsId = unitRepository.findByTenantIdAndCode(newTenantId, "PCS").orElseThrow().getId();
        assertThat(newTenantPcsId).isNotEqualTo(tenantAPcsUnitId);
    }

    @Test
    @DisplayName("Missing tenant context fails closed across catalog services")
    void missingTenantContext_failsClosed() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> categoryService.listCategories(false))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> unitService.listUnits(false))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> productService.listActiveProducts())
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> queryFacade.findActiveVariantBySku("ANY"))
                .isInstanceOf(TenantContextMissingException.class);
    }

    @Test
    @DisplayName("Cross-tenant isolation blocks reading or referencing another tenant's catalog entities")
    void crossTenantIsolation_enforcedEverywhere() {
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        CategoryDto catA = categoryService.createCategory(
                new CreateCategoryCommand(null, "DRINKS", "Beverages", null, 0)
        );
        UnitOfMeasureDto customUnitA = unitService.createCustomUnit(
                new CreateUnitCommand("CRATE", "Crate", "crt", false, 0)
        );
        ProductDto prodA = productService.createProduct(new CreateProductCommand(
                catA.id(),
                tenantAPcsUnitId,
                "Cola 500ml",
                null,
                "Cola",
                ProductType.SIMPLE,
                List.of(),
                ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand(
                        "SHARED-SKU-01",
                        "Default",
                        Map.of(),
                        new BigDecimal("0.50"),
                        new BigDecimal("1.00"),
                        true,
                        List.of(new BarcodeInput("4601112223334", BarcodeType.EAN13, true))
                ))
        ));

        // Switch to Tenant B: same SKU, barcode, category code, and unit code are allowed independently
        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        CategoryDto catB = categoryService.createCategory(
                new CreateCategoryCommand(null, "DRINKS", "Beverages", null, 0)
        );
        ProductDto prodB = productService.createProduct(new CreateProductCommand(
                catB.id(),
                tenantBPcsUnitId,
                "Cola B 500ml",
                null,
                "Cola",
                ProductType.SIMPLE,
                List.of(),
                ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand(
                        "SHARED-SKU-01",
                        "Default",
                        Map.of(),
                        new BigDecimal("0.55"),
                        new BigDecimal("1.20"),
                        true,
                        List.of(new BarcodeInput("4601112223334", BarcodeType.EAN13, true))
                ))
        ));
        assertThat(prodB.id()).isNotEqualTo(prodA.id());

        // Tenant B cannot see or mutate Tenant A's resources
        UUID variantAId = prodA.variants().get(0).id();
        UUID barcodeAId = prodA.variants().get(0).barcodes().get(0).id();

        assertThatThrownBy(() -> categoryService.getCategory(catA.id()))
                .isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> unitService.getUnit(customUnitA.id()))
                .isInstanceOf(UnitOfMeasureNotFoundException.class);
        assertThatThrownBy(() -> productService.getProduct(prodA.id()))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                catA.id(), tenantBPcsUnitId, "Cross", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand("CROSS-1", "Def", Map.of(), BigDecimal.ZERO, BigDecimal.ONE, true, List.of()))
        ))).isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> barcodeService.setPrimaryBarcode(barcodeAId))
                .isInstanceOf(RuntimeException.class);
        assertThat(queryFacade.findVariantById(variantAId)).isEmpty();
    }

    @Test
    @DisplayName("Category hierarchy supports 5 levels, detects multi-hop cycles, recalculates subtree depths, and enforces guards")
    void categoryHierarchy_integrityAndMoves() {
        CategoryDto root0 = categoryService.createCategory(new CreateCategoryCommand(null, "L0", "Root", null, 0));
        CategoryDto lvl1 = categoryService.createCategory(new CreateCategoryCommand(root0.id(), "L1", "Level 1", null, 0));
        CategoryDto lvl2 = categoryService.createCategory(new CreateCategoryCommand(lvl1.id(), "L2", "Level 2", null, 0));
        CategoryDto lvl3 = categoryService.createCategory(new CreateCategoryCommand(lvl2.id(), "L3", "Level 3", null, 0));
        CategoryDto lvl4 = categoryService.createCategory(new CreateCategoryCommand(lvl3.id(), "L4", "Level 4", null, 0));
        assertThat(lvl4.depth()).isEqualTo(4);

        // Level 5 (depth 5) exceeds max depth 4
        assertThatThrownBy(() -> categoryService.createCategory(
                new CreateCategoryCommand(lvl4.id(), "L5", "Too Deep", null, 0)
        )).isInstanceOf(MaxCategoryDepthExceededException.class);

        // Multi-hop cycle root0 -> lvl1 -> lvl2 -> root0 rejected
        assertThatThrownBy(() -> categoryService.moveCategory(root0.id(), new MoveCategoryCommand(lvl2.id())))
                .isInstanceOf(CategoryHierarchyCycleException.class);

        // Move subtree (lvl2 -> lvl3 -> lvl4, height 2) to root -> depths become 0, 1, 2
        CategoryDto movedLvl2 = categoryService.moveCategory(lvl2.id(), new MoveCategoryCommand(null));
        assertThat(movedLvl2.depth()).isZero();
        assertThat(categoryService.getCategory(lvl3.id()).depth()).isEqualTo(1);
        assertThat(categoryService.getCategory(lvl4.id()).depth()).isEqualTo(2);

        // Sibling name collision on move rejected
        CategoryDto otherRoot = categoryService.createCategory(new CreateCategoryCommand(null, "OTHER", "Level 1", null, 0));
        assertThatThrownBy(() -> categoryService.moveCategory(lvl1.id(), new MoveCategoryCommand(null)))
                .isInstanceOf(DuplicateCategoryException.class);

        // Deactivate parent with active child rejected
        assertThatThrownBy(() -> categoryService.deactivateCategory(movedLvl2.id()))
                .isInstanceOf(CategoryConflictException.class);
        assertThat(otherRoot.isActive()).isTrue();
    }

    @Test
    @DisplayName("Product creation rolls back atomically on variant/barcode failure leaving zero orphaned products")
    void createProduct_rollsBackAtomicallyOnVariantFailure() {
        productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Existing", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand("EXIST-01", "Def", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true, List.of()))
        ));
        long countBefore = productRepository.findAllByTenantIdAndStatus(tenantAId, ProductStatus.ACTIVE).size();

        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                null,
                tenantAPcsUnitId,
                "Atomic Rollback Shirt",
                null,
                null,
                ProductType.VARIABLE,
                List.of("Size"),
                ProductStatus.ACTIVE,
                List.of(
                        new CreateVariantCommand("NEW-S", "S", Map.of("Size", "S"), BigDecimal.ONE, BigDecimal.TEN, true, List.of()),
                        new CreateVariantCommand("EXIST-01", "M", Map.of("Size", "M"), BigDecimal.ONE, BigDecimal.TEN, false, List.of())
                )
        ))).isInstanceOf(DuplicateSkuException.class);

        long countAfter = productRepository.findAllByTenantIdAndStatus(tenantAId, ProductStatus.ACTIVE).size();
        assertThat(countAfter).isEqualTo(countBefore);
        assertThat(variantRepository.findByTenantIdAndSku(tenantAId, "NEW-S")).isEmpty();
    }

    @Test
    @DisplayName("Product, variant, barcode lifecycle, query facade, and optimistic locking work end-to-end")
    void fullCatalogLifecycleAndFacadeAndOptimisticLocking() {
        ProductDto product = productService.createProduct(new CreateProductCommand(
                null,
                tenantAPcsUnitId,
                "Running Shoes",
                "Lightweight",
                "Runner",
                ProductType.VARIABLE,
                List.of("Size", "Color"),
                ProductStatus.DRAFT,
                List.of(
                        new CreateVariantCommand(
                                "RUN-42-BLK", "42 / Black", Map.of("Size", "42", "Color", "Black"),
                                new BigDecimal("40.00"), new BigDecimal("79.99"), true,
                                List.of(new BarcodeInput("4601000000011", BarcodeType.EAN13, true))
                        ),
                        new CreateVariantCommand(
                                "RUN-43-BLK", "43 / Black", Map.of("Size", "43", "Color", "Black"),
                                new BigDecimal("40.00"), new BigDecimal("79.99"), false,
                                List.of()
                        )
                )
        ));

        UUID v1Id = product.variants().get(0).id();
        UUID v2Id = product.variants().get(1).id();

        // Activate product and query via ProductQueryFacade
        ProductDto active = productService.changeProductStatus(product.id(), ProductStatus.ACTIVE);
        assertThat(active.status()).isEqualTo(ProductStatus.ACTIVE);

        VariantCatalogDto bySku = queryFacade.findActiveVariantBySku("run-42-blk").orElseThrow();
        assertThat(bySku.sellable()).isTrue();
        assertThat(bySku.primaryBarcode()).isEqualTo("4601000000011");

        VariantCatalogDto byBarcode = queryFacade.findActiveVariantByBarcode("4601000000011").orElseThrow();
        assertThat(byBarcode.variantId()).isEqualTo(v1Id);
        assertThat(queryFacade.findVariantsByIds(Set.of(v1Id, v2Id))).hasSize(2);

        // Add barcode, set primary, update, remove
        ProductBarcodeDto b2 = barcodeService.addBarcode(v2Id, new AddBarcodeCommand("4601000000028", BarcodeType.CODE128, true));
        ProductBarcodeDto b3 = barcodeService.addBarcode(v2Id, new AddBarcodeCommand("4601000000035", BarcodeType.INTERNAL, false));
        ProductBarcodeDto promotedB3 = barcodeService.setPrimaryBarcode(b3.id());
        assertThat(promotedB3.isPrimary()).isTrue();
        assertThat(barcodeRepository.findByTenantIdAndId(tenantAId, b2.id()).orElseThrow().isPrimary()).isFalse();

        barcodeService.updateBarcode(b2.id(), new UpdateBarcodeCommand("4601000000099", BarcodeType.OTHER, false));
        assertThat(barcodeService.findByBarcode("4601000000099")).isPresent();
        barcodeService.removeBarcode(b2.id());
        assertThat(barcodeService.findByBarcode("4601000000099")).isEmpty();

        // Change default variant to v2, then deactivate v1
        variantService.changeDefaultVariant(product.id(), v2Id);
        variantService.deactivateVariant(product.id(), v1Id);

        // Cannot re-select inactive v1 as default or deactivate sole active default v2
        assertThatThrownBy(() -> variantService.changeDefaultVariant(product.id(), v1Id))
                .isInstanceOf(InvalidDefaultVariantTransitionException.class);
        assertThatThrownBy(() -> variantService.deactivateVariant(product.id(), v2Id))
                .isInstanceOf(InvalidDefaultVariantTransitionException.class);

        // Update variant SKU & price
        ProductVariantDto updatedV2 = variantService.updateVariant(product.id(), v2Id, new UpdateVariantCommand(
                "RUN-43-BLU", "43 / Blue", Map.of("Size", "43", "Color", "Blue"),
                new BigDecimal("42.00"), new BigDecimal("85.00")
        ));
        assertThat(updatedV2.sku()).isEqualTo("RUN-43-BLU");

        // Archiving product preserves historical default while clearing active selectable default
        ProductDto archivedDto = productService.archiveProduct(product.id());
        assertThat(archivedDto.status()).isEqualTo(ProductStatus.ARCHIVED);
        assertThat(archivedDto.activeDefaultVariant()).isEmpty();
        assertThat(archivedDto.historicalDefaultVariant()).isPresent();
        assertThat(archivedDto.historicalDefaultVariant().get().id()).isEqualTo(v2Id);
        assertThat(archivedDto.historicalDefaultVariant().get().isActiveDefault()).isFalse();
        assertThat(archivedDto.historicalDefaultVariant().get().isDefault()).isTrue();

        assertThat(queryFacade.findActiveVariantBySku("RUN-43-BLU")).isEmpty();
        VariantCatalogDto archivedFacadeDto = queryFacade.findVariantById(v2Id).orElseThrow();
        assertThat(archivedFacadeDto.sellable()).isFalse();
        assertThat(archivedFacadeDto.activeDefault()).isFalse();

        // Mutations on ARCHIVED product are rejected until restored
        assertThatThrownBy(() -> variantService.addVariant(product.id(), new CreateVariantCommand(
                "RUN-44-BLU", "44 / Blue", Map.of("Size", "44", "Color", "Blue"),
                BigDecimal.TEN, BigDecimal.valueOf(85), false, List.of()
        ))).isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> variantService.updateVariant(product.id(), v2Id, new UpdateVariantCommand(
                "RUN-43-BLU", "43 / Blue", Map.of("Size", "43", "Color", "Blue"),
                new BigDecimal("42.00"), new BigDecimal("85.00")
        ))).isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> variantService.changeDefaultVariant(product.id(), v1Id))
                .isInstanceOf(InvalidProductConfigurationException.class);

        // Restoring archived product reactivates the historical default variant while keeping v1 inactive
        ProductDto restored = productService.restoreProduct(product.id());
        assertThat(restored.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(restored.activeDefaultVariant()).isPresent();
        assertThat(restored.activeDefaultVariant().get().id()).isEqualTo(v2Id);
        assertThat(restored.activeDefaultVariant().get().isActiveDefault()).isTrue();
        assertThat(restored.variants().stream().filter(v -> v.id().equals(v1Id)).findFirst().orElseThrow().isActive())
                .isFalse();
        assertThat(queryFacade.findActiveVariantBySku("RUN-43-BLU")).isPresent();

        // Re-archive and restore via changeProductStatus(..., ACTIVE)
        productService.archiveProduct(product.id());
        ProductDto restoredViaStatus = productService.changeProductStatus(product.id(), ProductStatus.ACTIVE);
        assertThat(restoredViaStatus.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(restoredViaStatus.activeDefaultVariant()).isPresent();
        assertThat(restoredViaStatus.activeDefaultVariant().get().id()).isEqualTo(v2Id);

        // Optimistic locking conflict check on stale Product version
        Product copy1 = productRepository.findByTenantIdAndId(tenantAId, product.id()).orElseThrow();
        Product copy2 = productRepository.findByTenantIdAndId(tenantAId, product.id()).orElseThrow();
        copy1.updateMetadata(null, tenantAPcsUnitId, "Running Shoes V2", null, "Runner");
        productRepository.saveAndFlush(copy1);

        copy2.updateMetadata(null, tenantAPcsUnitId, "Running Shoes Stale", null, "Runner");
        assertThatThrownBy(() -> productRepository.saveAndFlush(copy2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("SIMPLE product enforces single variant invariant across create, update, deactivate, and conversion")
    void simpleProduct_variantCountInvariantEnforced() {
        // Cannot create SIMPLE product with variantOptions or 2 variants
        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Simple With Options", null, null, ProductType.SIMPLE, List.of("Size"), ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand("SIMP-OPT", "V1", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true, List.of()))
        ))).isInstanceOf(InvalidProductConfigurationException.class);

        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Two Variant Simple", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(
                        new CreateVariantCommand("SIMP-1", "V1", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true, List.of()),
                        new CreateVariantCommand("SIMP-2", "V2", Map.of(), BigDecimal.ONE, BigDecimal.TEN, false, List.of())
                )
        ))).isInstanceOf(InvalidProductConfigurationException.class);

        ProductDto simple = productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Single Simple", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand("SIMP-ONLY", "Default", Map.of(), BigDecimal.ONE, BigDecimal.TEN, false, List.of()))
        ));
        assertThat(simple.variants()).hasSize(1);
        UUID soleVariantId = simple.variants().get(0).id();
        assertThat(simple.activeDefaultVariant()).isPresent();

        // Cannot add second variant to SIMPLE product
        assertThatThrownBy(() -> variantService.addVariant(simple.id(), new CreateVariantCommand(
                "SIMP-EXTRA", "Extra", Map.of(), BigDecimal.ONE, BigDecimal.TEN, false, List.of()
        ))).isInstanceOf(InvalidVariantConfigurationException.class);

        // Cannot set option values on SIMPLE variant update
        assertThatThrownBy(() -> variantService.updateVariant(simple.id(), soleVariantId, new UpdateVariantCommand(
                "SIMP-ONLY", "Default", Map.of("Size", "L"), BigDecimal.ONE, BigDecimal.TEN
        ))).isInstanceOf(InvalidVariantConfigurationException.class);

        // Cannot deactivate sole variant of SIMPLE product
        assertThatThrownBy(() -> variantService.deactivateVariant(simple.id(), soleVariantId))
                .isInstanceOf(InvalidDefaultVariantTransitionException.class);

        // Idempotent default selection on sole variant succeeds
        assertThat(variantService.changeDefaultVariant(simple.id(), soleVariantId).isActiveDefault()).isTrue();

        // Converting SIMPLE -> VARIABLE preserves variant ID and then permits adding a second variant
        ProductDto converted = productService.convertSimpleToVariable(simple.id(), List.of("Size"), Map.of("Size", "M"));
        assertThat(converted.productType()).isEqualTo(ProductType.VARIABLE);
        assertThat(converted.variants().get(0).id()).isEqualTo(soleVariantId);

        ProductVariantDto second = variantService.addVariant(simple.id(), new CreateVariantCommand(
                "SIMP-L", "Large", Map.of("Size", "L"), BigDecimal.ONE, BigDecimal.valueOf(12), false, List.of()
        ));
        assertThat(second.productId()).isEqualTo(simple.id());
        assertThat(variantService.listVariants(simple.id())).hasSize(2);

        // Cannot re-convert an already VARIABLE product
        assertThatThrownBy(() -> productService.convertSimpleToVariable(simple.id(), List.of("Color"), Map.of("Color", "Red")))
                .isInstanceOf(InvalidProductConfigurationException.class);
    }

    @Test
    @DisplayName("Archive and restore roll back atomically on persistence failure without leaving inconsistent default state")
    void archiveAndRestore_rollBackAtomicallyOnFailure() {
        ProductDto created = productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Rollback Product", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand("RB-PROD-1", "Default", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true, List.of()))
        ));
        UUID variantId = created.variants().get(0).id();

        org.mockito.Mockito.doThrow(new IllegalStateException("Simulated archive variant flush failure"))
                .when(variantRepository)
                .saveAllAndFlush(org.mockito.ArgumentMatchers.anyList());

        assertThatThrownBy(() -> productService.archiveProduct(created.id()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("Simulated archive variant flush failure");

        org.mockito.Mockito.reset(variantRepository);
        Product afterFailedArchive = productRepository.findByTenantIdAndId(tenantAId, created.id()).orElseThrow();
        com.savdocrm.modules.catalog.domain.ProductVariant variantAfterFailedArchive =
                variantRepository.findByTenantIdAndId(tenantAId, variantId).orElseThrow();
        assertThat(afterFailedArchive.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(variantAfterFailedArchive.isActive()).isTrue();
        assertThat(variantAfterFailedArchive.isDefault()).isTrue();

        // Archive cleanly, then simulate failure when saving Product during restore
        productService.archiveProduct(created.id());

        org.mockito.Mockito.doThrow(new IllegalStateException("Simulated restore product flush failure"))
                .when(productRepository)
                .saveAndFlush(org.mockito.ArgumentMatchers.any(Product.class));

        assertThatThrownBy(() -> productService.restoreProduct(created.id()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("Simulated restore product flush failure");

        org.mockito.Mockito.reset(productRepository);
        Product afterFailedRestore = productRepository.findByTenantIdAndId(tenantAId, created.id()).orElseThrow();
        com.savdocrm.modules.catalog.domain.ProductVariant variantAfterFailedRestore =
                variantRepository.findByTenantIdAndId(tenantAId, variantId).orElseThrow();
        assertThat(afterFailedRestore.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        assertThat(variantAfterFailedRestore.isActive()).isFalse();
        assertThat(variantAfterFailedRestore.isDefault()).isTrue();
    }

    @Test
    @DisplayName("ProductBarcodeRepository enforces tenant isolation on direct repository helpers")
    void barcodeRepository_enforcesTenantIsolation() {
        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        ProductDto prodA = productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Barcode Iso", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand(
                        "BAR-ISO-1", "Default", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true,
                        List.of(new BarcodeInput("4608889990001", BarcodeType.EAN13, true))
                ))
        ));
        UUID barcodeAId = prodA.variants().get(0).barcodes().get(0).id();

        TenantContextHolder.setContext(TenantContext.of(tenantBId));
        assertThat(barcodeRepository.findByIdInCurrentTenant(barcodeAId)).isEmpty();
        assertThat(barcodeRepository.findAllInCurrentTenant(PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(barcodeRepository.existsByIdInCurrentTenant(barcodeAId)).isFalse();
        java.util.Optional<com.savdocrm.modules.catalog.domain.ProductBarcode> lockedCrossTenant =
                transactionTemplate.execute(status -> barcodeRepository.findByTenantIdAndIdWithLock(tenantBId, barcodeAId));
        assertThat(lockedCrossTenant).isEmpty();
        barcodeRepository.deleteByIdInCurrentTenant(barcodeAId);

        TenantContextHolder.setContext(TenantContext.of(tenantAId));
        assertThat(barcodeRepository.findByIdInCurrentTenant(barcodeAId)).isPresent();
        assertThat(barcodeRepository.findAllInCurrentTenant(PageRequest.of(0, 10)).getContent())
                .extracting(com.savdocrm.modules.catalog.domain.ProductBarcode::getId)
                .containsExactly(barcodeAId);
    }

    @Test
    @DisplayName("System unit modification is forbidden and DRAFT product physical deletion cleans up variants and barcodes")
    void systemUnitImmutableAndDraftDeletion() {
        assertThatThrownBy(() -> unitService.deactivateCustomUnit(tenantAPcsUnitId))
                .isInstanceOf(SystemUnitImmutableException.class);

        ProductDto draft = productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Draft Item", null, null, ProductType.SIMPLE, List.of(), ProductStatus.DRAFT,
                List.of(new CreateVariantCommand(
                        "DRAFT-01", "Default", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true,
                        List.of(new BarcodeInput("4605555555551", BarcodeType.EAN13, true))
                ))
        ));

        productService.deleteDraftProduct(draft.id());
        assertThat(productRepository.findByTenantIdAndId(tenantAId, draft.id())).isEmpty();
        assertThat(variantRepository.findByTenantIdAndSku(tenantAId, "DRAFT-01")).isEmpty();
        assertThat(barcodeRepository.findByTenantIdAndBarcode(tenantAId, "4605555555551")).isEmpty();

        // Active product cannot be physically deleted
        ProductDto active = productService.createProduct(new CreateProductCommand(
                null, tenantAPcsUnitId, "Active Item", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand("ACT-01", "Default", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true, List.of()))
        ));
        assertThatThrownBy(() -> productService.deleteDraftProduct(active.id()))
                .isInstanceOf(InvalidProductConfigurationException.class);
    }
}
