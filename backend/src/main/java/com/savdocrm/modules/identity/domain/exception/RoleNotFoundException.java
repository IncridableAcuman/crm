package com.savdocrm.modules.identity.domain.exception;

import java.util.UUID;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }

    public RoleNotFoundException(UUID roleId) {
        super("Role not found with ID: " + roleId);
    }
}
