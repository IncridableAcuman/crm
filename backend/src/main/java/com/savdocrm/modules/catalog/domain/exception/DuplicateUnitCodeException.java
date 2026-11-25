package com.savdocrm.modules.catalog.domain.exception;

public class DuplicateUnitCodeException extends RuntimeException {
    public DuplicateUnitCodeException(String message) {
        super(message);
    }
}
