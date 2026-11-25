package com.savdocrm.modules.catalog.domain.exception;

public class DuplicateBarcodeException extends RuntimeException {
    public DuplicateBarcodeException(String message) {
        super(message);
    }
}
