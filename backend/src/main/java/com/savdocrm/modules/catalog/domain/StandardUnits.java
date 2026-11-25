package com.savdocrm.modules.catalog.domain;

import java.util.List;

/**
 * Canonical definitions of the 8 standard system-seeded units of measure.
 * Matches Liquibase migration v1.0.0-18-seed-standard-units-of-measure.
 */
public final class StandardUnits {

    private StandardUnits() {
    }

    public record StandardUnitTemplate(
            String code,
            String name,
            String symbol,
            boolean allowDecimal,
            short decimalPrecision
    ) {
    }

    public static final List<StandardUnitTemplate> TEMPLATES = List.of(
            new StandardUnitTemplate("PCS", "Piece", "pcs", false, (short) 0),
            new StandardUnitTemplate("KG", "Kilogram", "kg", true, (short) 3),
            new StandardUnitTemplate("G", "Gram", "g", false, (short) 0),
            new StandardUnitTemplate("L", "Liter", "l", true, (short) 3),
            new StandardUnitTemplate("ML", "Milliliter", "ml", false, (short) 0),
            new StandardUnitTemplate("M", "Meter", "m", true, (short) 2),
            new StandardUnitTemplate("BOX", "Box", "box", false, (short) 0),
            new StandardUnitTemplate("PACK", "Package", "pack", false, (short) 0)
    );
}
