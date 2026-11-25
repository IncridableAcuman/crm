package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.InvalidProductConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @Test
    @DisplayName("SIMPLE product creates cleanly with empty variantOptions and empty optionValues")
    void simpleProduct_validatesEmptyOptions() {
        Product product = Product.create(
                tenantId, null, unitId, "  Coca-Cola 1.5L ", "  Cold drink ", " Coca-Cola ",
                ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE
        );

        assertThat(product.getName()).isEqualTo("Coca-Cola 1.5L");
        assertThat(product.getBrand()).isEqualTo("Coca-Cola");
        assertThat(product.getVariantOptions()).isEmpty();
        assertThat(product.validateAndNormalizeOptionValues(null)).isEmpty();
        assertThat(product.validateAndNormalizeOptionValues(Map.of())).isEmpty();

        assertThatThrownBy(() -> product.validateAndNormalizeOptionValues(Map.of("Size", "L")))
                .isInstanceOf(InvalidVariantConfigurationException.class);
    }

    @Test
    @DisplayName("SIMPLE product rejects non-empty variantOptions")
    void simpleProduct_rejectsVariantOptions() {
        assertThatThrownBy(() -> Product.create(
                tenantId, null, unitId, "Bread", null, null,
                ProductType.SIMPLE, List.of("Size"), ProductStatus.ACTIVE
        )).isInstanceOf(InvalidProductConfigurationException.class);
    }

    @Test
    @DisplayName("VARIABLE product enforces 1..3 unique non-blank variantOptions and exact optionValues parity")
    void variableProduct_validatesOptionsAndValues() {
        Product product = Product.create(
                tenantId, null, unitId, "T-Shirt", null, "Nike",
                ProductType.VARIABLE, List.of(" Size ", "Color"), ProductStatus.DRAFT
        );

        assertThat(product.getVariantOptions()).containsExactly("Size", "Color");

        Map<String, String> normalized = product.validateAndNormalizeOptionValues(
                Map.of("Color", " Black ", "Size", " M ")
        );
        assertThat(normalized).containsExactlyInAnyOrderEntriesOf(Map.of("Size", "M", "Color", "Black"));

        assertThatThrownBy(() -> product.validateAndNormalizeOptionValues(Map.of("Size", "M")))
                .isInstanceOf(InvalidVariantConfigurationException.class);

        assertThatThrownBy(() -> product.validateAndNormalizeOptionValues(Map.of("Size", "M", "Color", "   ")))
                .isInstanceOf(InvalidVariantConfigurationException.class);
    }

    @Test
    @DisplayName("VARIABLE product rejects 0 options, >3 options, blank options, and case-insensitive duplicates")
    void variableProduct_rejectsInvalidVariantOptionLists() {
        assertThatThrownBy(() -> Product.create(
                tenantId, null, unitId, "Item", null, null,
                ProductType.VARIABLE, List.of(), ProductStatus.ACTIVE
        )).isInstanceOf(InvalidProductConfigurationException.class);

        assertThatThrownBy(() -> Product.create(
                tenantId, null, unitId, "Item", null, null,
                ProductType.VARIABLE, List.of("Size", "Color", "Material", "Fit"), ProductStatus.ACTIVE
        )).isInstanceOf(InvalidProductConfigurationException.class);

        assertThatThrownBy(() -> Product.create(
                tenantId, null, unitId, "Item", null, null,
                ProductType.VARIABLE, List.of("Size", "size"), ProductStatus.ACTIVE
        )).isInstanceOf(InvalidProductConfigurationException.class);

        assertThatThrownBy(() -> Product.create(
                tenantId, null, unitId, "Item", null, null,
                ProductType.VARIABLE, List.of("  "), ProductStatus.ACTIVE
        )).isInstanceOf(InvalidProductConfigurationException.class);
    }

    @Test
    @DisplayName("Product rejects blank name or direct ARCHIVED creation")
    void product_rejectsBlankNameOrArchivedCreation() {
        assertThatThrownBy(() -> Product.create(
                tenantId, null, unitId, "   ", null, null,
                ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE
        )).isInstanceOf(InvalidProductConfigurationException.class);

        assertThatThrownBy(() -> Product.create(
                tenantId, null, unitId, "Milk", null, null,
                ProductType.SIMPLE, List.of(), ProductStatus.ARCHIVED
        )).isInstanceOf(InvalidProductConfigurationException.class);
    }

    @Test
    @DisplayName("Product status transitions enforce active variant invariant and disallow reverting to DRAFT")
    void product_statusTransitions() {
        Product product = Product.create(
                tenantId, null, unitId, "Phone", null, null,
                ProductType.SIMPLE, List.of(), ProductStatus.DRAFT
        );

        assertThatThrownBy(() -> product.transitionStatus(ProductStatus.ACTIVE, false))
                .isInstanceOf(InvalidProductConfigurationException.class);

        product.transitionStatus(ProductStatus.ACTIVE, true);
        assertThat(product.isActive()).isTrue();

        assertThatThrownBy(() -> product.transitionStatus(ProductStatus.DRAFT, true))
                .isInstanceOf(InvalidProductConfigurationException.class);

        product.transitionStatus(ProductStatus.ARCHIVED, false);
        assertThat(product.isArchived()).isTrue();
        assertThat(product.isActive()).isFalse();

        assertThatThrownBy(() -> product.transitionStatus(ProductStatus.DRAFT, true))
                .isInstanceOf(InvalidProductConfigurationException.class);

        assertThatThrownBy(() -> product.transitionStatus(ProductStatus.ACTIVE, false))
                .isInstanceOf(InvalidProductConfigurationException.class);

        product.transitionStatus(ProductStatus.ACTIVE, true);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    @DisplayName("SIMPLE product can convert to VARIABLE once")
    void product_convertToVariable() {
        Product product = Product.create(
                tenantId, null, unitId, "Jacket", null, null,
                ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE
        );

        product.convertToVariable(List.of("Size", "Color"));
        assertThat(product.getProductType()).isEqualTo(ProductType.VARIABLE);
        assertThat(product.getVariantOptions()).containsExactly("Size", "Color");

        assertThatThrownBy(() -> product.convertToVariable(List.of("Material")))
                .isInstanceOf(InvalidProductConfigurationException.class);
    }
}
