package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.InvalidBarcodeException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared normalization and validation primitives for catalog value invariants.
 */
public final class CatalogValidation {

    public static final Pattern CATEGORY_CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{2,50}$");
    public static final Pattern UNIT_CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{1,20}$");
    public static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9._-]{2,100}$");

    public static final int MONEY_SCALE = 4;
    public static final int MAX_MONEY_INTEGER_DIGITS = 11; // NUMERIC(15,4) -> 15 - 4 = 11 integer digits
    public static final int MAX_BARCODE_LENGTH = 100;

    private CatalogValidation() {
    }

    public static String normalizeRequiredText(String raw, String fieldName, int maxLength) {
        if (raw == null || raw.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static String normalizeOptionalText(String raw, String fieldName, int maxLength) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static String normalizeCategoryCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isBlank()) {
            throw new IllegalArgumentException("Category code cannot be blank.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORY_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Category code must match pattern ^[A-Z0-9_-]{2,50}$: '" + normalized + "'."
            );
        }
        return normalized;
    }

    public static String normalizeUnitCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isBlank()) {
            throw new IllegalArgumentException("Unit code cannot be blank.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!UNIT_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Unit code must match pattern ^[A-Z0-9_-]{1,20}$: '" + normalized + "'."
            );
        }
        return normalized;
    }

    public static String normalizeSku(String rawSku) {
        if (rawSku == null || rawSku.trim().isBlank()) {
            throw new InvalidVariantConfigurationException("Variant SKU cannot be blank.");
        }
        String normalized = rawSku.trim().toUpperCase(Locale.ROOT);
        if (!SKU_PATTERN.matcher(normalized).matches()) {
            throw new InvalidVariantConfigurationException(
                    "Variant SKU must match pattern ^[A-Z0-9._-]{2,100}$: '" + normalized + "'."
            );
        }
        return normalized;
    }

    public static String normalizeBarcode(String rawBarcode) {
        if (rawBarcode == null || rawBarcode.trim().isBlank()) {
            throw new InvalidBarcodeException("Barcode cannot be blank.");
        }
        String trimmed = rawBarcode.trim();
        if (trimmed.length() > MAX_BARCODE_LENGTH) {
            throw new InvalidBarcodeException("Barcode cannot exceed " + MAX_BARCODE_LENGTH + " characters.");
        }
        return trimmed;
    }

    public static BigDecimal normalizeMoney(BigDecimal rawAmount, String fieldName) {
        if (rawAmount == null) {
            throw new InvalidVariantConfigurationException(fieldName + " cannot be null.");
        }
        if (rawAmount.signum() < 0) {
            throw new InvalidVariantConfigurationException(fieldName + " must be non-negative.");
        }
        BigDecimal scaled = rawAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        int integerDigits = scaled.precision() - scaled.scale();
        if (integerDigits > MAX_MONEY_INTEGER_DIGITS) {
            throw new InvalidVariantConfigurationException(
                    fieldName + " exceeds NUMERIC(15,4) precision bounds."
            );
        }
        return scaled;
    }
}
