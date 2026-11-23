package com.savdocrm.shared.multitenancy;

public class TenantContextMissingException extends RuntimeException {

    public TenantContextMissingException(String message) {
        super(message);
    }

    public static TenantContextMissingException createDefault() {
        return new TenantContextMissingException("Security Violation: Operation invoked without a valid, trusted tenant context.");
    }
}
