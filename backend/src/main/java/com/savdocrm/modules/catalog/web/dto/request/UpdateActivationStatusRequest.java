package com.savdocrm.modules.catalog.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateActivationStatusRequest(
        @NotNull(message = "isActive flag is required.")
        Boolean isActive
) {
}
