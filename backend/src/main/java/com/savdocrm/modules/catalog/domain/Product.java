package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.InvalidProductConfigurationException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;
import com.savdocrm.shared.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_products_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseTenantEntity {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_BRAND_LENGTH = 100;
    public static final int MAX_VARIANT_OPTIONS = 3;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "brand", length = 100)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    @Setter(AccessLevel.PACKAGE)
    private ProductType productType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_options", nullable = false)
    @Builder.Default
    @Setter(AccessLevel.PACKAGE)
    private List<String> variantOptions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    @Setter(AccessLevel.PACKAGE)
    private ProductStatus status = ProductStatus.ACTIVE;

    public static Product create(
            UUID tenantId,
            UUID categoryId,
            UUID unitId,
            String rawName,
            String description,
            String brand,
            ProductType productType,
            List<String> rawVariantOptions,
            ProductStatus initialStatus
    ) {
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null.");
        if (unitId == null) {
            throw new InvalidProductConfigurationException("Unit of measure ID cannot be null.");
        }
        if (productType == null) {
            throw new InvalidProductConfigurationException("Product type cannot be null.");
        }
        ProductStatus resolvedStatus = initialStatus != null ? initialStatus : ProductStatus.ACTIVE;
        if (resolvedStatus == ProductStatus.ARCHIVED) {
            throw new InvalidProductConfigurationException("A new product cannot be created directly in ARCHIVED status.");
        }

        List<String> normalizedOptions = normalizeAndValidateVariantOptions(productType, rawVariantOptions);

        Product product = Product.builder()
                .categoryId(categoryId)
                .unitId(unitId)
                .name(normalizeProductName(rawName))
                .description(description != null && !description.trim().isEmpty() ? description.trim() : null)
                .brand(CatalogValidation.normalizeOptionalText(brand, "Brand", MAX_BRAND_LENGTH))
                .productType(productType)
                .variantOptions(new ArrayList<>(normalizedOptions))
                .status(resolvedStatus)
                .build();
        product.setTenantId(tenantId);
        return product;
    }

    public void updateMetadata(
            UUID categoryId,
            UUID unitId,
            String rawName,
            String description,
            String brand
    ) {
        if (unitId == null) {
            throw new InvalidProductConfigurationException("Unit of measure ID cannot be null.");
        }
        this.categoryId = categoryId;
        this.unitId = unitId;
        this.name = normalizeProductName(rawName);
        this.description = description != null && !description.trim().isEmpty() ? description.trim() : null;
        this.brand = CatalogValidation.normalizeOptionalText(brand, "Brand", MAX_BRAND_LENGTH);
    }

    public void convertToVariable(List<String> rawVariantOptions) {
        if (this.productType == ProductType.VARIABLE) {
            throw new InvalidProductConfigurationException("Product is already of type VARIABLE.");
        }
        List<String> normalized = normalizeAndValidateVariantOptions(ProductType.VARIABLE, rawVariantOptions);
        this.productType = ProductType.VARIABLE;
        this.variantOptions = new ArrayList<>(normalized);
    }

    public void transitionStatus(ProductStatus targetStatus, boolean hasActiveVariant) {
        if (targetStatus == null) {
            throw new InvalidProductConfigurationException("Target product status cannot be null.");
        }
        if (this.status == targetStatus) {
            if (targetStatus == ProductStatus.ACTIVE && !hasActiveVariant) {
                throw new InvalidProductConfigurationException("An ACTIVE product must have at least one active variant.");
            }
            return;
        }

        switch (this.status) {
            case DRAFT -> {
                if (targetStatus == ProductStatus.ACTIVE && !hasActiveVariant) {
                    throw new InvalidProductConfigurationException("Cannot activate product without at least one active variant.");
                }
                this.status = targetStatus;
            }
            case ACTIVE -> {
                if (targetStatus == ProductStatus.DRAFT) {
                    throw new InvalidProductConfigurationException("Cannot revert an ACTIVE product back to DRAFT.");
                }
                this.status = targetStatus;
            }
            case ARCHIVED -> {
                if (targetStatus == ProductStatus.DRAFT) {
                    throw new InvalidProductConfigurationException("Cannot revert an ARCHIVED product back to DRAFT.");
                }
                if (targetStatus == ProductStatus.ACTIVE && !hasActiveVariant) {
                    throw new InvalidProductConfigurationException("Cannot reactivate an ARCHIVED product without at least one active variant.");
                }
                this.status = targetStatus;
            }
        }
    }

    public Map<String, String> validateAndNormalizeOptionValues(Map<String, String> rawOptionValues) {
        if (this.productType == ProductType.SIMPLE) {
            if (rawOptionValues != null && !rawOptionValues.isEmpty()) {
                throw new InvalidVariantConfigurationException(
                        "SIMPLE products must have empty variant option values."
                );
            }
            return Collections.emptyMap();
        }

        if (rawOptionValues == null || rawOptionValues.isEmpty()) {
            throw new InvalidVariantConfigurationException(
                    "VARIABLE product variants must supply option values for: " + this.variantOptions
            );
        }

        List<String> declaredOptions = this.variantOptions == null ? List.of() : this.variantOptions;
        if (rawOptionValues.size() != declaredOptions.size()) {
            throw new InvalidVariantConfigurationException(
                    "Variant option values must match declared options " + declaredOptions + "."
            );
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (String optionKey : declaredOptions) {
            if (!rawOptionValues.containsKey(optionKey)) {
                throw new InvalidVariantConfigurationException(
                        "Missing required variant option '" + optionKey + "'."
                );
            }
            String value = rawOptionValues.get(optionKey);
            if (value == null || value.trim().isBlank()) {
                throw new InvalidVariantConfigurationException(
                        "Variant option '" + optionKey + "' cannot have a blank value."
                );
            }
            normalized.put(optionKey, value.trim());
        }
        return normalized;
    }

    public boolean isActive() {
        return this.status == ProductStatus.ACTIVE;
    }

    public boolean isArchived() {
        return this.status == ProductStatus.ARCHIVED;
    }

    private static String normalizeProductName(String rawName) {
        if (rawName == null || rawName.trim().isBlank()) {
            throw new InvalidProductConfigurationException("Product name cannot be blank.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidProductConfigurationException(
                    "Product name cannot exceed " + MAX_NAME_LENGTH + " characters."
            );
        }
        return trimmed;
    }

    public static List<String> normalizeAndValidateVariantOptions(ProductType type, List<String> rawOptions) {
        List<String> options = rawOptions == null ? List.of() : rawOptions;
        if (type == ProductType.SIMPLE) {
            if (!options.isEmpty()) {
                throw new InvalidProductConfigurationException(
                        "SIMPLE products cannot define variant options."
                );
            }
            return List.of();
        }

        if (options.isEmpty()) {
            throw new InvalidProductConfigurationException(
                    "VARIABLE products must define between 1 and " + MAX_VARIANT_OPTIONS + " variant options."
            );
        }
        if (options.size() > MAX_VARIANT_OPTIONS) {
            throw new InvalidProductConfigurationException(
                    "VARIABLE products cannot define more than " + MAX_VARIANT_OPTIONS + " variant options."
            );
        }

        List<String> normalized = new ArrayList<>(options.size());
        Set<String> seenLower = new HashSet<>();
        for (String opt : options) {
            if (opt == null || opt.trim().isBlank()) {
                throw new InvalidProductConfigurationException("Variant option name cannot be blank.");
            }
            String trimmed = opt.trim();
            if (!seenLower.add(trimmed.toLowerCase(Locale.ROOT))) {
                throw new InvalidProductConfigurationException("Duplicate variant option name: '" + trimmed + "'.");
            }
            normalized.add(trimmed);
        }
        return Collections.unmodifiableList(normalized);
    }
}
