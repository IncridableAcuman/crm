package com.savdocrm.modules.identity.domain.exception;

public class TenantBootstrapNotAllowedException extends RuntimeException {
    public TenantBootstrapNotAllowedException(String message) {
        super(message);
    }
}
