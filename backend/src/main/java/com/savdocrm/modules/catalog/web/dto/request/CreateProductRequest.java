package com.savdocrm.modules.catalog.web.dto.request;

import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
        UUID categoryId,

        @NotNull(message = "Unit of measure ID is required.")
        UUID unitId,

        @NotBlank(message = "Product name is required.")
        @Size(max = 200, message = "Product name cannot exceed 200 characters.")
        String name,

        @Size(max = 4000, message = "Description cannot exceed 4000 characters.")
        String description,

        @Size(max = 100, message = "Brand cannot exceed 100 characters.")
        String brand,

        @NotNull(message = "Product type is required.")
        ProductType productType,

        @Size(max = 3, message = "A product can define at most 3 variant options.")
        List<@NotBlank(message = "Variant option name cannot be blank.") @Size(max = 80, message = "Variant option name cannot exceed 80 characters.") String> variantOptions,

        ProductStatus status,

        @NotEmpty(message = "At least one product variant is required.")
        @Valid
        List<@NotNull(message = "Variant entry cannot be null.") CreateVariantRequest> variants
) {
}
