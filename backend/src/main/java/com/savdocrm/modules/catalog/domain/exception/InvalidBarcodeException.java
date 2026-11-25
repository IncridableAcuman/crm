package com.savdocrm.modules.catalog.domain.exception;

public class InvalidBarcodeException extends RuntimeException {
    public InvalidBarcodeException(String message) {
        super(message);
    }
}
