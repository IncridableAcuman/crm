package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.PermissionAuthorizationService;
import com.savdocrm.modules.identity.application.dto.UserPermissionsResponse;
import com.savdocrm.shared.dto.ApiResponse;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.multitenancy.TenantContextMissingException;
import com.savdocrm.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST Controller providing the current authenticated user's effective permissions and scopes
 * in the active tenant. Used by frontend for dynamic UI gating.
 */
@RestController
@RequestMapping("/api/v1/me/permissions")
@RequiredArgsConstructor
@Tag(name = "Current User Permissions", description = "Current User Effective Permissions (Tenant Scoped)")
@SecurityRequirement(name = "bearerAuth")
public class MePermissionController {

    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    @Operation(summary = "Get effective tenant and branch permissions for current user in active store")
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> getCurrentUserPermissions(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        if (principal == null || principal.getActiveMembershipId() == null) {
            throw new TenantContextMissingException("Active tenant membership is required to inspect permissions.");
        }

        UUID membershipId = principal.getActiveMembershipId();
        Set<String> tenantPermissions = permissionAuthorizationService.getEffectiveTenantPermissions(tenantId, membershipId);
        Map<UUID, Set<String>> branchPermissions = permissionAuthorizationService.getEffectiveBranchPermissions(tenantId, membershipId);

        UserPermissionsResponse response = new UserPermissionsResponse(
                tenantId,
                membershipId,
                tenantPermissions,
                branchPermissions
        );

        return ResponseEntity.ok(ApiResponse.success(response, "User permissions retrieved successfully"));
    }
}
