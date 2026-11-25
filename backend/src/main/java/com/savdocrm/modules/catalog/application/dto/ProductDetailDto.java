package com.savdocrm.modules.catalog.application.dto;

/**
 * Enriched product detail projection including category and unit metadata alongside variants.
 */
public record ProductDetailDto(
        ProductDto product,
        CategoryDto category,
        UnitOfMeasureDto unit
) {
}
