package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.CategoryDto;
import com.savdocrm.modules.catalog.application.dto.CreateCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.MoveCategoryCommand;
import com.savdocrm.modules.catalog.domain.ProductCategory;
import com.savdocrm.modules.catalog.domain.exception.CategoryConflictException;
import com.savdocrm.modules.catalog.domain.exception.CategoryHierarchyCycleException;
import com.savdocrm.modules.catalog.domain.exception.CategoryNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateCategoryException;
import com.savdocrm.modules.catalog.domain.exception.InvalidCategoryOperationException;
import com.savdocrm.modules.catalog.domain.exception.MaxCategoryDepthExceededException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductCategoryRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.multitenancy.TenantContextMissingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest {

    @Mock
    private ProductCategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private ProductCategoryService categoryService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        Tenant tenant = Tenant.builder().name("Store").code("STORE").status(TenantStatus.ACTIVE).build();
        tenant.setId(tenantId);
        lenient().when(tenantRepository.findByIdWithLock(tenantId)).thenReturn(Optional.of(tenant));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Throws TenantContextMissingException when tenant context is absent")
    void failsClosedWhenTenantContextMissing() {
        TenantContextHolder.clear();
        assertThatThrownBy(() -> categoryService.listCategories(false))
                .isInstanceOf(TenantContextMissingException.class);
    }

    @Test
    @DisplayName("Rejects duplicate category code and duplicate sibling name")
    void createCategory_rejectsDuplicateCodeAndSiblingName() {
        when(categoryRepository.existsByTenantIdAndCode(tenantId, "BEV")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(
                new CreateCategoryCommand(null, "bev", "Beverages", null, 0)
        )).isInstanceOf(DuplicateCategoryException.class);

        when(categoryRepository.existsByTenantIdAndCode(tenantId, "BEV2")).thenReturn(false);
        when(categoryRepository.existsByTenantIdAndParentIdIsNullAndNameIgnoreCase(tenantId, "Beverages")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(
                new CreateCategoryCommand(null, "BEV2", "Beverages", null, 0)
        )).isInstanceOf(DuplicateCategoryException.class);
    }

    @Test
    @DisplayName("Move category detects multi-hop cycle A -> B -> C -> A without loading entire tree")
    void moveCategory_detectsMultiHopCycle() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();

        ProductCategory catA = category(aId, null, "CAT_A", "Cat A", 0, true);
        ProductCategory catB = category(bId, aId, "CAT_B", "Cat B", 1, true);
        ProductCategory catC = category(cId, bId, "CAT_C", "Cat C", 2, true);

        when(categoryRepository.findByTenantIdAndIdWithLock(tenantId, aId)).thenReturn(Optional.of(catA));
        when(categoryRepository.findByTenantIdAndId(tenantId, cId)).thenReturn(Optional.of(catC));
        when(categoryRepository.findByTenantIdAndId(tenantId, bId)).thenReturn(Optional.of(catB));

        assertThatThrownBy(() -> categoryService.moveCategory(aId, new MoveCategoryCommand(cId)))
                .isInstanceOf(CategoryHierarchyCycleException.class);
    }

    @Test
    @DisplayName("Move category validates descendant depth overflow and recalculates subtree depths atomically")
    void moveCategory_recalculatesDescendantDepthsAndRejectsOverflow() {
        UUID catId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID destParentId = UUID.randomUUID();

        ProductCategory moving = category(catId, null, "MOV", "Moving", 0, true);
        ProductCategory child = category(childId, catId, "CHD", "Child", 1, true);
        ProductCategory destDepth3 = category(destParentId, UUID.randomUUID(), "D3", "Depth 3", 3, true);
        ProductCategory destDepth2 = category(destDepth3.getParentId(), null, "D2", "Depth 2", 2, true);

        when(categoryRepository.findByTenantIdAndIdWithLock(tenantId, catId)).thenReturn(Optional.of(moving));
        when(categoryRepository.findByTenantIdAndId(tenantId, destParentId)).thenReturn(Optional.of(destDepth3));
        when(categoryRepository.findByTenantIdAndId(tenantId, destDepth3.getParentId())).thenReturn(Optional.of(destDepth2));
        when(categoryRepository.findAllByTenantIdAndParentIdIn(tenantId, List.of(catId))).thenReturn(List.of(child));
        when(categoryRepository.findAllByTenantIdAndParentIdIn(tenantId, List.of(childId))).thenReturn(List.of());

        // Moving depth-0 node (with depth-1 child) under depth-3 parent would put child at depth 5 (> 4)
        assertThatThrownBy(() -> categoryService.moveCategory(catId, new MoveCategoryCommand(destParentId)))
                .isInstanceOf(MaxCategoryDepthExceededException.class);

        // Moving under depth-2 parent succeeds: moving -> depth 3, child -> depth 4
        when(categoryRepository.saveAndFlush(any(ProductCategory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CategoryDto moved = categoryService.moveCategory(catId, new MoveCategoryCommand(destDepth2.getId()));
        assertThat(moved.depth()).isEqualTo(3);
        assertThat(child.getDepth()).isEqualTo(4);
        verify(categoryRepository).saveAllAndFlush(List.of(child));
    }

    @Test
    @DisplayName("Cross-tenant parent or missing category fails closed with CategoryNotFoundException")
    void crossTenantCategoryFailsClosed() {
        UUID foreignId = UUID.randomUUID();
        when(categoryRepository.findByTenantIdAndId(tenantId, foreignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(foreignId))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("Deactivate and delete enforce child and product reference guards")
    void deactivateAndDeleteEnforceGuards() {
        UUID catId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        ProductCategory cat = category(catId, parentId, "CAT", "Cat", 1, true);
        ProductCategory inactiveParent = category(parentId, null, "PAR", "Parent", 0, false);

        when(categoryRepository.findByTenantIdAndIdWithLock(tenantId, catId)).thenReturn(Optional.of(cat));
        when(categoryRepository.existsByTenantIdAndParentIdAndIsActiveTrue(tenantId, catId)).thenReturn(true);
        assertThatThrownBy(() -> categoryService.deactivateCategory(catId))
                .isInstanceOf(CategoryConflictException.class);

        cat.setActive(false);
        when(categoryRepository.findByTenantIdAndId(tenantId, parentId)).thenReturn(Optional.of(inactiveParent));
        assertThatThrownBy(() -> categoryService.activateCategory(catId))
                .isInstanceOf(InvalidCategoryOperationException.class);

        when(categoryRepository.existsByTenantIdAndParentId(tenantId, catId)).thenReturn(false);
        when(productRepository.existsByTenantIdAndCategoryId(tenantId, catId)).thenReturn(true);
        assertThatThrownBy(() -> categoryService.deleteCategory(catId))
                .isInstanceOf(CategoryConflictException.class);
    }

    private ProductCategory category(UUID id, UUID parentId, String code, String name, int depth, boolean active) {
        ProductCategory c = ProductCategory.create(tenantId, parentId, depth, code, name, null, 0);
        c.setId(id);
        c.setActive(active);
        return c;
    }
}
