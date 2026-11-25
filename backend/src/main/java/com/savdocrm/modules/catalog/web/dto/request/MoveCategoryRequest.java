package com.savdocrm.modules.catalog.web.dto.request;

import java.util.UUID;

public record MoveCategoryRequest(
        UUID newParentId
) {
}
