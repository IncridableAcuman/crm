package com.savdocrm.modules.identity.application.dto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record UserPermissionsResponse(
        UUID tenantId,
        UUID membershipId,
        Set<String> tenantPermissions,
        Map<UUID, Set<String>> branchPermissions
) {}
