package com.savdocrm.modules.catalog.application.facade;

import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.UnitOfMeasure;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryFacadeImpl implements ProductQueryFacade {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final ProductBarcodeRepository barcodeRepository;

    @Override
    public Optional<VariantCatalogDto> findVariantById(UUID variantId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (variantId == null) {
            return Optional.empty();
        }
        return variantRepository.findByTenantIdAndId(tenantId, variantId)
                .flatMap(variant -> assembleSingle(tenantId, variant));
    }

    @Override
    public List<VariantCatalogDto> findVariantsByIds(Set<UUID> variantIds) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (variantIds == null || variantIds.isEmpty()) {
            return List.of();
        }
        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndIdIn(tenantId, variantIds);
        if (variants.isEmpty()) {
            return List.of();
        }

        Set<UUID> productIds = variants.stream().map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productsById = productIds.stream()
                .map(pid -> productRepository.findByTenantIdAndId(tenantId, pid))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Set<UUID> unitIds = productsById.values().stream().map(Product::getUnitId).collect(Collectors.toSet());
        Map<UUID, UnitOfMeasure> unitsById = unitIds.stream()
                .map(uid -> unitOfMeasureRepository.findByTenantIdAndId(tenantId, uid))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(UnitOfMeasure::getId, Function.identity()));

        List<UUID> loadedVariantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, String> primaryBarcodes = barcodeRepository.findAllByTenantIdAndVariantIdIn(tenantId, loadedVariantIds)
                .stream()
                .filter(ProductBarcode::isPrimary)
                .collect(Collectors.toMap(ProductBarcode::getVariantId, ProductBarcode::getBarcode, (a, b) -> a));

        return variants.stream()
                .map(v -> {
                    Product product = productsById.get(v.getProductId());
                    UnitOfMeasure unit = product != null ? unitsById.get(product.getUnitId()) : null;
                    if (product == null || unit == null) {
                        return null;
                    }
                    return toDto(product, v, unit, primaryBarcodes.get(v.getId()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<VariantCatalogDto> findActiveVariantByBarcode(String barcode) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (barcode == null || barcode.trim().isBlank()) {
            return Optional.empty();
        }
        String normalized = CatalogValidation.normalizeBarcode(barcode);
        return barcodeRepository.findByTenantIdAndBarcode(tenantId, normalized)
                .flatMap(b -> variantRepository.findByTenantIdAndId(tenantId, b.getVariantId()))
                .filter(ProductVariant::isActive)
                .flatMap(v -> assembleSingle(tenantId, v))
                .filter(VariantCatalogDto::sellable);
    }

    @Override
    public Optional<VariantCatalogDto> findActiveVariantBySku(String sku) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (sku == null || sku.trim().isBlank()) {
            return Optional.empty();
        }
        String normalizedSku = CatalogValidation.normalizeSku(sku);
        return variantRepository.findByTenantIdAndSku(tenantId, normalizedSku)
                .filter(ProductVariant::isActive)
                .flatMap(v -> assembleSingle(tenantId, v))
                .filter(VariantCatalogDto::sellable);
    }

    private Optional<VariantCatalogDto> assembleSingle(UUID tenantId, ProductVariant variant) {
        Optional<Product> productOpt = productRepository.findByTenantIdAndId(tenantId, variant.getProductId());
        if (productOpt.isEmpty()) {
            return Optional.empty();
        }
        Product product = productOpt.get();
        Optional<UnitOfMeasure> unitOpt = unitOfMeasureRepository.findByTenantIdAndId(tenantId, product.getUnitId());
        if (unitOpt.isEmpty()) {
            return Optional.empty();
        }
        String primaryBarcode = barcodeRepository.findByTenantIdAndVariantIdAndIsPrimaryTrue(tenantId, variant.getId())
                .map(ProductBarcode::getBarcode)
                .orElse(null);
        return Optional.of(toDto(product, variant, unitOpt.get(), primaryBarcode));
    }

    private VariantCatalogDto toDto(Product product, ProductVariant variant, UnitOfMeasure unit, String primaryBarcode) {
        boolean sellable = product.getStatus() == ProductStatus.ACTIVE && variant.isActive();
        boolean activeDefault = product.getStatus() != ProductStatus.ARCHIVED && variant.isDefault() && variant.isActive();
        Map<String, String> options = variant.getOptionValues() == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(variant.getOptionValues()));
        return new VariantCatalogDto(
                variant.getId(),
                product.getId(),
                product.getName(),
                variant.getName(),
                variant.getSku(),
                primaryBarcode,
                options,
                unit.getId(),
                unit.getCode(),
                unit.getSymbol(),
                unit.isAllowDecimal(),
                unit.getDecimalPrecision(),
                variant.getCostPrice(),
                variant.getSellingPrice(),
                product.getStatus(),
                variant.isActive(),
                activeDefault,
                sellable
        );
    }
}
