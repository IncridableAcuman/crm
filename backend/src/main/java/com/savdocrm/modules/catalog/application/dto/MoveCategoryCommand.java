package com.savdocrm.modules.catalog.application.dto;

import java.util.UUID;

public record MoveCategoryCommand(
        UUID newParentId
) {
}
