package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.BarcodeInput;
import com.savdocrm.modules.catalog.application.dto.CreateProductCommand;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.application.dto.UpdateProductCommand;
import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductCategory;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.StandardUnits;
import com.savdocrm.modules.catalog.domain.UnitOfMeasure;
import com.savdocrm.modules.catalog.domain.exception.DuplicateSkuException;
import com.savdocrm.modules.catalog.domain.exception.InactiveUnitOfMeasureException;
import com.savdocrm.modules.catalog.domain.exception.InvalidCategoryOperationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidProductConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.ProductNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductCategoryRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private ProductBarcodeRepository barcodeRepository;
    @Mock
    private ProductCategoryRepository categoryRepository;
    @Mock
    private UnitOfMeasureRepository unitRepository;

    private ProductService productService;

    private UUID tenantId;
    private UUID unitId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantId));

        CatalogVariantHelper helper = new CatalogVariantHelper(variantRepository, barcodeRepository);
        productService = new ProductService(
                productRepository, variantRepository, barcodeRepository, categoryRepository, unitRepository, helper
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Creates SIMPLE product with default variant and primary barcode atomically")
    void createSimpleProduct_succeeds() {
        mockActiveUnitAndCategory();
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(variantRepository.existsByTenantIdAndSku(tenantId, "MILK-1L")).thenReturn(false);
        when(variantRepository.saveAndFlush(any(ProductVariant.class))).thenAnswer(inv -> {
            ProductVariant v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
        when(barcodeRepository.existsByTenantIdAndBarcode(tenantId, "4600000000017")).thenReturn(false);
        when(barcodeRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductDto dto = productService.createProduct(new CreateProductCommand(
                categoryId,
                unitId,
                "Fresh Milk 1L",
                "Whole milk",
                "DairyCo",
                ProductType.SIMPLE,
                List.of(),
                ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand(
                        "milk-1l",
                        "Default",
                        Map.of(),
                        new BigDecimal("0.80"),
                        new BigDecimal("1.50"),
                        true,
                        List.of(new BarcodeInput("4600000000017", BarcodeType.EAN13, true))
                ))
        ));

        assertThat(dto.name()).isEqualTo("Fresh Milk 1L");
        assertThat(dto.variants()).hasSize(1);
        assertThat(dto.variants().get(0).isDefault()).isTrue();
        assertThat(dto.variants().get(0).sku()).isEqualTo("MILK-1L");
        assertThat(dto.variants().get(0).barcodes()).hasSize(1);
    }

    @Test
    @DisplayName("Rejects product creation when unit or category is inactive, or variants list is empty")
    void createProduct_rejectsInactiveUnitCategoryOrEmptyVariants() {
        UnitOfMeasure inactiveUnit = UnitOfMeasure.createCustom(tenantId, "OLD", "Old", "old", false, 0);
        inactiveUnit.setId(unitId);
        inactiveUnit.deactivate();
        when(unitRepository.findByTenantIdAndId(tenantId, unitId)).thenReturn(Optional.of(inactiveUnit));

        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                null, unitId, "Test", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE, List.of()
        ))).isInstanceOf(InactiveUnitOfMeasureException.class);

        UnitOfMeasure activeUnit = UnitOfMeasure.createSystem(tenantId, StandardUnits.TEMPLATES.get(0));
        activeUnit.setId(unitId);
        when(unitRepository.findByTenantIdAndId(tenantId, unitId)).thenReturn(Optional.of(activeUnit));

        ProductCategory inactiveCat = ProductCategory.create(tenantId, null, 0, "OLD", "Old", null, 0);
        inactiveCat.setId(categoryId);
        inactiveCat.deactivate(false);
        when(categoryRepository.findByTenantIdAndId(tenantId, categoryId)).thenReturn(Optional.of(inactiveCat));

        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                categoryId, unitId, "Test", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE, List.of()
        ))).isInstanceOf(InvalidCategoryOperationException.class);

        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                null, unitId, "Test", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE, List.of()
        ))).isInstanceOf(InvalidProductConfigurationException.class);
    }

    @Test
    @DisplayName("Duplicate SKU during product creation throws DuplicateSkuException")
    void createProduct_duplicateSkuThrows() {
        mockActiveUnitAndCategory();
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(variantRepository.existsByTenantIdAndSku(tenantId, "DUP-SKU")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(new CreateProductCommand(
                categoryId,
                unitId,
                "Item",
                null,
                null,
                ProductType.SIMPLE,
                List.of(),
                ProductStatus.ACTIVE,
                List.of(new CreateVariantCommand(
                        "dup-sku", "Default", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true, List.of()
                ))
        ))).isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    @DisplayName("Updating product preserves historical inactive unit when unitId is unchanged")
    void updateProduct_retainsHistoricalInactiveUnit() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create(tenantId, null, unitId, "Legacy", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE);
        product.setId(productId);

        when(productRepository.findByTenantIdAndIdWithLock(tenantId, productId)).thenReturn(Optional.of(product));
        when(unitRepository.existsByTenantIdAndId(tenantId, unitId)).thenReturn(true);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variantRepository.findAllByTenantIdAndProductIdOrderByIsDefaultDescCreatedAtAsc(tenantId, productId)).thenReturn(List.of());

        ProductDto updated = productService.updateProduct(productId, new UpdateProductCommand(
                null, unitId, "Legacy Renamed", "Desc", "Brand"
        ));
        assertThat(updated.name()).isEqualTo("Legacy Renamed");
        assertThat(updated.unitId()).isEqualTo(unitId);
    }

    @Test
    @DisplayName("Archive deactivates variants and cross-tenant product lookup fails closed")
    void archiveAndCrossTenantAccess() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create(tenantId, null, unitId, "Item", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE);
        product.setId(productId);
        ProductVariant variant = ProductVariant.create(tenantId, product, "SKU-1", "Default", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, true);
        variant.setId(UUID.randomUUID());

        when(productRepository.findByTenantIdAndIdWithLock(tenantId, productId)).thenReturn(Optional.of(product));
        when(variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, productId)).thenReturn(List.of(variant));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variantRepository.findAllByTenantIdAndProductIdOrderByIsDefaultDescCreatedAtAsc(tenantId, productId)).thenReturn(List.of(variant));

        ProductDto archived = productService.archiveProduct(productId);
        assertThat(archived.status()).isEqualTo(ProductStatus.ARCHIVED);
        assertThat(variant.isActive()).isFalse();

        UUID alienProductId = UUID.randomUUID();
        when(productRepository.findByTenantIdAndId(tenantId, alienProductId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productService.getProduct(alienProductId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private void mockActiveUnitAndCategory() {
        UnitOfMeasure activeUnit = UnitOfMeasure.createSystem(tenantId, StandardUnits.TEMPLATES.get(0));
        activeUnit.setId(unitId);
        when(unitRepository.findByTenantIdAndId(tenantId, unitId)).thenReturn(Optional.of(activeUnit));

        ProductCategory activeCat = ProductCategory.create(tenantId, null, 0, "CAT", "Category", null, 0);
        activeCat.setId(categoryId);
        when(categoryRepository.findByTenantIdAndId(tenantId, categoryId)).thenReturn(Optional.of(activeCat));
    }
}
