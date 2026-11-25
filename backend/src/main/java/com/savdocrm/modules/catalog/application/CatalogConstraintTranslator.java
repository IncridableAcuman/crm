package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.domain.exception.DuplicateBarcodeException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateCategoryException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateSkuException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateUnitCodeException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateVariantOptionsException;
import com.savdocrm.modules.catalog.domain.exception.InvalidDefaultVariantTransitionException;
import com.savdocrm.modules.catalog.domain.exception.InvalidPrimaryBarcodeTransitionException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * Translates database unique constraint violations (triggered under concurrent races)
 * into clean catalog domain exceptions without exposing SQL implementation details.
 */
final class CatalogConstraintTranslator {

    private CatalogConstraintTranslator() {
    }

    static RuntimeException translate(DataIntegrityViolationException ex) {
        String message = collectMessagesLowerCase(ex);
        if (message.contains("uq_product_variants_tenant_sku")) {
            return new DuplicateSkuException("A variant with this SKU already exists in the tenant.");
        }
        if (message.contains("uq_product_variants_product_options")) {
            return new DuplicateVariantOptionsException("A variant with these option values already exists on this product.");
        }
        if (message.contains("uq_product_variants_default_per_product")) {
            return new InvalidDefaultVariantTransitionException("A product can have at most one default variant.");
        }
        if (message.contains("uq_product_barcodes_tenant_barcode")) {
            return new DuplicateBarcodeException("This barcode is already assigned to a variant in the tenant.");
        }
        if (message.contains("uq_product_barcodes_primary_per_variant")) {
            return new InvalidPrimaryBarcodeTransitionException("A variant can have at most one primary barcode.");
        }
        if (message.contains("uq_product_categories_tenant_code")
                || message.contains("uq_product_categories_root_name")
                || message.contains("uq_product_categories_child_name")) {
            return new DuplicateCategoryException("A category with this code or sibling name already exists in the tenant.");
        }
        if (message.contains("uq_units_of_measure_tenant_code")) {
            return new DuplicateUnitCodeException("A unit of measure with this code already exists in the tenant.");
        }
        return ex;
    }

    private static String collectMessagesLowerCase(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage().toLowerCase(Locale.ROOT)).append(' ');
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }
}
