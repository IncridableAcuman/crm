package com.savdocrm.modules.catalog.web.dto.request;

import com.savdocrm.modules.catalog.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(
        @NotNull(message = "Target product status is required.")
        ProductStatus status
) {
}
