package com.savdocrm.modules.catalog;

import com.savdocrm.modules.catalog.application.ProductBarcodeService;
import com.savdocrm.modules.catalog.application.ProductCategoryService;
import com.savdocrm.modules.catalog.application.ProductService;
import com.savdocrm.modules.catalog.application.ProductVariantService;
import com.savdocrm.modules.catalog.application.dto.AddBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.CategoryDto;
import com.savdocrm.modules.catalog.application.dto.CreateCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.CreateProductCommand;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.MoveCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.modules.catalog.domain.ProductCategory;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.exception.CategoryConflictException;
import com.savdocrm.modules.catalog.domain.exception.CategoryHierarchyCycleException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateBarcodeException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateSkuException;
import com.savdocrm.modules.catalog.domain.exception.InvalidCategoryOperationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidDefaultVariantTransitionException;
import com.savdocrm.modules.catalog.domain.exception.MaxCategoryDepthExceededException;
import com.savdocrm.modules.catalog.domain.exception.ProductBarcodeNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductCategoryRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.modules.tenancy.application.TenantService;
import com.savdocrm.modules.tenancy.application.dto.CreateTenantCommand;
import com.savdocrm.modules.tenancy.application.dto.TenantDto;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.multitenancy.TenantExecutionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class CatalogConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_catalog_concurrency_test")
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
    private TenantExecutionTemplate tenantExecutionTemplate;
    @Autowired
    private UnitOfMeasureRepository unitRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductVariantService variantService;
    @Autowired
    private ProductBarcodeService barcodeService;
    @Autowired
    private ProductCategoryService categoryService;
    @Autowired
    private ProductVariantRepository variantRepository;
    @Autowired
    private ProductBarcodeRepository barcodeRepository;
    @Autowired
    private ProductCategoryRepository categoryRepository;

    private UUID tenantId;
    private UUID pcsUnitId;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TenantDto tenant = tenantService.createTenant(
                new CreateTenantCommand("Concurrency Store " + suffix, "conc-cat-" + suffix, "USD", "UTC")
        );
        tenantId = tenant.id();
        pcsUnitId = unitRepository.findByTenantIdAndCode(tenantId, "PCS").orElseThrow().getId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Concurrency 1: Two concurrent default variant changes serialize cleanly leaving exactly 1 active default")
    void concurrentDefaultVariantChanges_preserveSingleActiveDefault() throws Exception {
        ProductDto product = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                productService.createProduct(new CreateProductCommand(
                        null, pcsUnitId, "Concurrent Shirt", null, null, ProductType.VARIABLE, List.of("Size"), ProductStatus.ACTIVE,
                        List.of(
                                new CreateVariantCommand("CONC-S", "S", Map.of("Size", "S"), BigDecimal.ONE, BigDecimal.TEN, true, List.of()),
                                new CreateVariantCommand("CONC-M", "M", Map.of("Size", "M"), BigDecimal.ONE, BigDecimal.TEN, false, List.of()),
                                new CreateVariantCommand("CONC-L", "L", Map.of("Size", "L"), BigDecimal.ONE, BigDecimal.TEN, false, List.of())
                        )
                ))
        );

        UUID v2Id = product.variants().get(1).id();
        UUID v3Id = product.variants().get(2).id();

        runTwoConcurrentTasks(
                () -> tenantExecutionTemplate.executeAsTenant(tenantId, () -> variantService.changeDefaultVariant(product.id(), v2Id)),
                () -> tenantExecutionTemplate.executeAsTenant(tenantId, () -> variantService.changeDefaultVariant(product.id(), v3Id))
        );

        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdOrderByIsDefaultDescCreatedAtAsc(tenantId, product.id());
        long defaultCount = variants.stream().filter(ProductVariant::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);
        ProductVariant winner = variants.stream().filter(ProductVariant::isDefault).findFirst().orElseThrow();
        assertThat(winner.getId()).isIn(v2Id, v3Id);
        assertThat(winner.isActive()).isTrue();
    }

    @Test
    @DisplayName("Concurrency 2: Concurrent default promotion vs deactivation cannot remove the last valid/default variant")
    void concurrentDefaultPromotionAndDeactivation_preserveValidDefaultVariant() throws Exception {
        for (int cycle = 0; cycle < 4; cycle++) {
            final int idx = cycle;
            ProductDto product = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    productService.createProduct(new CreateProductCommand(
                            null, pcsUnitId, "Deact Shirt " + idx, null, null, ProductType.VARIABLE, List.of("Size"), ProductStatus.ACTIVE,
                            List.of(
                                    new CreateVariantCommand("DEACT-" + idx + "-1", "S", Map.of("Size", "S"), BigDecimal.ONE, BigDecimal.TEN, true, List.of()),
                                    new CreateVariantCommand("DEACT-" + idx + "-2", "M", Map.of("Size", "M"), BigDecimal.ONE, BigDecimal.TEN, false, List.of())
                            )
                    ))
            );
            UUID v2Id = product.variants().get(1).id();

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger transitionRejectionCount = new AtomicInteger(0);

            runTwoConcurrentTasks(
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () -> variantService.changeDefaultVariant(product.id(), v2Id));
                            successCount.incrementAndGet();
                        } catch (InvalidDefaultVariantTransitionException ex) {
                            transitionRejectionCount.incrementAndGet();
                        }
                    },
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () -> variantService.deactivateVariant(product.id(), v2Id));
                            successCount.incrementAndGet();
                        } catch (InvalidDefaultVariantTransitionException ex) {
                            transitionRejectionCount.incrementAndGet();
                        }
                    }
            );

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(transitionRejectionCount.get()).isEqualTo(1);

            List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdOrderByIsDefaultDescCreatedAtAsc(tenantId, product.id());
            List<ProductVariant> activeDefaults = variants.stream()
                    .filter(v -> v.isDefault() && v.isActive())
                    .toList();
            assertThat(activeDefaults).hasSize(1);
        }
    }

    @Test
    @DisplayName("Concurrency 3: Concurrent category moves A -> B and B -> A cannot create a hierarchy cycle")
    void concurrentCategoryMoves_preventCycle() throws Exception {
        for (int cycle = 0; cycle < 4; cycle++) {
            final int idx = cycle;
            CategoryDto catA = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(null, "CYC_A_" + idx, "Cycle A " + idx, null, 0))
            );
            CategoryDto catB = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(null, "CYC_B_" + idx, "Cycle B " + idx, null, 0))
            );

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger cycleErrorCount = new AtomicInteger(0);

            runTwoConcurrentTasks(
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                    categoryService.moveCategory(catA.id(), new MoveCategoryCommand(catB.id()))
                            );
                            successCount.incrementAndGet();
                        } catch (CategoryHierarchyCycleException ex) {
                            cycleErrorCount.incrementAndGet();
                        }
                    },
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                    categoryService.moveCategory(catB.id(), new MoveCategoryCommand(catA.id()))
                            );
                            successCount.incrementAndGet();
                        } catch (CategoryHierarchyCycleException ex) {
                            cycleErrorCount.incrementAndGet();
                        }
                    }
            );

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(cycleErrorCount.get()).isEqualTo(1);

            ProductCategory finalA = categoryRepository.findByTenantIdAndId(tenantId, catA.id()).orElseThrow();
            ProductCategory finalB = categoryRepository.findByTenantIdAndId(tenantId, catB.id()).orElseThrow();
            boolean aIsRootAndBChild = finalA.getParentId() == null && catA.id().equals(finalB.getParentId());
            boolean bIsRootAndAChild = finalB.getParentId() == null && catB.id().equals(finalA.getParentId());
            assertThat(aIsRootAndBChild || bIsRootAndAChild).isTrue();
        }
    }

    @Test
    @DisplayName("Concurrency 4: Concurrent duplicate SKU creation yields exactly 1 success and 1 DuplicateSkuException")
    void concurrentDuplicateSkuCreation_allowsExactlyOne() throws Exception {
        String contestedSku = "RACE-SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        runTwoConcurrentTasks(
                () -> {
                    try {
                        tenantExecutionTemplate.executeAsTenant(tenantId, () -> productService.createProduct(
                                new CreateProductCommand(
                                        null, pcsUnitId, "Race Product 1", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                                        List.of(new CreateVariantCommand(contestedSku, "Def", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true, List.of()))
                                )
                        ));
                        successCount.incrementAndGet();
                    } catch (DuplicateSkuException ex) {
                        duplicateCount.incrementAndGet();
                    }
                },
                () -> {
                    try {
                        tenantExecutionTemplate.executeAsTenant(tenantId, () -> productService.createProduct(
                                new CreateProductCommand(
                                        null, pcsUnitId, "Race Product 2", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                                        List.of(new CreateVariantCommand(contestedSku, "Def", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true, List.of()))
                                )
                        ));
                        successCount.incrementAndGet();
                    } catch (DuplicateSkuException ex) {
                        duplicateCount.incrementAndGet();
                    }
                }
        );

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(1);
        assertThat(variantRepository.findByTenantIdAndSku(tenantId, contestedSku)).isPresent();
    }

    @Test
    @DisplayName("Concurrency 5: Concurrent duplicate barcode creation yields exactly 1 success and 1 DuplicateBarcodeException")
    void concurrentDuplicateBarcodeCreation_allowsExactlyOne() throws Exception {
        ProductDto prod1 = tenantExecutionTemplate.executeAsTenant(tenantId, () -> productService.createProduct(
                new CreateProductCommand(
                        null, pcsUnitId, "Barcode Host 1", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                        List.of(new CreateVariantCommand("BAR-HOST-1", "Def", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true, List.of()))
                )
        ));
        ProductDto prod2 = tenantExecutionTemplate.executeAsTenant(tenantId, () -> productService.createProduct(
                new CreateProductCommand(
                        null, pcsUnitId, "Barcode Host 2", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                        List.of(new CreateVariantCommand("BAR-HOST-2", "Def", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true, List.of()))
                )
        ));

        UUID v1Id = prod1.variants().get(0).id();
        UUID v2Id = prod2.variants().get(0).id();
        String contestedBarcode = "460" + System.nanoTime();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        runTwoConcurrentTasks(
                () -> {
                    try {
                        tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                barcodeService.addBarcode(v1Id, new AddBarcodeCommand(contestedBarcode, BarcodeType.EAN13, true))
                        );
                        successCount.incrementAndGet();
                    } catch (DuplicateBarcodeException ex) {
                        duplicateCount.incrementAndGet();
                    }
                },
                () -> {
                    try {
                        tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                barcodeService.addBarcode(v2Id, new AddBarcodeCommand(contestedBarcode, BarcodeType.EAN13, true))
                        );
                        successCount.incrementAndGet();
                    } catch (DuplicateBarcodeException ex) {
                        duplicateCount.incrementAndGet();
                    }
                }
        );

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(1);
        assertThat(barcodeRepository.findByTenantIdAndBarcode(tenantId, contestedBarcode)).isPresent();
    }

    @Test
    @DisplayName("Concurrency 6: Concurrent primary barcode changes on the same variant keep at most 1 primary barcode")
    void concurrentPrimaryBarcodeChanges_preserveSinglePrimary() throws Exception {
        ProductDto prod = tenantExecutionTemplate.executeAsTenant(tenantId, () -> productService.createProduct(
                new CreateProductCommand(
                        null, pcsUnitId, "Primary Barcode Host", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                        List.of(new CreateVariantCommand("PBAR-HOST", "Def", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true, List.of()))
                )
        ));
        UUID variantId = prod.variants().get(0).id();

        ProductBarcodeDto b1 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                barcodeService.addBarcode(variantId, new AddBarcodeCommand("PBAR-001", BarcodeType.CODE128, true))
        );
        ProductBarcodeDto b2 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                barcodeService.addBarcode(variantId, new AddBarcodeCommand("PBAR-002", BarcodeType.CODE128, false))
        );
        ProductBarcodeDto b3 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                barcodeService.addBarcode(variantId, new AddBarcodeCommand("PBAR-003", BarcodeType.CODE128, false))
        );

        runTwoConcurrentTasks(
                () -> tenantExecutionTemplate.executeAsTenant(tenantId, () -> barcodeService.setPrimaryBarcode(b2.id())),
                () -> tenantExecutionTemplate.executeAsTenant(tenantId, () -> barcodeService.setPrimaryBarcode(b3.id()))
        );

        List<ProductBarcode> all = barcodeRepository.findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(tenantId, variantId);
        assertThat(all).hasSize(3);
        long primaryCount = all.stream().filter(ProductBarcode::isPrimary).count();
        assertThat(primaryCount).isEqualTo(1);
        assertThat(all.stream().filter(ProductBarcode::isPrimary).findFirst().orElseThrow().getId())
                .isIn(b2.id(), b3.id())
                .isNotEqualTo(b1.id());
    }

    @Test
    @DisplayName("Concurrency 7: Concurrent child creation under a subtree being moved never violates max depth")
    void concurrentChildCreationAndSubtreeMove_preserveDepthBounds() throws Exception {
        for (int cycle = 0; cycle < 3; cycle++) {
            final int idx = cycle;
            CategoryDto d0 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(null, "D0_" + idx, "D0 " + idx, null, 0))
            );
            CategoryDto d1 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(d0.id(), "D1_" + idx, "D1 " + idx, null, 0))
            );
            CategoryDto d2 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(d1.id(), "D2_" + idx, "D2 " + idx, null, 0))
            );
            CategoryDto d3 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(d2.id(), "D3_" + idx, "D3 " + idx, null, 0))
            );
            CategoryDto moving = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(null, "MOV_" + idx, "Moving " + idx, null, 0))
            );

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger depthRejectedCount = new AtomicInteger(0);

            runTwoConcurrentTasks(
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                    categoryService.moveCategory(moving.id(), new MoveCategoryCommand(d3.id()))
                            );
                            successCount.incrementAndGet();
                        } catch (MaxCategoryDepthExceededException ex) {
                            depthRejectedCount.incrementAndGet();
                        }
                    },
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                    categoryService.createCategory(new CreateCategoryCommand(moving.id(), "CHD_" + idx, "Child " + idx, null, 0))
                            );
                            successCount.incrementAndGet();
                        } catch (MaxCategoryDepthExceededException ex) {
                            depthRejectedCount.incrementAndGet();
                        }
                    }
            );

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(depthRejectedCount.get()).isEqualTo(1);

            List<ProductCategory> all = categoryRepository.findAllByTenantIdOrderByDepthAscSortOrderAscNameAsc(tenantId);
            assertThat(all).allSatisfy(c -> assertThat(c.getDepth()).isBetween(0, ProductCategory.MAX_DEPTH));
        }
    }

    @Test
    @DisplayName("Concurrency 8: Concurrent parent deactivation and child creation are mutually exclusive")
    void concurrentParentDeactivationAndChildCreation_preventActiveChildUnderInactiveParent() throws Exception {
        for (int cycle = 0; cycle < 3; cycle++) {
            final int idx = cycle;
            CategoryDto parent = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                    categoryService.createCategory(new CreateCategoryCommand(null, "PAR_" + idx, "Parent " + idx, null, 0))
            );

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            runTwoConcurrentTasks(
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                    categoryService.deactivateCategory(parent.id())
                            );
                            successCount.incrementAndGet();
                        } catch (CategoryConflictException ex) {
                            rejectedCount.incrementAndGet();
                        }
                    },
                    () -> {
                        try {
                            tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                                    categoryService.createCategory(new CreateCategoryCommand(parent.id(), "KID_" + idx, "Kid " + idx, null, 0))
                            );
                            successCount.incrementAndGet();
                        } catch (InvalidCategoryOperationException ex) {
                            rejectedCount.incrementAndGet();
                        }
                    }
            );

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(rejectedCount.get()).isEqualTo(1);

            ProductCategory finalParent = categoryRepository.findByTenantIdAndId(tenantId, parent.id()).orElseThrow();
            boolean hasActiveChild = categoryRepository.existsByTenantIdAndParentIdAndIsActiveTrue(tenantId, parent.id());
            // Never allow active child under inactive parent
            if (!finalParent.isActive()) {
                assertThat(hasActiveChild).isFalse();
            }
        }
    }

    @Test
    @DisplayName("Concurrency 9: Concurrent subtree moves preserve depth == parent.depth + 1 across descendants")
    void concurrentSubtreeMoves_preserveConsistentDescendantDepths() throws Exception {
        CategoryDto branchA = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                categoryService.createCategory(new CreateCategoryCommand(null, "BR_A", "Branch A", null, 0))
        );
        CategoryDto branchB = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                categoryService.createCategory(new CreateCategoryCommand(null, "BR_B", "Branch B", null, 0))
        );
        CategoryDto branchBSub = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                categoryService.createCategory(new CreateCategoryCommand(branchB.id(), "BR_B1", "Branch B1", null, 0))
        );
        CategoryDto sub = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                categoryService.createCategory(new CreateCategoryCommand(null, "SUB_X", "Sub X", null, 0))
        );
        CategoryDto leaf = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                categoryService.createCategory(new CreateCategoryCommand(sub.id(), "LEAF_X", "Leaf X", null, 0))
        );

        runTwoConcurrentTasks(
                () -> tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                        categoryService.moveCategory(sub.id(), new MoveCategoryCommand(branchA.id()))
                ),
                () -> tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                        categoryService.moveCategory(sub.id(), new MoveCategoryCommand(branchBSub.id()))
                )
        );

        ProductCategory reloadedSub = categoryRepository.findByTenantIdAndId(tenantId, sub.id()).orElseThrow();
        ProductCategory reloadedLeaf = categoryRepository.findByTenantIdAndId(tenantId, leaf.id()).orElseThrow();
        ProductCategory reloadedParent = categoryRepository.findByTenantIdAndId(tenantId, reloadedSub.getParentId()).orElseThrow();

        assertThat(reloadedSub.getDepth()).isEqualTo(reloadedParent.getDepth() + 1);
        assertThat(reloadedLeaf.getDepth()).isEqualTo(reloadedSub.getDepth() + 1);
    }

    @Test
    @DisplayName("Concurrency 10: Concurrent primary promotion vs barcode removal serialize cleanly without stale overwrite")
    void concurrentBarcodePrimaryAndRemoval_serializeSafely() throws Exception {
        ProductDto prod = tenantExecutionTemplate.executeAsTenant(tenantId, () -> productService.createProduct(
                new CreateProductCommand(
                        null, pcsUnitId, "Barcode Race Host", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                        List.of(new CreateVariantCommand("BR-HOST", "Def", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true, List.of()))
                )
        ));
        UUID variantId = prod.variants().get(0).id();

        ProductBarcodeDto b1 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                barcodeService.addBarcode(variantId, new AddBarcodeCommand("RACE-B-1", BarcodeType.CODE128, true))
        );
        ProductBarcodeDto b2 = tenantExecutionTemplate.executeAsTenant(tenantId, () ->
                barcodeService.addBarcode(variantId, new AddBarcodeCommand("RACE-B-2", BarcodeType.CODE128, false))
        );

        AtomicInteger removedFirstCount = new AtomicInteger(0);
        runTwoConcurrentTasks(
                () -> {
                    try {
                        tenantExecutionTemplate.executeAsTenant(tenantId, () -> barcodeService.setPrimaryBarcode(b2.id()));
                    } catch (ProductBarcodeNotFoundException ex) {
                        removedFirstCount.incrementAndGet();
                    }
                },
                () -> tenantExecutionTemplate.executeAsTenant(tenantId, () -> barcodeService.removeBarcode(b2.id()))
        );

        // b2 must be deleted; b1 must never have been corrupted into an invalid state
        assertThat(barcodeRepository.findByTenantIdAndId(tenantId, b2.id())).isEmpty();
        List<ProductBarcode> remaining = barcodeRepository.findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(tenantId, variantId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(b1.id());
        if (removedFirstCount.get() == 0) {
            // Promotion ran first, clearing b1 primary, then b2 was removed -> 0 primary barcodes
            assertThat(remaining.get(0).isPrimary()).isFalse();
        } else {
            // Removal ran first, promotion failed with ProductBarcodeNotFoundException -> b1 remained primary
            assertThat(remaining.get(0).isPrimary()).isTrue();
        }
    }

    private void runTwoConcurrentTasks(Runnable task1, Runnable task2) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        Callable<Void> c1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                task1.run();
            } catch (Throwable t) {
                unexpectedError.compareAndSet(null, t);
            }
            return null;
        };
        Callable<Void> c2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                task2.run();
            } catch (Throwable t) {
                unexpectedError.compareAndSet(null, t);
            }
            return null;
        };

        Future<Void> f1 = executor.submit(c1);
        Future<Void> f2 = executor.submit(c2);
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        f1.get(15, TimeUnit.SECONDS);
        f2.get(15, TimeUnit.SECONDS);
        executor.shutdown();
        if (unexpectedError.get() != null) {
            throw new AssertionError("Concurrent task failed with unexpected exception", unexpectedError.get());
        }
    }
}
