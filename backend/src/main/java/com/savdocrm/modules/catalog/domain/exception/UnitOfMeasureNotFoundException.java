package com.savdocrm.modules.catalog.domain.exception;

import java.util.UUID;

public class UnitOfMeasureNotFoundException extends RuntimeException {
    public UnitOfMeasureNotFoundException(UUID unitId) {
        super("Unit of measure not found with ID: " + unitId);
    }

    public UnitOfMeasureNotFoundException(String message) {
        super(message);
    }
}
