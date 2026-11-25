package com.savdocrm.modules.catalog.web.dto.request;

import com.savdocrm.modules.catalog.domain.BarcodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBarcodeRequest(
        @NotBlank(message = "Barcode value is required.")
        @Size(max = 100, message = "Barcode cannot exceed 100 characters.")
        String barcode,

        @NotNull(message = "Barcode type is required.")
        BarcodeType barcodeType,

        boolean isPrimary
) {
}
