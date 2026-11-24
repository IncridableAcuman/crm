package com.savdocrm.modules.identity.domain.exception;

public class DuplicateRoleAssignmentException extends RuntimeException {
    public DuplicateRoleAssignmentException(String message) {
        super(message);
    }
}
