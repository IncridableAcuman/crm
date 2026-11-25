package com.savdocrm.modules.catalog.web;

import com.savdocrm.modules.catalog.application.UnitOfMeasureService;
import com.savdocrm.modules.catalog.application.dto.UnitOfMeasureDto;
import com.savdocrm.modules.catalog.web.dto.request.CreateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateActivationStatusRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.response.UnitOfMeasureResponse;
import com.savdocrm.modules.catalog.web.mapper.CatalogWebMapper;
import com.savdocrm.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for tenant-scoped Units of Measure.
 * Per ADR-0010 and 05-product-api-contracts.md, read endpoints use catalog.products.read
 * (supporting branch-scoped readers) and write endpoints reuse catalog.categories.manage (TENANT scope)
 * without introducing a 34th permission code.
 */
@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
@Tag(name = "Units of Measure", description = "System & Custom Units of Measure Management (Tenant Scoped)")
@SecurityRequirement(name = "bearerAuth")
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitService;
    private final CatalogWebMapper mapper;

    @GetMapping
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "List units of measure for the active tenant")
    public ResponseEntity<ApiResponse<List<UnitOfMeasureResponse>>> listUnits(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) UUID branchId
    ) {
        List<UnitOfMeasureResponse> units = mapper.toUnitResponses(unitService.listUnits(includeInactive));
        return ResponseEntity.ok(ApiResponse.success(units, "Units of measure retrieved successfully"));
    }

    @GetMapping("/{unitId}")
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "Get unit of measure by ID")
    public ResponseEntity<ApiResponse<UnitOfMeasureResponse>> getUnit(
            @PathVariable UUID unitId,
            @RequestParam(required = false) UUID branchId
    ) {
        UnitOfMeasureResponse unit = mapper.toResponse(unitService.getUnit(unitId));
        return ResponseEntity.ok(ApiResponse.success(unit, "Unit of measure retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Create a custom unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasureResponse>> createUnit(
            @Valid @RequestBody CreateUnitRequest request
    ) {
        UnitOfMeasureDto created = unitService.createCustomUnit(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mapper.toResponse(created), "Unit of measure created successfully"));
    }

    @PutMapping("/{unitId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Update a custom unit of measure (system units are immutable)")
    public ResponseEntity<ApiResponse<UnitOfMeasureResponse>> updateUnit(
            @PathVariable UUID unitId,
            @Valid @RequestBody UpdateUnitRequest request
    ) {
        UnitOfMeasureDto updated = unitService.updateCustomUnit(unitId, mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Unit of measure updated successfully"));
    }

    @PatchMapping("/{unitId}/status")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Activate or deactivate a custom unit of measure (system units are immutable)")
    public ResponseEntity<ApiResponse<UnitOfMeasureResponse>> updateUnitStatus(
            @PathVariable UUID unitId,
            @Valid @RequestBody UpdateActivationStatusRequest request
    ) {
        UnitOfMeasureDto updated = Boolean.TRUE.equals(request.isActive())
                ? unitService.activateCustomUnit(unitId)
                : unitService.deactivateCustomUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Unit of measure status updated successfully"));
    }

    @DeleteMapping("/{unitId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Delete an unused custom unit of measure (system units cannot be deleted)")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable UUID unitId) {
        unitService.deleteCustomUnit(unitId);
        return ResponseEntity.ok(ApiResponse.success(null, "Unit of measure deleted successfully"));
    }
}
