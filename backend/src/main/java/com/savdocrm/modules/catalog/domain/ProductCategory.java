package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.CategoryConflictException;
import com.savdocrm.modules.catalog.domain.exception.CategoryHierarchyCycleException;
import com.savdocrm.modules.catalog.domain.exception.MaxCategoryDepthExceededException;
import com.savdocrm.shared.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "product_categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_product_categories_tenant_id", columnNames = {"tenant_id", "id"}),
                @UniqueConstraint(name = "uq_product_categories_tenant_code", columnNames = {"tenant_id", "code"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategory extends BaseTenantEntity {

    public static final int MAX_DEPTH = 4; // 5 hierarchy levels: 0..4
    public static final int MAX_NAME_LENGTH = 120;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "depth", nullable = false)
    @Builder.Default
    private int depth = 0;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    public static ProductCategory create(
            UUID tenantId,
            UUID parentId,
            int depth,
            String rawCode,
            String rawName,
            String description,
            int sortOrder
    ) {
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null.");
        validateDepth(depth);

        ProductCategory category = ProductCategory.builder()
                .parentId(parentId)
                .code(CatalogValidation.normalizeCategoryCode(rawCode))
                .name(CatalogValidation.normalizeRequiredText(rawName, "Category name", MAX_NAME_LENGTH))
                .description(description != null && !description.trim().isEmpty() ? description.trim() : null)
                .depth(depth)
                .sortOrder(sortOrder)
                .isActive(true)
                .build();
        category.setTenantId(tenantId);
        return category;
    }

    public void updateDetails(String rawName, String description, int sortOrder) {
        this.name = CatalogValidation.normalizeRequiredText(rawName, "Category name", MAX_NAME_LENGTH);
        this.description = description != null && !description.trim().isEmpty() ? description.trim() : null;
        this.sortOrder = sortOrder;
    }

    public void moveTo(UUID newParentId, int newDepth) {
        if (newParentId != null && Objects.equals(newParentId, this.getId())) {
            throw new CategoryHierarchyCycleException("A category cannot be its own parent.");
        }
        validateDepth(newDepth);
        this.parentId = newParentId;
        this.depth = newDepth;
    }

    public void shiftDepth(int delta) {
        int updatedDepth = this.depth + delta;
        validateDepth(updatedDepth);
        this.depth = updatedDepth;
    }

    public void deactivate(boolean hasActiveChildren) {
        if (hasActiveChildren) {
            throw new CategoryConflictException(
                    "Cannot deactivate category '" + this.name + "' while it has active child categories."
            );
        }
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public static void validateDepth(int depth) {
        if (depth < 0 || depth > MAX_DEPTH) {
            throw new MaxCategoryDepthExceededException(
                    "Category depth " + depth + " exceeds allowed hierarchy bounds (0.." + MAX_DEPTH + ")."
            );
        }
    }
}
