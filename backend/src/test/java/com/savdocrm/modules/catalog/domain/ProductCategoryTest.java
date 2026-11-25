package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.CategoryConflictException;
import com.savdocrm.modules.catalog.domain.exception.CategoryHierarchyCycleException;
import com.savdocrm.modules.catalog.domain.exception.MaxCategoryDepthExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductCategoryTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("Category normalizes code and validates depth bounds 0..4")
    void category_normalizesCodeAndEnforcesDepthBounds() {
        ProductCategory root = ProductCategory.create(tenantId, null, 0, "  beverages_01 ", " Soft Drinks ", "Cold", 1);
        assertThat(root.getCode()).isEqualTo("BEVERAGES_01");
        assertThat(root.getName()).isEqualTo("Soft Drinks");
        assertThat(root.getDepth()).isZero();

        assertThatThrownBy(() -> ProductCategory.create(tenantId, UUID.randomUUID(), 5, "TOO_DEEP", "Deep", null, 0))
                .isInstanceOf(MaxCategoryDepthExceededException.class);

        assertThatThrownBy(() -> ProductCategory.create(tenantId, null, -1, "NEG", "Neg", null, 0))
                .isInstanceOf(MaxCategoryDepthExceededException.class);
    }

    @Test
    @DisplayName("Category rejects invalid codes and blank names")
    void category_rejectsInvalidCodesAndBlankNames() {
        assertThatThrownBy(() -> ProductCategory.create(tenantId, null, 0, "A", "Valid", null, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ProductCategory.create(tenantId, null, 0, "BAD CODE!", "Valid", null, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ProductCategory.create(tenantId, null, 0, "VALID_CODE", "   ", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Category prevents self-parenting, enforces shiftDepth bounds, and guards active children on deactivate")
    void category_selfParentShiftAndDeactivateGuards() {
        ProductCategory category = ProductCategory.create(tenantId, null, 2, "CAT_2", "Cat 2", null, 0);
        UUID id = UUID.randomUUID();
        category.setId(id);

        assertThatThrownBy(() -> category.moveTo(id, 3))
                .isInstanceOf(CategoryHierarchyCycleException.class);

        assertThatThrownBy(() -> category.shiftDepth(3))
                .isInstanceOf(MaxCategoryDepthExceededException.class);

        category.shiftDepth(2);
        assertThat(category.getDepth()).isEqualTo(4);

        assertThatThrownBy(() -> category.deactivate(true))
                .isInstanceOf(CategoryConflictException.class);

        category.deactivate(false);
        assertThat(category.isActive()).isFalse();
    }
}
