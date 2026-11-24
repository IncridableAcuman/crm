package com.savdocrm.modules.identity.domain.exception;

public class InactiveMembershipException extends RuntimeException {
    public InactiveMembershipException(String message) {
        super(message);
    }
}
