package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.RoleService;
import com.savdocrm.modules.identity.application.dto.CreateRoleRequest;
import com.savdocrm.modules.identity.application.dto.RolePermissionResponse;
import com.savdocrm.modules.identity.application.dto.RolePermissionUpdateRequest;
import com.savdocrm.modules.identity.application.dto.RoleResponse;
import com.savdocrm.modules.identity.application.dto.UpdateRoleRequest;
import com.savdocrm.shared.dto.ApiResponse;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-scoped Role Management REST Controller.
 * Protected by fine-grained method-level RBAC permissions.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role Management & Permission Mappings (Tenant Scoped)")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.read')")
    @Operation(summary = "List all system and custom roles for the active tenant")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<RoleResponse> roles = roleService.getRoles(tenantId);
        return ResponseEntity.ok(ApiResponse.success(roles, "Roles retrieved successfully"));
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.read')")
    @Operation(summary = "Get role details and permission mappings by ID")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable UUID roleId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        RoleResponse role = roleService.getRole(tenantId, roleId);
        return ResponseEntity.ok(ApiResponse.success(role, "Role retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Create a custom role with optional initial permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;
        RoleResponse created = roleService.createRole(tenantId, request, actorUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Role created successfully"));
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Update custom role name and description (system roles are immutable)")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;
        RoleResponse updated = roleService.updateRole(tenantId, roleId, request, actorUserId);
        return ResponseEntity.ok(ApiResponse.success(updated, "Role updated successfully"));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Delete or soft-deactivate a custom role (system roles cannot be deleted)")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable UUID roleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;
        roleService.deleteOrDeactivateRole(tenantId, roleId, actorUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role deleted or deactivated successfully"));
    }

    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.read')")
    @Operation(summary = "Get permission codes assigned to a role")
    public ResponseEntity<ApiResponse<RolePermissionResponse>> getRolePermissions(@PathVariable UUID roleId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        RoleResponse role = roleService.getRole(tenantId, roleId);
        Set<String> permissions = roleService.getRolePermissions(tenantId, roleId);
        return ResponseEntity.ok(ApiResponse.success(
                new RolePermissionResponse(roleId, role.code(), permissions),
                "Role permissions retrieved successfully"
        ));
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Update permission mappings for a custom role (enforces Grant-What-You-Hold)")
    public ResponseEntity<ApiResponse<RolePermissionResponse>> updateRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody RolePermissionUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;
        RoleResponse role = roleService.getRole(tenantId, roleId);
        Set<String> updatedPermissions = roleService.updateRolePermissions(tenantId, roleId, request.permissions(), actorUserId);
        return ResponseEntity.ok(ApiResponse.success(
                new RolePermissionResponse(roleId, role.code(), updatedPermissions),
                "Role permissions updated successfully"
        ));
    }
}
