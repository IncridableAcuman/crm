package com.savdocrm.modules.catalog.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record UpdateVariantRequest(
        @NotBlank(message = "Variant SKU is required.")
        @Size(min = 2, max = 100, message = "Variant SKU must be between 2 and 100 characters.")
        @Pattern(
                regexp = "^(?i)\\s*[A-Z0-9._-]{2,100}\\s*$",
                message = "Variant SKU may only contain letters, digits, '.', '_', and '-'."
        )
        String sku,

        @NotBlank(message = "Variant name is required.")
        @Size(max = 220, message = "Variant name cannot exceed 220 characters.")
        String name,

        Map<@NotBlank(message = "Option key cannot be blank.") String, @NotBlank(message = "Option value cannot be blank.") String> optionValues,

        @DecimalMin(value = "0.0", inclusive = true, message = "Cost price must be non-negative.")
        @Digits(integer = 11, fraction = 4, message = "Cost price must fit NUMERIC(15,4) bounds.")
        BigDecimal costPrice,

        @DecimalMin(value = "0.0", inclusive = true, message = "Selling price must be non-negative.")
        @Digits(integer = 11, fraction = 4, message = "Selling price must fit NUMERIC(15,4) bounds.")
        BigDecimal sellingPrice
) {
}
