package com.savdocrm.modules.identity.domain.exception;

public class CrossTenantAssignmentException extends RuntimeException {
    public CrossTenantAssignmentException(String message) {
        super(message);
    }
}
