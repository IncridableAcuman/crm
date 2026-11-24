package com.savdocrm.modules.identity.domain.exception;

import java.util.UUID;

public class LastActiveOwnerException extends RuntimeException {
    public LastActiveOwnerException(String message) {
        super(message);
    }

    public LastActiveOwnerException(UUID tenantId) {
        super("Cannot revoke or suspend final eligible OWNER: tenant " + tenantId + " must retain at least one active eligible OWNER.");
    }
}
