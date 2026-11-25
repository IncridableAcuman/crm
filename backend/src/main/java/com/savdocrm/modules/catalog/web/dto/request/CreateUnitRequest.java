package com.savdocrm.modules.catalog.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUnitRequest(
        @NotBlank(message = "Unit code is required.")
        @Size(min = 1, max = 20, message = "Unit code must be between 1 and 20 characters.")
        @Pattern(
                regexp = "^(?i)\\s*[A-Z0-9_-]{1,20}\\s*$",
                message = "Unit code may only contain letters, digits, '_', and '-'."
        )
        String code,

        @NotBlank(message = "Unit name is required.")
        @Size(max = 80, message = "Unit name cannot exceed 80 characters.")
        String name,

        @NotBlank(message = "Unit symbol is required.")
        @Size(max = 15, message = "Unit symbol cannot exceed 15 characters.")
        String symbol,

        boolean allowDecimal,

        @Min(value = 0, message = "Decimal precision must be between 0 and 3.")
        @Max(value = 3, message = "Decimal precision must be between 0 and 3.")
        int decimalPrecision
) {
}
