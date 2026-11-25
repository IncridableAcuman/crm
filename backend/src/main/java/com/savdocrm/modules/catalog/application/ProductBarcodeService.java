package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.AddBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.UpdateBarcodeCommand;
import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.modules.catalog.domain.exception.DuplicateBarcodeException;
import com.savdocrm.modules.catalog.domain.exception.ProductBarcodeNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.ProductVariantNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductBarcodeService {

    private final ProductBarcodeRepository barcodeRepository;
    private final ProductVariantRepository variantRepository;

    @Transactional
    public ProductBarcodeDto addBarcode(UUID variantId, AddBarcodeCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "AddBarcodeCommand cannot be null.");

        // Lock owning variant so primary-barcode transitions serialize safely
        variantRepository.findByTenantIdAndIdWithLock(tenantId, variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));

        String normalized = CatalogValidation.normalizeBarcode(command.barcode());
        if (barcodeRepository.existsByTenantIdAndBarcode(tenantId, normalized)) {
            throw new DuplicateBarcodeException("Barcode '" + normalized + "' already exists in this tenant.");
        }

        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        if (command.isPrimary()) {
            clearPrimaryBarcodeIfPresent(tenantId, variantId, actorUserId);
        }

        ProductBarcode barcode = ProductBarcode.create(
                tenantId, variantId, normalized, command.barcodeType(), command.isPrimary()
        );
        barcode.setCreatedBy(actorUserId);
        barcode.setUpdatedBy(actorUserId);

        try {
            return ProductBarcodeDto.fromEntity(barcodeRepository.saveAndFlush(barcode));
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public ProductBarcodeDto updateBarcode(UUID barcodeId, UpdateBarcodeCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "UpdateBarcodeCommand cannot be null.");

        ProductBarcode barcode = barcodeRepository.findByTenantIdAndId(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));

        variantRepository.findByTenantIdAndIdWithLock(tenantId, barcode.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException(barcode.getVariantId()));

        String normalized = CatalogValidation.normalizeBarcode(command.barcode());
        if (barcodeRepository.existsByTenantIdAndBarcodeAndIdNot(tenantId, normalized, barcodeId)) {
            throw new DuplicateBarcodeException("Barcode '" + normalized + "' already exists in this tenant.");
        }

        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        if (command.isPrimary() && !barcode.isPrimary()) {
            clearPrimaryBarcodeIfPresent(tenantId, barcode.getVariantId(), actorUserId);
        }

        barcode.update(normalized, command.barcodeType(), command.isPrimary());
        barcode.setUpdatedBy(actorUserId);

        try {
            return ProductBarcodeDto.fromEntity(barcodeRepository.saveAndFlush(barcode));
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public ProductBarcodeDto setPrimaryBarcode(UUID barcodeId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        ProductBarcode barcode = barcodeRepository.findByTenantIdAndId(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));

        variantRepository.findByTenantIdAndIdWithLock(tenantId, barcode.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException(barcode.getVariantId()));

        if (barcode.isPrimary()) {
            return ProductBarcodeDto.fromEntity(barcode);
        }

        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        clearPrimaryBarcodeIfPresent(tenantId, barcode.getVariantId(), actorUserId);

        barcode.markAsPrimary();
        barcode.setUpdatedBy(actorUserId);

        try {
            return ProductBarcodeDto.fromEntity(barcodeRepository.saveAndFlush(barcode));
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public void removeBarcode(UUID barcodeId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        ProductBarcode barcode = barcodeRepository.findByTenantIdAndId(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));

        variantRepository.findByTenantIdAndIdWithLock(tenantId, barcode.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException(barcode.getVariantId()));

        barcodeRepository.delete(barcode);
        barcodeRepository.flush();
    }

    @Transactional(readOnly = true)
    public Optional<ProductBarcodeDto> findByBarcode(String rawBarcode) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        String normalized = CatalogValidation.normalizeBarcode(rawBarcode);
        return barcodeRepository.findByTenantIdAndBarcode(tenantId, normalized)
                .map(ProductBarcodeDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<ProductBarcodeDto> getBarcodesForVariant(UUID variantId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (!variantRepository.existsByTenantIdAndId(tenantId, variantId)) {
            throw new ProductVariantNotFoundException(variantId);
        }
        return barcodeRepository.findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(tenantId, variantId)
                .stream()
                .map(ProductBarcodeDto::fromEntity)
                .toList();
    }

    private void clearPrimaryBarcodeIfPresent(UUID tenantId, UUID variantId, UUID actorUserId) {
        barcodeRepository.findByTenantIdAndVariantIdAndIsPrimaryTrue(tenantId, variantId)
                .ifPresent(existingPrimary -> {
                    existingPrimary.unmarkAsPrimary();
                    existingPrimary.setUpdatedBy(actorUserId);
                    barcodeRepository.saveAndFlush(existingPrimary);
                });
    }
}
