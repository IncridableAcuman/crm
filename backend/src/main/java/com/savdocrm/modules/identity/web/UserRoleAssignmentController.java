package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.RoleAssignmentService;
import com.savdocrm.modules.identity.application.dto.AssignRoleCommand;
import com.savdocrm.modules.identity.application.dto.CreateRoleAssignmentRequest;
import com.savdocrm.modules.identity.application.dto.UserRoleAssignmentDto;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped User Role Assignment REST Controller.
 * Supports TENANT and BRANCH scopes, Last Active Owner protection, and self-elevation prevention.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Role Assignments", description = "Role Assignment & Scope Management (Tenant Scoped)")
@SecurityRequirement(name = "bearerAuth")
public class UserRoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    @GetMapping("/role-assignments")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.read')")
    @Operation(summary = "List all active role assignments in tenant, optionally filtered by membershipId")
    public ResponseEntity<ApiResponse<List<UserRoleAssignmentDto>>> getRoleAssignments(
            @RequestParam(required = false) UUID membershipId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<UserRoleAssignmentDto> assignments = membershipId != null
                ? roleAssignmentService.getAssignmentsForMembership(tenantId, membershipId)
                : roleAssignmentService.getAssignmentsForTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(assignments, "Role assignments retrieved successfully"));
    }

    @GetMapping("/members/{membershipId}/roles")
    @PreAuthorize("@authorizationService.hasPermission('identity.users.read') or @authorizationService.hasPermission('identity.roles.read')")
    @Operation(summary = "List all role assignments for a specific member")
    public ResponseEntity<ApiResponse<List<UserRoleAssignmentDto>>> getMemberRoles(
            @PathVariable UUID membershipId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<UserRoleAssignmentDto> assignments = roleAssignmentService.getAssignmentsForMembership(tenantId, membershipId);
        return ResponseEntity.ok(ApiResponse.success(assignments, "Member role assignments retrieved successfully"));
    }

    @PostMapping("/role-assignments")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Assign a role to a tenant membership with TENANT or BRANCH scope")
    public ResponseEntity<ApiResponse<UserRoleAssignmentDto>> assignRole(
            @Valid @RequestBody CreateRoleAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;

        AssignRoleCommand command = new AssignRoleCommand(
                tenantId,
                request.membershipId(),
                request.roleId(),
                request.scopeType(),
                request.branchId()
        );

        UserRoleAssignmentDto assignment = roleAssignmentService.assignRole(command, actorUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(assignment, "Role assigned successfully"));
    }

    @PostMapping("/members/{membershipId}/roles")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Assign a role directly to a specific member with TENANT or BRANCH scope")
    public ResponseEntity<ApiResponse<UserRoleAssignmentDto>> assignRoleToMember(
            @PathVariable UUID membershipId,
            @Valid @RequestBody CreateRoleAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;

        AssignRoleCommand command = new AssignRoleCommand(
                tenantId,
                membershipId,
                request.roleId(),
                request.scopeType(),
                request.branchId()
        );

        UserRoleAssignmentDto assignment = roleAssignmentService.assignRole(command, actorUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(assignment, "Role assigned successfully"));
    }

    @DeleteMapping("/role-assignments/{assignmentId}")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Revoke a role assignment (enforces concurrency-safe Last Active Owner check)")
    public ResponseEntity<ApiResponse<Void>> revokeRoleAssignment(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;

        roleAssignmentService.revokeRoleAssignment(tenantId, assignmentId, actorUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role assignment revoked successfully"));
    }

    @DeleteMapping("/members/{membershipId}/roles/{assignmentId}")
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.manage')")
    @Operation(summary = "Revoke a role assignment for a specific member")
    public ResponseEntity<ApiResponse<Void>> revokeMemberRoleAssignment(
            @PathVariable UUID membershipId,
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UUID actorUserId = principal != null ? principal.getId() : null;

        roleAssignmentService.revokeRoleAssignment(tenantId, assignmentId, actorUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role assignment revoked successfully"));
    }
}
