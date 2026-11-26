package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.AddBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductVariantDto;
import com.savdocrm.modules.catalog.application.dto.UpdateVariantCommand;
import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.exception.DuplicateBarcodeException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateVariantOptionsException;
import com.savdocrm.modules.catalog.domain.exception.InvalidDefaultVariantTransitionException;
import com.savdocrm.modules.catalog.domain.exception.InvalidProductConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantAndBarcodeServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private ProductBarcodeRepository barcodeRepository;

    private ProductVariantService variantService;
    private ProductBarcodeService barcodeService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        CatalogVariantHelper helper = new CatalogVariantHelper(variantRepository, barcodeRepository);
        variantService = new ProductVariantService(productRepository, variantRepository, barcodeRepository, helper);
        barcodeService = new ProductBarcodeService(barcodeRepository, variantRepository, productRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Rejects adding extra variant to SIMPLE product and duplicate option values on VARIABLE product")
    void addVariant_validationGuards() {
        UUID simpleId = UUID.randomUUID();
        Product simple = Product.create(tenantId, null, UUID.randomUUID(), "Simple", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE);
        simple.setId(simpleId);

        when(productRepository.findByTenantIdAndIdWithLock(tenantId, simpleId)).thenReturn(Optional.of(simple));
        assertThatThrownBy(() -> variantService.addVariant(simpleId, new CreateVariantCommand(
                "SKU-2", "Extra", Map.of(), BigDecimal.ZERO, BigDecimal.TEN, false, List.of()
        ))).isInstanceOf(InvalidVariantConfigurationException.class);

        UUID varId = UUID.randomUUID();
        Product variable = Product.create(tenantId, null, UUID.randomUUID(), "Shirt", null, null, ProductType.VARIABLE, List.of("Size"), ProductStatus.ACTIVE);
        variable.setId(varId);
        ProductVariant existing = ProductVariant.create(tenantId, variable, "SHIRT-M", "M", Map.of("Size", "M"), BigDecimal.ONE, BigDecimal.TEN, true);
        existing.setId(UUID.randomUUID());

        when(productRepository.findByTenantIdAndIdWithLock(tenantId, varId)).thenReturn(Optional.of(variable));
        when(variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, varId)).thenReturn(List.of(existing));
        when(variantRepository.existsByTenantIdAndSku(tenantId, "SHIRT-M-2")).thenReturn(false);

        assertThatThrownBy(() -> variantService.addVariant(varId, new CreateVariantCommand(
                "SHIRT-M-2", "M Dup", Map.of("Size", "M"), BigDecimal.ONE, BigDecimal.TEN, false, List.of()
        ))).isInstanceOf(DuplicateVariantOptionsException.class);
    }

    @Test
    @DisplayName("Changing default variant atomically unmarks old default and rejects inactive target")
    void changeDefaultVariant_atomicAndGuardsInactive() {
        UUID prodId = UUID.randomUUID();
        Product variable = Product.create(tenantId, null, UUID.randomUUID(), "Shirt", null, null, ProductType.VARIABLE, List.of("Size"), ProductStatus.ACTIVE);
        variable.setId(prodId);

        ProductVariant v1 = ProductVariant.create(tenantId, variable, "S-1", "S", Map.of("Size", "S"), BigDecimal.ONE, BigDecimal.TEN, true);
        v1.setId(UUID.randomUUID());
        ProductVariant v2 = ProductVariant.create(tenantId, variable, "S-2", "M", Map.of("Size", "M"), BigDecimal.ONE, BigDecimal.TEN, false);
        v2.setId(UUID.randomUUID());
        ProductVariant v3Inactive = ProductVariant.create(tenantId, variable, "S-3", "L", Map.of("Size", "L"), BigDecimal.ONE, BigDecimal.TEN, false);
        v3Inactive.setId(UUID.randomUUID());
        v3Inactive.deactivate(3);

        when(productRepository.findByTenantIdAndIdWithLock(tenantId, prodId)).thenReturn(Optional.of(variable));
        when(variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, prodId)).thenReturn(List.of(v1, v2, v3Inactive));

        assertThatThrownBy(() -> variantService.changeDefaultVariant(prodId, v3Inactive.getId()))
                .isInstanceOf(InvalidDefaultVariantTransitionException.class);

        when(variantRepository.saveAndFlush(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(barcodeRepository.findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(any(), any())).thenReturn(List.of());

        ProductVariantDto result = variantService.changeDefaultVariant(prodId, v2.getId());
        assertThat(result.isDefault()).isTrue();
        assertThat(v1.isDefault()).isFalse();
        assertThat(v2.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Barcode service swaps primary barcode safely and rejects duplicates")
    void barcodeService_primarySwapAndDuplicateGuard() {
        UUID prodId = UUID.randomUUID();
        Product activeProduct = Product.create(tenantId, null, UUID.randomUUID(), "Active", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE);
        activeProduct.setId(prodId);

        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder().productId(prodId).sku("SKU-1").name("V").build();
        variant.setId(variantId);
        variant.setTenantId(tenantId);

        when(productRepository.findByTenantIdAndIdWithLock(tenantId, prodId)).thenReturn(Optional.of(activeProduct));
        when(variantRepository.findByTenantIdAndId(tenantId, variantId)).thenReturn(Optional.of(variant));
        when(variantRepository.findByTenantIdAndIdWithLock(tenantId, variantId)).thenReturn(Optional.of(variant));
        when(barcodeRepository.existsByTenantIdAndBarcode(tenantId, "460111")).thenReturn(true);

        assertThatThrownBy(() -> barcodeService.addBarcode(variantId, new AddBarcodeCommand("460111", BarcodeType.EAN13, true)))
                .isInstanceOf(DuplicateBarcodeException.class);

        ProductBarcode oldPrimary = ProductBarcode.create(tenantId, variantId, "460111", BarcodeType.EAN13, true);
        oldPrimary.setId(UUID.randomUUID());
        ProductBarcode secondary = ProductBarcode.create(tenantId, variantId, "460222", BarcodeType.CODE128, false);
        secondary.setId(UUID.randomUUID());

        when(barcodeRepository.findByTenantIdAndId(tenantId, secondary.getId())).thenReturn(Optional.of(secondary));
        when(barcodeRepository.findByTenantIdAndIdWithLock(tenantId, secondary.getId())).thenReturn(Optional.of(secondary));
        when(barcodeRepository.findByTenantIdAndVariantIdAndIsPrimaryTrue(tenantId, variantId)).thenReturn(Optional.of(oldPrimary));
        when(barcodeRepository.saveAndFlush(any(ProductBarcode.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductBarcodeDto promoted = barcodeService.setPrimaryBarcode(secondary.getId());
        assertThat(promoted.isPrimary()).isTrue();
        assertThat(oldPrimary.isPrimary()).isFalse();

        barcodeService.removeBarcode(secondary.getId());
        verify(barcodeRepository).deleteByTenantIdAndId(tenantId, secondary.getId());
    }

    @Test
    @DisplayName("Archived product rejects variant and barcode mutations until restored")
    void archivedProduct_rejectsVariantMutations() {
        UUID prodId = UUID.randomUUID();
        Product archived = Product.create(tenantId, null, UUID.randomUUID(), "Archived", null, null, ProductType.VARIABLE, List.of("Size"), ProductStatus.ACTIVE);
        archived.setId(prodId);
        archived.transitionStatus(ProductStatus.ARCHIVED, false);

        UUID variantId = UUID.randomUUID();
        UUID barcodeId = UUID.randomUUID();
        ProductBarcode barcode = ProductBarcode.create(tenantId, variantId, "460999", BarcodeType.EAN13, true);
        barcode.setId(barcodeId);

        when(productRepository.findByTenantIdAndIdWithLock(tenantId, prodId)).thenReturn(Optional.of(archived));
        when(barcodeRepository.findByTenantIdAndId(tenantId, barcodeId)).thenReturn(Optional.of(barcode));

        assertThatThrownBy(() -> variantService.addVariant(prodId, new CreateVariantCommand(
                "ARC-1", "S", Map.of("Size", "S"), BigDecimal.ONE, BigDecimal.TEN, false, List.of()
        ))).isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> variantService.updateVariant(prodId, variantId, new UpdateVariantCommand(
                "ARC-1", "S", Map.of("Size", "S"), BigDecimal.ONE, BigDecimal.TEN
        ))).isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> variantService.changeDefaultVariant(prodId, variantId))
                .isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> variantService.deactivateVariant(prodId, variantId))
                .isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> variantService.activateVariant(prodId, variantId))
                .isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> barcodeService.addBarcode(prodId, variantId, new AddBarcodeCommand(
                "460888", BarcodeType.EAN13, false
        ))).isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> barcodeService.setPrimaryBarcode(prodId, variantId, barcodeId))
                .isInstanceOf(InvalidProductConfigurationException.class);
        assertThatThrownBy(() -> barcodeService.removeBarcode(prodId, variantId, barcodeId))
                .isInstanceOf(InvalidProductConfigurationException.class);
    }
}
