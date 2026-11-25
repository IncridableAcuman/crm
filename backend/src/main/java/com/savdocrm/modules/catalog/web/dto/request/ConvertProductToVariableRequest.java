package com.savdocrm.modules.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ConvertProductToVariableRequest(
        @NotEmpty(message = "At least one variant option is required.")
        @Size(max = 3, message = "A product can define at most 3 variant options.")
        List<@NotBlank(message = "Variant option name cannot be blank.") @Size(max = 80, message = "Variant option name cannot exceed 80 characters.") String> variantOptions,

        @NotEmpty(message = "Option values for the existing default variant are required.")
        Map<@NotBlank(message = "Option key cannot be blank.") String, @NotBlank(message = "Option value cannot be blank.") String> existingDefaultOptionValues
) {
}
