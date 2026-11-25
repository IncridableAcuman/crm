package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.BarcodeType;

public record BarcodeInput(
        String barcode,
        BarcodeType barcodeType,
        boolean isPrimary
) {
}
