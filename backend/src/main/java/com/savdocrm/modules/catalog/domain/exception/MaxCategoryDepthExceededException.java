package com.savdocrm.modules.catalog.domain.exception;

public class MaxCategoryDepthExceededException extends RuntimeException {
    public MaxCategoryDepthExceededException(String message) {
        super(message);
    }
}
