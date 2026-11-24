package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.PermissionService;
import com.savdocrm.modules.identity.application.dto.PermissionDto;
import com.savdocrm.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller exposing the canonical permissions master catalog.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "System Permission Catalog (Read Only)")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission('identity.roles.read')")
    @Operation(summary = "List all canonical system permissions in the master catalog")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        List<PermissionDto> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions, "Permissions retrieved successfully"));
    }
}
