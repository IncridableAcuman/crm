package com.savdocrm.modules.tenancy.web;

import com.savdocrm.modules.tenancy.application.BranchService;
import com.savdocrm.modules.tenancy.application.dto.BranchDto;
import com.savdocrm.modules.tenancy.application.dto.CreateBranchCommand;
import com.savdocrm.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-protected business API controller for store branch management.
 * Requires a tenant-scoped JWT token. Global login tokens fail closed with 403 Forbidden.
 */
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@Tag(name = "Branches", description = "Store Branch Management (Tenant Scoped)")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @Operation(summary = "Get all active branches for current active store")
    public ResponseEntity<ApiResponse<List<BranchDto>>> getActiveBranches() {
        List<BranchDto> branches = branchService.getActiveBranches();
        return ResponseEntity.ok(ApiResponse.success(branches, "Branches retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get branch by id for current active store")
    public ResponseEntity<ApiResponse<BranchDto>> getBranchById(@PathVariable UUID id) {
        return branchService.getBranchById(id)
                .map(branch -> ResponseEntity.ok(ApiResponse.success(branch, "Branch retrieved")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Branch not found", HttpStatus.NOT_FOUND.value())));
    }

    @PostMapping
    @Operation(summary = "Create new branch in current active store")
    public ResponseEntity<ApiResponse<BranchDto>> createBranch(@Valid @RequestBody CreateBranchCommand command) {
        BranchDto branch = branchService.createBranch(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(branch, "Branch created successfully"));
    }
}
