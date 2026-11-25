package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.InvalidDefaultVariantTransitionException;
import com.savdocrm.modules.catalog.domain.exception.InvalidVariantConfigurationException;
import com.savdocrm.shared.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_product_variants_tenant_id", columnNames = {"tenant_id", "id"}),
                @UniqueConstraint(name = "uq_product_variants_tenant_sku", columnNames = {"tenant_id", "sku"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant extends BaseTenantEntity {

    public static final int MAX_NAME_LENGTH = 220;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "name", nullable = false, length = 220)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_values", nullable = false)
    @Builder.Default
    private Map<String, String> optionValues = new LinkedHashMap<>();

    @Column(name = "cost_price", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO.setScale(CatalogValidation.MONEY_SCALE);

    @Column(name = "selling_price", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal sellingPrice = BigDecimal.ZERO.setScale(CatalogValidation.MONEY_SCALE);

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    @Setter(AccessLevel.PACKAGE)
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    @Setter(AccessLevel.PACKAGE)
    private boolean isActive = true;

    public static ProductVariant create(
            UUID tenantId,
            Product product,
            String rawSku,
            String rawName,
            Map<String, String> rawOptionValues,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            boolean isDefault
    ) {
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null.");
        Objects.requireNonNull(product, "Product cannot be null.");
        if (product.getId() == null) {
            throw new InvalidVariantConfigurationException("Product must be persisted before creating variants.");
        }

        Map<String, String> normalizedOptions = product.validateAndNormalizeOptionValues(rawOptionValues);
        String resolvedName = normalizeVariantName(rawName);

        ProductVariant variant = ProductVariant.builder()
                .productId(product.getId())
                .sku(CatalogValidation.normalizeSku(rawSku))
                .name(resolvedName)
                .optionValues(new LinkedHashMap<>(normalizedOptions))
                .costPrice(CatalogValidation.normalizeMoney(costPrice != null ? costPrice : BigDecimal.ZERO, "Cost price"))
                .sellingPrice(CatalogValidation.normalizeMoney(sellingPrice != null ? sellingPrice : BigDecimal.ZERO, "Selling price"))
                .isDefault(isDefault)
                .isActive(true)
                .build();
        variant.setTenantId(tenantId);
        return variant;
    }

    public void update(
            Product product,
            String rawSku,
            String rawName,
            Map<String, String> rawOptionValues,
            BigDecimal costPrice,
            BigDecimal sellingPrice
    ) {
        Objects.requireNonNull(product, "Product cannot be null.");
        Map<String, String> normalizedOptions = product.validateAndNormalizeOptionValues(rawOptionValues);

        this.sku = CatalogValidation.normalizeSku(rawSku);
        this.name = normalizeVariantName(rawName);
        this.optionValues = new LinkedHashMap<>(normalizedOptions);
        this.costPrice = CatalogValidation.normalizeMoney(costPrice != null ? costPrice : BigDecimal.ZERO, "Cost price");
        this.sellingPrice = CatalogValidation.normalizeMoney(sellingPrice != null ? sellingPrice : BigDecimal.ZERO, "Selling price");
    }

    public void updateOptionValues(Product product, Map<String, String> rawOptionValues) {
        Objects.requireNonNull(product, "Product cannot be null.");
        this.optionValues = new LinkedHashMap<>(product.validateAndNormalizeOptionValues(rawOptionValues));
    }

    public void markAsDefault() {
        if (!this.isActive) {
            throw new InvalidDefaultVariantTransitionException("Inactive variants cannot be selected as the default variant.");
        }
        this.isDefault = true;
    }

    public void unmarkAsDefault() {
        this.isDefault = false;
    }

    public void deactivate(long activeVariantCount) {
        if (this.isDefault) {
            throw new InvalidDefaultVariantTransitionException(
                    "Cannot deactivate the current default variant. Assign another active variant as default first."
            );
        }
        if (activeVariantCount <= 1) {
            throw new InvalidDefaultVariantTransitionException(
                    "Cannot deactivate the sole remaining active variant of a product."
            );
        }
        this.isActive = false;
    }

    public void deactivateForProductArchive() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    private static String normalizeVariantName(String rawName) {
        if (rawName == null || rawName.trim().isBlank()) {
            throw new InvalidVariantConfigurationException("Variant name cannot be blank.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidVariantConfigurationException(
                    "Variant name cannot exceed " + MAX_NAME_LENGTH + " characters."
            );
        }
        return trimmed;
    }
}
