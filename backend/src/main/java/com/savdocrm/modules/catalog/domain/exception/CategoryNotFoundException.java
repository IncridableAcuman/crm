package com.savdocrm.modules.catalog.domain.exception;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(UUID categoryId) {
        super("Product category not found with ID: " + categoryId);
    }

    public CategoryNotFoundException(String message) {
        super(message);
    }
}
