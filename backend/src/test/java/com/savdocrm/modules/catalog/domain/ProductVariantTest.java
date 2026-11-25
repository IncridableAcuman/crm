package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.InvalidDefaultVariantTransitionException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductVariantTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    private Product persistedVariableProduct() {
        Product product = Product.create(
                tenantId, null, unitId, "Sneakers", null, "Adidas",
                ProductType.VARIABLE, List.of("Size"), ProductStatus.ACTIVE
        );
        product.setId(UUID.randomUUID());
        return product;
    }

    private static final class List {
        static java.util.List<String> of(String... items) {
            return java.util.List.of(items);
        }
    }

    @Test
    @DisplayName("Variant normalizes SKU to uppercase and scales money to NUMERIC(15,4)")
    void variant_normalizesSkuAndMoneyPrecision() {
        Product product = persistedVariableProduct();

        ProductVariant variant = ProductVariant.create(
                tenantId, product, "  snk-42.blk ", "Size 42",
                Map.of("Size", "42"),
                new BigDecimal("45.12345"),
                new BigDecimal("99.9"),
                true
        );

        assertThat(variant.getSku()).isEqualTo("SNK-42.BLK");
        assertThat(variant.getCostPrice()).isEqualByComparingTo("45.1235");
        assertThat(variant.getCostPrice().scale()).isEqualTo(4);
        assertThat(variant.getSellingPrice()).isEqualByComparingTo("99.9000");
        assertThat(variant.getSellingPrice().scale()).isEqualTo(4);
        assertThat(variant.isDefault()).isTrue();
        assertThat(variant.isActive()).isTrue();
    }

    @Test
    @DisplayName("Variant rejects invalid SKUs, negative money, and overflowing precision")
    void variant_rejectsInvalidSkuAndMoney() {
        Product product = persistedVariableProduct();

        for (String badSku : java.util.List.of("", "  ", "A", "SKU WITH SPACE", "SKU$123")) {
            assertThatThrownBy(() -> ProductVariant.create(
                    tenantId, product, badSku, "V", Map.of("Size", "42"),
                    BigDecimal.ZERO, BigDecimal.TEN, true
            )).isInstanceOf(InvalidVariantConfigurationException.class);
        }

        assertThatThrownBy(() -> ProductVariant.create(
                tenantId, product, "SKU-01", "V", Map.of("Size", "42"),
                new BigDecimal("-0.01"), BigDecimal.TEN, true
        )).isInstanceOf(InvalidVariantConfigurationException.class);

        assertThatThrownBy(() -> ProductVariant.create(
                tenantId, product, "SKU-02", "V", Map.of("Size", "42"),
                BigDecimal.ZERO, new BigDecimal("100000000000.0000"), true
        )).isInstanceOf(InvalidVariantConfigurationException.class);
    }

    @Test
    @DisplayName("Inactive variant cannot become default, and default or sole active variant cannot be deactivated")
    void variant_defaultAndDeactivationInvariants() {
        Product product = persistedVariableProduct();

        ProductVariant defaultVariant = ProductVariant.create(
                tenantId, product, "SKU-DEF", "Size 42", Map.of("Size", "42"),
                BigDecimal.TEN, BigDecimal.valueOf(20), true
        );
        ProductVariant secondVariant = ProductVariant.create(
                tenantId, product, "SKU-SEC", "Size 43", Map.of("Size", "43"),
                BigDecimal.TEN, BigDecimal.valueOf(20), false
        );

        assertThatThrownBy(() -> defaultVariant.deactivate(2))
                .isInstanceOf(InvalidDefaultVariantTransitionException.class);

        assertThatThrownBy(() -> secondVariant.deactivate(1))
                .isInstanceOf(InvalidDefaultVariantTransitionException.class);

        secondVariant.deactivate(2);
        assertThat(secondVariant.isActive()).isFalse();

        assertThatThrownBy(secondVariant::markAsDefault)
                .isInstanceOf(InvalidDefaultVariantTransitionException.class);
    }
}
