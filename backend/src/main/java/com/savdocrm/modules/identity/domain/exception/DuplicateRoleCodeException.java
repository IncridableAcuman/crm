package com.savdocrm.modules.identity.domain.exception;

public class DuplicateRoleCodeException extends RuntimeException {
    public DuplicateRoleCodeException(String message) {
        super(message);
    }
}
