package com.savdocrm.modules.catalog.web.dto.response;

import java.util.UUID;

public record CategorySummaryResponse(
        UUID id,
        String code,
        String name
) {
}
