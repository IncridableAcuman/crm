package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductVariantDto;
import com.savdocrm.modules.catalog.application.dto.UpdateVariantCommand;
import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.exception.DuplicateSkuException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateVariantOptionsException;
import com.savdocrm.modules.catalog.domain.exception.InvalidProductConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.ProductNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.ProductVariantNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final CatalogVariantHelper variantHelper;

    @Transactional
    public ProductVariantDto addVariant(UUID productId, CreateVariantCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "CreateVariantCommand cannot be null.");

        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        assertNotArchived(product);

        if (product.getProductType() == ProductType.SIMPLE) {
            throw new InvalidVariantConfigurationException(
                    "Cannot add variants to a SIMPLE product. Convert the product to VARIABLE first."
            );
        }

        List<ProductVariant> existingVariants = variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, productId);
        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();

        boolean becomeDefault = command.isDefault() || existingVariants.isEmpty();
        if (command.isDefault() && !existingVariants.isEmpty()) {
            for (ProductVariant existing : existingVariants) {
                if (existing.isDefault()) {
                    existing.unmarkAsDefault();
                    existing.setUpdatedBy(actorUserId);
                    variantRepository.saveAndFlush(existing);
                }
            }
        }

        return variantHelper.createAndPersistVariantWithBarcodes(
                tenantId, product, command, becomeDefault, existingVariants, actorUserId
        );
    }

    @Transactional
    public ProductVariantDto updateVariant(UUID productId, UUID variantId, UpdateVariantCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "UpdateVariantCommand cannot be null.");

        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        assertNotArchived(product);

        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, productId);
        ProductVariant target = findVariantInList(variants, variantId);

        String normalizedSku = CatalogValidation.normalizeSku(command.sku());
        if (variantRepository.existsByTenantIdAndSkuAndIdNot(tenantId, normalizedSku, variantId)) {
            throw new DuplicateSkuException("Variant SKU '" + normalizedSku + "' already exists in this tenant.");
        }

        target.update(
                product,
                normalizedSku,
                command.name(),
                command.optionValues(),
                command.costPrice(),
                command.sellingPrice()
        );

        for (ProductVariant sibling : variants) {
            if (!Objects.equals(sibling.getId(), variantId)
                    && Objects.equals(sibling.getOptionValues(), target.getOptionValues())) {
                throw new DuplicateVariantOptionsException(
                        "Another variant with option values " + target.getOptionValues() + " already exists on this product."
                );
            }
        }

        target.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());

        try {
            ProductVariant saved = variantRepository.saveAndFlush(target);
            return assembleVariantDto(tenantId, product.getStatus(), saved);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public ProductVariantDto changeDefaultVariant(UUID productId, UUID newDefaultVariantId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        // Serialize at aggregate root level so concurrent default switches cannot create 0 or >1 defaults
        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        assertNotArchived(product);

        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, productId);
        ProductVariant target = findVariantInList(variants, newDefaultVariantId);

        if (target.isDefault()) {
            return assembleVariantDto(tenantId, product.getStatus(), target);
        }

        // Validate target eligibility (must be active) BEFORE unmarking the current default
        if (!target.isActive()) {
            target.markAsDefault(); // throws InvalidDefaultVariantTransitionException
        }

        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        for (ProductVariant existing : variants) {
            if (existing.isDefault()) {
                existing.unmarkAsDefault();
                existing.setUpdatedBy(actorUserId);
                variantRepository.saveAndFlush(existing);
            }
        }

        target.markAsDefault();
        target.setUpdatedBy(actorUserId);
        try {
            ProductVariant saved = variantRepository.saveAndFlush(target);
            return assembleVariantDto(tenantId, product.getStatus(), saved);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public ProductVariantDto deactivateVariant(UUID productId, UUID variantId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        assertNotArchived(product);

        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, productId);
        ProductVariant target = findVariantInList(variants, variantId);

        if (!target.isActive()) {
            return assembleVariantDto(tenantId, product.getStatus(), target);
        }

        long activeCount = variants.stream().filter(ProductVariant::isActive).count();
        target.deactivate(activeCount);
        target.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());

        ProductVariant saved = variantRepository.saveAndFlush(target);
        return assembleVariantDto(tenantId, product.getStatus(), saved);
    }

    @Transactional
    public ProductVariantDto activateVariant(UUID productId, UUID variantId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        assertNotArchived(product);

        ProductVariant target = variantRepository.findByTenantIdAndIdWithLock(tenantId, variantId)
                .filter(v -> Objects.equals(v.getProductId(), productId))
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));

        target.activate();
        target.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());
        ProductVariant saved = variantRepository.saveAndFlush(target);
        return assembleVariantDto(tenantId, product.getStatus(), saved);
    }

    @Transactional(readOnly = true)
    public ProductVariantDto getVariant(UUID productId, UUID variantId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        ProductVariant variant = variantRepository.findByTenantIdAndId(tenantId, variantId)
                .filter(v -> Objects.equals(v.getProductId(), productId))
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));
        return assembleVariantDto(tenantId, product.getStatus(), variant);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantDto> listVariants(UUID productId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        List<ProductVariant> variants = variantRepository
                .findAllByTenantIdAndProductIdOrderByIsDefaultDescCreatedAtAsc(tenantId, productId);
        var barcodesByVariant = variantHelper.loadBarcodesByVariantIds(
                tenantId, variants.stream().map(ProductVariant::getId).toList()
        );
        return variants.stream()
                .map(v -> ProductVariantDto.fromEntity(
                        v, product.getStatus(), barcodesByVariant.getOrDefault(v.getId(), List.of())
                ))
                .toList();
    }

    private void assertNotArchived(Product product) {
        if (product.isArchived()) {
            throw new InvalidProductConfigurationException(
                    "Cannot mutate variants of an ARCHIVED product; restore the product first."
            );
        }
    }

    private ProductVariant findVariantInList(List<ProductVariant> variants, UUID variantId) {
        return variants.stream()
                .filter(v -> Objects.equals(v.getId(), variantId))
                .findFirst()
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));
    }

    private ProductVariantDto assembleVariantDto(UUID tenantId, ProductStatus productStatus, ProductVariant variant) {
        List<ProductBarcodeDto> barcodes = barcodeRepository
                .findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(tenantId, variant.getId())
                .stream()
                .map(ProductBarcodeDto::fromEntity)
                .toList();
        return ProductVariantDto.fromEntity(variant, productStatus, barcodes);
    }
}
