package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.AddBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.UpdateBarcodeCommand;
import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.modules.catalog.domain.ProductVariant;
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
        return addBarcodeInternal(null, variantId, command);
    }

    @Transactional
    public ProductBarcodeDto addBarcode(UUID productId, UUID variantId, AddBarcodeCommand command) {
        Objects.requireNonNull(productId, "Product ID cannot be null.");
        return addBarcodeInternal(productId, variantId, command);
    }

    private ProductBarcodeDto addBarcodeInternal(UUID expectedProductId, UUID variantId, AddBarcodeCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "AddBarcodeCommand cannot be null.");

        // Lock owning variant so primary-barcode transitions serialize safely
        ProductVariant variant = variantRepository.findByTenantIdAndIdWithLock(tenantId, variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));
        assertVariantBelongsToProduct(variant, expectedProductId);

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
        return updateBarcodeInternal(null, null, barcodeId, command);
    }

    @Transactional
    public ProductBarcodeDto updateBarcode(UUID productId, UUID variantId, UUID barcodeId, UpdateBarcodeCommand command) {
        Objects.requireNonNull(productId, "Product ID cannot be null.");
        Objects.requireNonNull(variantId, "Variant ID cannot be null.");
        return updateBarcodeInternal(productId, variantId, barcodeId, command);
    }

    private ProductBarcodeDto updateBarcodeInternal(
            UUID expectedProductId,
            UUID expectedVariantId,
            UUID barcodeId,
            UpdateBarcodeCommand command
    ) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "UpdateBarcodeCommand cannot be null.");

        ProductBarcode initial = barcodeRepository.findByTenantIdAndId(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));

        ProductVariant variant = variantRepository.findByTenantIdAndIdWithLock(tenantId, initial.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException(initial.getVariantId()));
        assertOwnership(variant, expectedProductId, expectedVariantId, barcodeId);

        // Re-load under owning variant lock to avoid acting on a stale pre-lock snapshot
        ProductBarcode barcode = barcodeRepository.findByTenantIdAndIdWithLock(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));
        if (expectedVariantId != null && !Objects.equals(barcode.getVariantId(), expectedVariantId)) {
            throw new ProductBarcodeNotFoundException(barcodeId);
        }

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
        return setPrimaryBarcodeInternal(null, null, barcodeId);
    }

    @Transactional
    public ProductBarcodeDto setPrimaryBarcode(UUID productId, UUID variantId, UUID barcodeId) {
        Objects.requireNonNull(productId, "Product ID cannot be null.");
        Objects.requireNonNull(variantId, "Variant ID cannot be null.");
        return setPrimaryBarcodeInternal(productId, variantId, barcodeId);
    }

    private ProductBarcodeDto setPrimaryBarcodeInternal(UUID expectedProductId, UUID expectedVariantId, UUID barcodeId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        ProductBarcode initial = barcodeRepository.findByTenantIdAndId(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));

        ProductVariant variant = variantRepository.findByTenantIdAndIdWithLock(tenantId, initial.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException(initial.getVariantId()));
        assertOwnership(variant, expectedProductId, expectedVariantId, barcodeId);

        // Re-load under owning variant lock to avoid stale pre-lock state
        ProductBarcode barcode = barcodeRepository.findByTenantIdAndIdWithLock(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));
        if (expectedVariantId != null && !Objects.equals(barcode.getVariantId(), expectedVariantId)) {
            throw new ProductBarcodeNotFoundException(barcodeId);
        }

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
        removeBarcodeInternal(null, null, barcodeId);
    }

    @Transactional
    public void removeBarcode(UUID productId, UUID variantId, UUID barcodeId) {
        Objects.requireNonNull(productId, "Product ID cannot be null.");
        Objects.requireNonNull(variantId, "Variant ID cannot be null.");
        removeBarcodeInternal(productId, variantId, barcodeId);
    }

    private void removeBarcodeInternal(UUID expectedProductId, UUID expectedVariantId, UUID barcodeId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        ProductBarcode initial = barcodeRepository.findByTenantIdAndId(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));

        ProductVariant variant = variantRepository.findByTenantIdAndIdWithLock(tenantId, initial.getVariantId())
                .orElseThrow(() -> new ProductVariantNotFoundException(initial.getVariantId()));
        assertOwnership(variant, expectedProductId, expectedVariantId, barcodeId);

        ProductBarcode barcode = barcodeRepository.findByTenantIdAndIdWithLock(tenantId, barcodeId)
                .orElseThrow(() -> new ProductBarcodeNotFoundException(barcodeId));
        if (expectedVariantId != null && !Objects.equals(barcode.getVariantId(), expectedVariantId)) {
            throw new ProductBarcodeNotFoundException(barcodeId);
        }

        barcodeRepository.deleteByTenantIdAndId(tenantId, barcode.getId());
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
        return getBarcodesForVariantInternal(null, variantId);
    }

    @Transactional(readOnly = true)
    public List<ProductBarcodeDto> getBarcodesForVariant(UUID productId, UUID variantId) {
        Objects.requireNonNull(productId, "Product ID cannot be null.");
        return getBarcodesForVariantInternal(productId, variantId);
    }

    private List<ProductBarcodeDto> getBarcodesForVariantInternal(UUID expectedProductId, UUID variantId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        ProductVariant variant = variantRepository.findByTenantIdAndId(tenantId, variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));
        assertVariantBelongsToProduct(variant, expectedProductId);
        return barcodeRepository.findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(tenantId, variantId)
                .stream()
                .map(ProductBarcodeDto::fromEntity)
                .toList();
    }

    private void assertVariantBelongsToProduct(ProductVariant variant, UUID expectedProductId) {
        if (expectedProductId != null && !Objects.equals(variant.getProductId(), expectedProductId)) {
            throw new ProductVariantNotFoundException(variant.getId());
        }
    }

    private void assertOwnership(ProductVariant variant, UUID expectedProductId, UUID expectedVariantId, UUID barcodeId) {
        assertVariantBelongsToProduct(variant, expectedProductId);
        if (expectedVariantId != null && !Objects.equals(variant.getId(), expectedVariantId)) {
            throw new ProductBarcodeNotFoundException(barcodeId);
        }
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
