package com.savdocrm.modules.catalog.web;

import com.savdocrm.modules.catalog.application.CatalogQueryService;
import com.savdocrm.modules.catalog.application.ProductService;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.application.dto.ProductSearchCriteria;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.web.dto.request.ConvertProductToVariableRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductStatusRequest;
import com.savdocrm.modules.catalog.web.dto.response.ProductResponse;
import com.savdocrm.modules.catalog.web.dto.response.ProductSummaryResponse;
import com.savdocrm.modules.catalog.web.dto.response.VariantLookupResponse;
import com.savdocrm.modules.catalog.web.mapper.CatalogWebMapper;
import com.savdocrm.shared.dto.ApiResponse;
import com.savdocrm.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product Catalog Management (Tenant Scoped)")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;
    private final CatalogQueryService catalogQueryService;
    private final CatalogWebMapper mapper;

    @GetMapping
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "Search and paginate products for the active tenant")
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "false") boolean includeSubcategories,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                search,
                sku,
                barcode,
                categoryId,
                includeSubcategories,
                unitId,
                brand,
                status,
                productType,
                isActive,
                includeArchived
        );
        Page<ProductSummaryResponse> mapped = catalogQueryService
                .searchProducts(criteria, page, size, sort)
                .map(mapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(mapped), "Products retrieved successfully"));
    }

    @GetMapping("/lookup")
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "Lookup an active product variant by exact barcode or SKU")
    public ResponseEntity<ApiResponse<VariantLookupResponse>> lookupVariant(
            @RequestParam String code,
            @RequestParam(required = false) UUID branchId
    ) {
        VariantLookupResponse response = mapper.toResponse(catalogQueryService.lookupActiveVariant(code));
        return ResponseEntity.ok(ApiResponse.success(response, "Variant lookup succeeded"));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "Get product details with variants and barcodes by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID branchId
    ) {
        ProductResponse response = mapper.toResponse(catalogQueryService.getProductDetail(productId));
        return ResponseEntity.ok(ApiResponse.success(response, "Product retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.create')")
    @Operation(summary = "Create a new SIMPLE or VARIABLE product with initial variant(s)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductDto created = productService.createProduct(mapper.toCommand(request));
        ProductResponse response = mapper.toResponse(catalogQueryService.getProductDetail(created.id()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Product created successfully"));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Update product metadata")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        productService.updateProduct(productId, mapper.toCommand(request));
        ProductResponse response = mapper.toResponse(catalogQueryService.getProductDetail(productId));
        return ResponseEntity.ok(ApiResponse.success(response, "Product updated successfully"));
    }

    @PatchMapping("/{productId}/status")
    @PreAuthorize("(#request != null && #request.status == T(com.savdocrm.modules.catalog.domain.ProductStatus).ARCHIVED) ? @authorizationService.hasPermission('catalog.products.delete') : @authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Transition product status (DRAFT, ACTIVE, ARCHIVED)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProductStatus(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductStatusRequest request
    ) {
        productService.changeProductStatus(productId, request.status());
        ProductResponse response = mapper.toResponse(catalogQueryService.getProductDetail(productId));
        return ResponseEntity.ok(ApiResponse.success(response, "Product status updated successfully"));
    }

    @PostMapping("/{productId}/archive")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.delete')")
    @Operation(summary = "Archive a product and deactivate its variants while preserving historical default")
    public ResponseEntity<ApiResponse<ProductResponse>> archiveProduct(@PathVariable UUID productId) {
        productService.archiveProduct(productId);
        ProductResponse response = mapper.toResponse(catalogQueryService.getProductDetail(productId));
        return ResponseEntity.ok(ApiResponse.success(response, "Product archived successfully"));
    }

    @PostMapping("/{productId}/restore")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Restore an archived product to ACTIVE status and reactivate its historical default variant")
    public ResponseEntity<ApiResponse<ProductResponse>> restoreProduct(@PathVariable UUID productId) {
        productService.restoreProduct(productId);
        ProductResponse response = mapper.toResponse(catalogQueryService.getProductDetail(productId));
        return ResponseEntity.ok(ApiResponse.success(response, "Product restored successfully"));
    }

    @PostMapping("/{productId}/convert-to-variable")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Convert a SIMPLE product to VARIABLE while preserving its existing default variant")
    public ResponseEntity<ApiResponse<ProductResponse>> convertToVariable(
            @PathVariable UUID productId,
            @Valid @RequestBody ConvertProductToVariableRequest request
    ) {
        productService.convertSimpleToVariable(
                productId,
                request.variantOptions(),
                request.existingDefaultOptionValues()
        );
        ProductResponse response = mapper.toResponse(catalogQueryService.getProductDetail(productId));
        return ResponseEntity.ok(ApiResponse.success(response, "Product converted to VARIABLE successfully"));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.delete')")
    @Operation(summary = "Physically delete a DRAFT product")
    public ResponseEntity<ApiResponse<Void>> deleteDraftProduct(@PathVariable UUID productId) {
        productService.deleteDraftProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(null, "Draft product deleted successfully"));
    }
}
