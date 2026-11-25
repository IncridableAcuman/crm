package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.BarcodeInput;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductVariantDto;
import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.exception.DuplicateBarcodeException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateSkuException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateVariantOptionsException;
import com.savdocrm.modules.catalog.domain.exception.InvalidPrimaryBarcodeTransitionException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Shared internal helper for validating and persisting variants and their barcodes
 * consistently across ProductService and ProductVariantService.
 */
@Component
@RequiredArgsConstructor
class CatalogVariantHelper {

    private final ProductVariantRepository variantRepository;
    private final ProductBarcodeRepository barcodeRepository;

    ProductVariantDto createAndPersistVariantWithBarcodes(
            UUID tenantId,
            Product product,
            CreateVariantCommand command,
            boolean isDefault,
            List<ProductVariant> existingVariants,
            UUID actorUserId
    ) {
        String normalizedSku = CatalogValidation.normalizeSku(command.sku());
        if (variantRepository.existsByTenantIdAndSku(tenantId, normalizedSku)) {
            throw new DuplicateSkuException("Variant SKU '" + normalizedSku + "' already exists in this tenant.");
        }

        ProductVariant variant = ProductVariant.create(
                tenantId,
                product,
                normalizedSku,
                command.name(),
                command.optionValues(),
                command.costPrice(),
                command.sellingPrice(),
                isDefault
        );
        variant.setCreatedBy(actorUserId);
        variant.setUpdatedBy(actorUserId);

        for (ProductVariant existing : existingVariants) {
            if (Objects.equals(existing.getOptionValues(), variant.getOptionValues())) {
                throw new DuplicateVariantOptionsException(
                        "A variant with option values " + variant.getOptionValues() + " already exists on this product."
                );
            }
        }

        ProductVariant savedVariant;
        try {
            savedVariant = variantRepository.saveAndFlush(variant);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }

        List<ProductBarcodeDto> savedBarcodes = persistInitialBarcodes(
                tenantId, savedVariant.getId(), command.barcodes(), actorUserId
        );
        return ProductVariantDto.fromEntity(savedVariant, savedBarcodes);
    }

    List<ProductBarcodeDto> persistInitialBarcodes(
            UUID tenantId,
            UUID variantId,
            List<BarcodeInput> barcodeInputs,
            UUID actorUserId
    ) {
        if (barcodeInputs == null || barcodeInputs.isEmpty()) {
            return List.of();
        }

        Set<String> seenInBatch = new HashSet<>();
        boolean primarySeen = false;
        List<ProductBarcode> toSave = new ArrayList<>(barcodeInputs.size());

        for (BarcodeInput input : barcodeInputs) {
            if (input == null) {
                continue;
            }
            if (input.isPrimary()) {
                if (primarySeen) {
                    throw new InvalidPrimaryBarcodeTransitionException(
                            "A variant can have at most one primary barcode."
                    );
                }
                primarySeen = true;
            }
            String normalized = CatalogValidation.normalizeBarcode(input.barcode());
            if (!seenInBatch.add(normalized)) {
                throw new DuplicateBarcodeException("Duplicate barcode '" + normalized + "' in request.");
            }
            if (barcodeRepository.existsByTenantIdAndBarcode(tenantId, normalized)) {
                throw new DuplicateBarcodeException("Barcode '" + normalized + "' already exists in this tenant.");
            }

            ProductBarcode barcode = ProductBarcode.create(
                    tenantId, variantId, normalized, input.barcodeType(), input.isPrimary()
            );
            barcode.setCreatedBy(actorUserId);
            barcode.setUpdatedBy(actorUserId);
            toSave.add(barcode);
        }

        try {
            List<ProductBarcode> saved = barcodeRepository.saveAllAndFlush(toSave);
            return saved.stream().map(ProductBarcodeDto::fromEntity).toList();
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    Map<UUID, List<ProductBarcodeDto>> loadBarcodesByVariantIds(UUID tenantId, List<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return barcodeRepository.findAllByTenantIdAndVariantIdIn(tenantId, variantIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProductBarcode::getVariantId,
                        java.util.stream.Collectors.mapping(ProductBarcodeDto::fromEntity, java.util.stream.Collectors.toList())
                ));
    }
}
