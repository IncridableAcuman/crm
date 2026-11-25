package com.savdocrm.modules.catalog.domain.exception;

public class CategoryHierarchyCycleException extends RuntimeException {
    public CategoryHierarchyCycleException(String message) {
        super(message);
    }
}
