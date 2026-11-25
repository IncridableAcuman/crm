package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.CreateProductCommand;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.application.dto.ProductVariantDto;
import com.savdocrm.modules.catalog.application.dto.UpdateProductCommand;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductCategory;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.UnitOfMeasure;
import com.savdocrm.modules.catalog.domain.exception.CategoryNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.InactiveUnitOfMeasureException;
import com.savdocrm.modules.catalog.domain.exception.InvalidCategoryOperationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidDefaultVariantTransitionException;
import com.savdocrm.modules.catalog.domain.exception.InvalidProductConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.ProductNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.UnitOfMeasureNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductCategoryRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final CatalogVariantHelper variantHelper;

    @Transactional
    public ProductDto createProduct(CreateProductCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "CreateProductCommand cannot be null.");

        validateActiveUnit(tenantId, command.unitId());
        if (command.categoryId() != null) {
            validateActiveCategory(tenantId, command.categoryId());
        }

        List<CreateVariantCommand> variantCommands = command.variants();
        if (variantCommands == null || variantCommands.isEmpty()) {
            throw new InvalidProductConfigurationException("Every product must have at least one variant.");
        }
        if (command.productType() == ProductType.SIMPLE && variantCommands.size() != 1) {
            throw new InvalidProductConfigurationException("SIMPLE products must have exactly one default variant.");
        }

        int explicitDefaults = (int) variantCommands.stream().filter(CreateVariantCommand::isDefault).count();
        if (explicitDefaults > 1) {
            throw new InvalidDefaultVariantTransitionException("A product cannot have multiple default variants.");
        }

        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        Product product = Product.create(
                tenantId,
                command.categoryId(),
                command.unitId(),
                command.name(),
                command.description(),
                command.brand(),
                command.productType(),
                command.variantOptions(),
                command.status()
        );
        product.setCreatedBy(actorUserId);
        product.setUpdatedBy(actorUserId);
        Product savedProduct = productRepository.saveAndFlush(product);

        List<ProductVariant> persistedVariants = new ArrayList<>(variantCommands.size());
        List<ProductVariantDto> variantDtos = new ArrayList<>(variantCommands.size());

        for (int i = 0; i < variantCommands.size(); i++) {
            CreateVariantCommand variantCmd = variantCommands.get(i);
            boolean isDefault = command.productType() == ProductType.SIMPLE
                    || variantCmd.isDefault()
                    || (explicitDefaults == 0 && i == 0);

            ProductVariantDto variantDto = variantHelper.createAndPersistVariantWithBarcodes(
                    tenantId, savedProduct, variantCmd, isDefault, persistedVariants, actorUserId
            );
            variantDtos.add(variantDto);

            // Track option values in memory to catch duplicate option combinations inside the creation batch
            ProductVariant marker = ProductVariant.builder().optionValues(variantDto.optionValues()).build();
            persistedVariants.add(marker);
        }

        return ProductDto.fromEntity(savedProduct, variantDtos);
    }

    @Transactional
    public ProductDto updateProduct(UUID productId, UpdateProductCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "UpdateProductCommand cannot be null.");

        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (!Objects.equals(product.getUnitId(), command.unitId())) {
            validateActiveUnit(tenantId, command.unitId());
        } else {
            // Even when retaining historical unit, confirm unit belongs to this tenant
            if (!unitOfMeasureRepository.existsByTenantIdAndId(tenantId, command.unitId())) {
                throw new UnitOfMeasureNotFoundException(command.unitId());
            }
        }

        if (command.categoryId() != null && !Objects.equals(product.getCategoryId(), command.categoryId())) {
            validateActiveCategory(tenantId, command.categoryId());
        } else if (command.categoryId() != null && !categoryRepository.existsByTenantIdAndId(tenantId, command.categoryId())) {
            throw new CategoryNotFoundException(command.categoryId());
        }

        product.updateMetadata(
                command.categoryId(),
                command.unitId(),
                command.name(),
                command.description(),
                command.brand()
        );
        product.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());
        Product saved = productRepository.saveAndFlush(product);

        return assembleProductDto(tenantId, saved);
    }

    @Transactional
    public ProductDto changeProductStatus(UUID productId, ProductStatus targetStatus) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (targetStatus == ProductStatus.ARCHIVED) {
            return archiveLockedProduct(tenantId, product);
        }

        boolean hasActiveVariant = variantRepository.existsByTenantIdAndProductIdAndIsActiveTrue(tenantId, productId);
        product.transitionStatus(targetStatus, hasActiveVariant);
        product.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());
        Product saved = productRepository.saveAndFlush(product);

        return assembleProductDto(tenantId, saved);
    }

    @Transactional
    public ProductDto archiveProduct(UUID productId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return archiveLockedProduct(tenantId, product);
    }

    @Transactional
    public ProductDto convertSimpleToVariable(
            UUID productId,
            List<String> newVariantOptions,
            Map<String, String> existingDefaultOptionValues
    ) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, productId);
        if (variants.isEmpty()) {
            throw new InvalidProductConfigurationException("Product has no default variant.");
        }

        product.convertToVariable(newVariantOptions);
        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        product.setUpdatedBy(actorUserId);

        ProductVariant defaultVariant = variants.get(0);
        defaultVariant.updateOptionValues(product, existingDefaultOptionValues);
        defaultVariant.setUpdatedBy(actorUserId);

        Product saved = productRepository.saveAndFlush(product);
        variantRepository.saveAndFlush(defaultVariant);
        return assembleProductDto(tenantId, saved);
    }

    @Transactional
    public void deleteDraftProduct(UUID productId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Product product = productRepository.findByTenantIdAndIdWithLock(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (product.getStatus() != ProductStatus.DRAFT) {
            throw new InvalidProductConfigurationException(
                    "Only DRAFT products can be physically deleted; archive active products instead."
            );
        }

        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, productId);
        for (ProductVariant variant : variants) {
            barcodeRepository.deleteAllByTenantIdAndVariantId(tenantId, variant.getId());
        }
        barcodeRepository.flush();

        if (!variants.isEmpty()) {
            variantRepository.deleteAll(variants);
            variantRepository.flush();
        }

        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public ProductDto getProduct(UUID productId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return assembleProductDto(tenantId, product);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> listActiveProducts() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return productRepository.findAllByTenantIdAndStatus(tenantId, ProductStatus.ACTIVE).stream()
                .map(p -> assembleProductDto(tenantId, p))
                .toList();
    }

    private ProductDto archiveLockedProduct(UUID tenantId, Product product) {
        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        product.transitionStatus(ProductStatus.ARCHIVED, false);
        product.setUpdatedBy(actorUserId);

        List<ProductVariant> variants = variantRepository.findAllByTenantIdAndProductIdWithLock(tenantId, product.getId());
        for (ProductVariant variant : variants) {
            variant.deactivateForProductArchive();
            variant.setUpdatedBy(actorUserId);
        }

        Product saved = productRepository.saveAndFlush(product);
        if (!variants.isEmpty()) {
            variantRepository.saveAllAndFlush(variants);
        }
        return assembleProductDto(tenantId, saved);
    }

    private ProductDto assembleProductDto(UUID tenantId, Product product) {
        List<ProductVariant> variants = variantRepository
                .findAllByTenantIdAndProductIdOrderByIsDefaultDescCreatedAtAsc(tenantId, product.getId());
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, List<ProductBarcodeDto>> barcodesByVariant = variantHelper.loadBarcodesByVariantIds(tenantId, variantIds);

        List<ProductVariantDto> variantDtos = variants.stream()
                .map(v -> ProductVariantDto.fromEntity(v, barcodesByVariant.getOrDefault(v.getId(), List.of())))
                .toList();
        return ProductDto.fromEntity(product, variantDtos);
    }

    private void validateActiveUnit(UUID tenantId, UUID unitId) {
        if (unitId == null) {
            throw new InvalidProductConfigurationException("Unit of measure ID cannot be null.");
        }
        UnitOfMeasure unit = unitOfMeasureRepository.findByTenantIdAndId(tenantId, unitId)
                .orElseThrow(() -> new UnitOfMeasureNotFoundException(unitId));
        if (!unit.isActive()) {
            throw new InactiveUnitOfMeasureException(
                    "Inactive unit of measure '" + unit.getCode() + "' cannot be assigned to a product."
            );
        }
    }

    private void validateActiveCategory(UUID tenantId, UUID categoryId) {
        ProductCategory category = categoryRepository.findByTenantIdAndId(tenantId, categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (!category.isActive()) {
            throw new InvalidCategoryOperationException(
                    "Inactive category '" + category.getName() + "' cannot be assigned to a product."
            );
        }
    }
}
