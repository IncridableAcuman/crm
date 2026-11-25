package com.savdocrm.modules.catalog.web;

import com.savdocrm.modules.catalog.application.ProductBarcodeService;
import com.savdocrm.modules.catalog.application.ProductVariantService;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductVariantDto;
import com.savdocrm.modules.catalog.web.dto.request.AddBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateVariantRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateActivationStatusRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateVariantRequest;
import com.savdocrm.modules.catalog.web.dto.response.ProductBarcodeResponse;
import com.savdocrm.modules.catalog.web.dto.response.ProductVariantResponse;
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

@RestController
@RequestMapping("/api/v1/products/{productId}/variants")
@RequiredArgsConstructor
@Tag(name = "Product Variants & Barcodes", description = "Variant and Barcode Management under Product Aggregate Root")
@SecurityRequirement(name = "bearerAuth")
public class ProductVariantController {

    private final ProductVariantService variantService;
    private final ProductBarcodeService barcodeService;
    private final CatalogWebMapper mapper;

    @GetMapping
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "List all variants for a product")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> listVariants(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID branchId
    ) {
        List<ProductVariantResponse> variants = mapper.toVariantResponses(variantService.listVariants(productId));
        return ResponseEntity.ok(ApiResponse.success(variants, "Variants retrieved successfully"));
    }

    @GetMapping("/{variantId}")
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "Get a specific product variant")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> getVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @RequestParam(required = false) UUID branchId
    ) {
        ProductVariantResponse variant = mapper.toResponse(variantService.getVariant(productId, variantId));
        return ResponseEntity.ok(ApiResponse.success(variant, "Variant retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Add a new variant to a VARIABLE product")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> addVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateVariantRequest request
    ) {
        ProductVariantDto created = variantService.addVariant(productId, mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mapper.toResponse(created), "Variant added successfully"));
    }

    @PutMapping("/{variantId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Update a product variant")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest request
    ) {
        ProductVariantDto updated = variantService.updateVariant(productId, variantId, mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Variant updated successfully"));
    }

    @PatchMapping("/{variantId}/default")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Promote an active variant to default for the product")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> changeDefaultVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId
    ) {
        ProductVariantDto updated = variantService.changeDefaultVariant(productId, variantId);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Default variant updated successfully"));
    }

    @PatchMapping("/{variantId}/status")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Activate or deactivate a product variant")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariantStatus(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateActivationStatusRequest request
    ) {
        ProductVariantDto updated = Boolean.TRUE.equals(request.isActive())
                ? variantService.activateVariant(productId, variantId)
                : variantService.deactivateVariant(productId, variantId);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Variant status updated successfully"));
    }

    @GetMapping("/{variantId}/barcodes")
    @PreAuthorize("#branchId == null ? @authorizationService.hasPermission('catalog.products.read') : @authorizationService.hasBranchPermission('catalog.products.read', #branchId)")
    @Operation(summary = "List barcodes assigned to a variant")
    public ResponseEntity<ApiResponse<List<ProductBarcodeResponse>>> listBarcodes(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @RequestParam(required = false) UUID branchId
    ) {
        List<ProductBarcodeResponse> barcodes = mapper.toBarcodeResponses(
                barcodeService.getBarcodesForVariant(productId, variantId)
        );
        return ResponseEntity.ok(ApiResponse.success(barcodes, "Barcodes retrieved successfully"));
    }

    @PostMapping("/{variantId}/barcodes")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Add a barcode to a variant")
    public ResponseEntity<ApiResponse<ProductBarcodeResponse>> addBarcode(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody AddBarcodeRequest request
    ) {
        ProductBarcodeDto created = barcodeService.addBarcode(productId, variantId, mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mapper.toResponse(created), "Barcode added successfully"));
    }

    @PutMapping("/{variantId}/barcodes/{barcodeId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Update a variant barcode")
    public ResponseEntity<ApiResponse<ProductBarcodeResponse>> updateBarcode(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @PathVariable UUID barcodeId,
            @Valid @RequestBody UpdateBarcodeRequest request
    ) {
        ProductBarcodeDto updated = barcodeService.updateBarcode(productId, variantId, barcodeId, mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Barcode updated successfully"));
    }

    @PatchMapping("/{variantId}/barcodes/{barcodeId}/primary")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Set a barcode as primary for the variant")
    public ResponseEntity<ApiResponse<ProductBarcodeResponse>> setPrimaryBarcode(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @PathVariable UUID barcodeId
    ) {
        ProductBarcodeDto updated = barcodeService.setPrimaryBarcode(productId, variantId, barcodeId);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated), "Primary barcode updated successfully"));
    }

    @DeleteMapping("/{variantId}/barcodes/{barcodeId}")
    @PreAuthorize("@authorizationService.hasPermission('catalog.products.update')")
    @Operation(summary = "Remove a barcode from a variant")
    public ResponseEntity<ApiResponse<Void>> removeBarcode(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @PathVariable UUID barcodeId
    ) {
        barcodeService.removeBarcode(productId, variantId, barcodeId);
        return ResponseEntity.ok(ApiResponse.success(null, "Barcode removed successfully"));
    }
}
