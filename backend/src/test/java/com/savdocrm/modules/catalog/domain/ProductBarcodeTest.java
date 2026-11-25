package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.InvalidBarcodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductBarcodeTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();

    @Test
    @DisplayName("Barcode trims whitespace, preserves case, and manages primary flag")
    void barcode_normalizesAndSupportsPrimarySelection() {
        ProductBarcode barcode = ProductBarcode.create(
                tenantId, variantId, "  Code128-AbC  ", BarcodeType.CODE128, false
        );

        assertThat(barcode.getBarcode()).isEqualTo("Code128-AbC");
        assertThat(barcode.getBarcodeType()).isEqualTo(BarcodeType.CODE128);
        assertThat(barcode.isPrimary()).isFalse();

        barcode.markAsPrimary();
        assertThat(barcode.isPrimary()).isTrue();

        barcode.unmarkAsPrimary();
        assertThat(barcode.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("Barcode rejects blank values, oversized values, and null barcodeType")
    void barcode_rejectsInvalidInputs() {
        assertThatThrownBy(() -> ProductBarcode.create(tenantId, variantId, "   ", BarcodeType.EAN13, true))
                .isInstanceOf(InvalidBarcodeException.class);

        assertThatThrownBy(() -> ProductBarcode.create(tenantId, variantId, "x".repeat(101), BarcodeType.OTHER, false))
                .isInstanceOf(InvalidBarcodeException.class);

        assertThatThrownBy(() -> ProductBarcode.create(tenantId, variantId, "4601234567890", null, false))
                .isInstanceOf(InvalidBarcodeException.class);
    }
}
