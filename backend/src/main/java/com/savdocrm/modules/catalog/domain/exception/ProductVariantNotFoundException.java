package com.savdocrm.modules.catalog.domain.exception;

import java.util.UUID;

public class ProductVariantNotFoundException extends RuntimeException {
    public ProductVariantNotFoundException(UUID variantId) {
        super("Product variant not found with ID: " + variantId);
    }

    public ProductVariantNotFoundException(String message) {
        super(message);
    }
}
