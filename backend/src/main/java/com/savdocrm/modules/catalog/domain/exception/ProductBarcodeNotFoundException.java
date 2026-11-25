package com.savdocrm.modules.catalog.domain.exception;

import java.util.UUID;

public class ProductBarcodeNotFoundException extends RuntimeException {
    public ProductBarcodeNotFoundException(UUID barcodeId) {
        super("Product barcode not found with ID: " + barcodeId);
    }

    public ProductBarcodeNotFoundException(String message) {
        super(message);
    }
}
