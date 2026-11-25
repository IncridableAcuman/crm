package com.savdocrm.modules.catalog.domain.exception;

public class CrossTenantCatalogException extends RuntimeException {
    public CrossTenantCatalogException(String message) {
        super(message);
    }
}
