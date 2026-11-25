package com.savdocrm.modules.catalog.web;

import com.savdocrm.modules.catalog.application.CatalogQueryService;
import com.savdocrm.modules.catalog.application.ProductCategoryService;
import com.savdocrm.modules.catalog.application.dto.CategoryDto;
import com.savdocrm.modules.catalog.web.dto.request.CreateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.MoveCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateActivationStatusRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.response.CategoryResponse;
import com.savdocrm.modules.catalog.web.dto.response.CategoryTreeResponse;
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
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Product Categories", description = "Hierarchical Product Category Management (Tenant Scoped)")
@SecurityRequirement(name = "bearerAuth")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;
    private final CatalogQueryService catalogQueryService;
    private final CatalogWebMapper mapper;

    @GetMapping
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.categories.read') : @authorizationService.hasBranchPermission('catalog.categories.read', #branchId)")
    @Operation(summary = "List product categories as a flat list or hierarchical tree")
    public ResponseEntity<ApiResponse<?>> listCategories(
            @RequestParam(defaultValue = "flat") String view,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) UUID branchId
    ) {
        String normalizedView = view == null ? "flat" : view.trim().toLowerCase(Locale.ROOT);
        if ("tree".equals(normalizedView)) {
            List<CategoryTreeResponse> tree = mapper.toCategoryTreeResponses(
                    catalogQueryService.getCategoryTree(includeInactive)
            );
            return ResponseEntity.ok(ApiResponse.success(tree, "Category tree retrieved successfully"));
        }
        if ("flat".equals(normalizedView)) {
            List<CategoryResponse> flat = mapper.toCategoryResponses(categoryService.listCategories(includeInactive));
            return ResponseEntity.ok(ApiResponse.success(flat, "Categories retrieved successfully"));
        }
        throw new IllegalArgumentException("Unsupported category view '" + view + "'. Use 'flat' or 'tree'.");
    }

    @GetMapping("/tree")
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.categories.read') : @authorizationService.hasBranchPermission('catalog.categories.read', #branchId)")
    @Operation(summary = "Retrieve hierarchical category tree in a single query")
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getCategoryTree(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) UUID branchId
    ) {
        List<CategoryTreeResponse> tree = mapper.toCategoryTreeResponses(
                catalogQueryService.getCategoryTree(includeInactive)
        );
        return ResponseEntity.ok(ApiResponse.success(tree, "Category tree retrieved successfully"));
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.categories.read') : @authorizationService.hasBranchPermission('catalog.categories.read', #branchId)")
    @Operation(summary = "Get category details by ID")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(
            @PathVariable UUID categoryId,
            @RequestParam(required = false) UUID branchId
    ) {
        CategoryResponse response = mapper.toResponse(categoryService.getCategory(categoryId));
        return ResponseEntity.ok(ApiResponse.success(response, "Category retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Create a new product category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryDto created = categoryService.createCategory(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mapper.toResponse(created), "Category created successfully"));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Update category name, description, and sort order")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        CategoryDto updated = categoryService.updateCategory(categoryId, mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Category updated successfully"));
    }

    @PatchMapping("/{categoryId}/move")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Move a category under a new parent or to root while enforcing cycle and depth bounds")
    public ResponseEntity<ApiResponse<CategoryResponse>> moveCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody MoveCategoryRequest request
    ) {
        CategoryDto moved = categoryService.moveCategory(categoryId, mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(moved), "Category moved successfully"));
    }

    @PatchMapping("/{categoryId}/status")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Activate or deactivate a product category")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryStatus(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateActivationStatusRequest request
    ) {
        CategoryDto updated = Boolean.TRUE.equals(request.isActive())
                ? categoryService.activateCategory(categoryId)
                : categoryService.deactivateCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Category status updated successfully"));
    }

    @PostMapping("/{categoryId}/activate")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Activate a product category")
    public ResponseEntity<ApiResponse<CategoryResponse>> activateCategory(@PathVariable UUID categoryId) {
        CategoryDto updated = categoryService.activateCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Category activated successfully"));
    }

    @PostMapping("/{categoryId}/deactivate")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Deactivate a product category")
    public ResponseEntity<ApiResponse<CategoryResponse>> deactivateCategory(@PathVariable UUID categoryId) {
        CategoryDto updated = categoryService.deactivateCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Category deactivated successfully"));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.categories.manage')")
    @Operation(summary = "Delete an unused leaf product category")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(null, "Category deleted successfully"));
    }
}
