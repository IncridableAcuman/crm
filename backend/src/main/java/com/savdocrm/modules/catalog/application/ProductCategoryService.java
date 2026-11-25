package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.CategoryDto;
import com.savdocrm.modules.catalog.application.dto.CreateCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.MoveCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.UpdateCategoryCommand;
import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.ProductCategory;
import com.savdocrm.modules.catalog.domain.exception.CategoryConflictException;
import com.savdocrm.modules.catalog.domain.exception.CategoryHierarchyCycleException;
import com.savdocrm.modules.catalog.domain.exception.CategoryNotFoundException;
import com.savdocrm.modules.catalog.domain.exception.DuplicateCategoryException;
import com.savdocrm.modules.catalog.domain.exception.InvalidCategoryOperationException;
import com.savdocrm.modules.catalog.domain.exception.MaxCategoryDepthExceededException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductCategoryRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public CategoryDto createCategory(CreateCategoryCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "CreateCategoryCommand cannot be null.");

        // Serialize structural category hierarchy writes per tenant
        lockTenant(tenantId);

        String normalizedCode = CatalogValidation.normalizeCategoryCode(command.code());
        if (categoryRepository.existsByTenantIdAndCode(tenantId, normalizedCode)) {
            throw new DuplicateCategoryException("Category code '" + normalizedCode + "' already exists in this tenant.");
        }

        String normalizedName = CatalogValidation.normalizeRequiredText(
                command.name(), "Category name", ProductCategory.MAX_NAME_LENGTH
        );

        int depth = 0;
        UUID parentId = command.parentId();
        if (parentId != null) {
            ProductCategory parent = categoryRepository.findByTenantIdAndId(tenantId, parentId)
                    .orElseThrow(() -> new CategoryNotFoundException(parentId));
            if (!parent.isActive()) {
                throw new InvalidCategoryOperationException("Cannot create category under an inactive parent category.");
            }
            depth = parent.getDepth() + 1;
            ProductCategory.validateDepth(depth);
        }

        assertSiblingNameUnique(tenantId, parentId, normalizedName, null);

        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        ProductCategory category = ProductCategory.create(
                tenantId,
                parentId,
                depth,
                normalizedCode,
                normalizedName,
                command.description(),
                command.sortOrder() != null ? command.sortOrder() : 0
        );
        category.setCreatedBy(actorUserId);
        category.setUpdatedBy(actorUserId);

        try {
            ProductCategory saved = categoryRepository.saveAndFlush(category);
            return CategoryDto.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public CategoryDto updateCategory(UUID categoryId, UpdateCategoryCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "UpdateCategoryCommand cannot be null.");

        ProductCategory category = categoryRepository.findByTenantIdAndIdWithLock(tenantId, categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        String normalizedName = CatalogValidation.normalizeRequiredText(
                command.name(), "Category name", ProductCategory.MAX_NAME_LENGTH
        );
        assertSiblingNameUnique(tenantId, category.getParentId(), normalizedName, categoryId);

        category.updateDetails(
                normalizedName,
                command.description(),
                command.sortOrder() != null ? command.sortOrder() : category.getSortOrder()
        );
        category.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());

        try {
            ProductCategory saved = categoryRepository.saveAndFlush(category);
            return CategoryDto.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    /**
     * Moves a category under a new parent (or to root when newParentId is null).
     * Acquires a tenant-level write lock to serialize concurrent hierarchy moves,
     * walks the bounded ancestor chain (<= 5 hops) for cycle prevention,
     * and traverses only the moved category's subtree level-by-level to validate and update descendant depths.
     */
    @Transactional
    public CategoryDto moveCategory(UUID categoryId, MoveCategoryCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "MoveCategoryCommand cannot be null.");

        // Serialize concurrent category moves within the tenant so A->B and B->A cannot race into a cycle
        lockTenant(tenantId);

        ProductCategory category = categoryRepository.findByTenantIdAndIdWithLock(tenantId, categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        UUID newParentId = command.newParentId();
        if (Objects.equals(category.getParentId(), newParentId)) {
            return CategoryDto.fromEntity(category);
        }
        if (Objects.equals(categoryId, newParentId)) {
            throw new CategoryHierarchyCycleException("A category cannot be its own parent.");
        }

        int newDepth = resolveValidatedDestinationDepth(tenantId, categoryId, newParentId);

        // Verify sibling name uniqueness under destination parent
        assertSiblingNameUnique(tenantId, newParentId, category.getName(), categoryId);

        // Bounded subtree traversal: loads ONLY descendants of the moved category, level-by-level
        List<ProductCategory> descendants = loadSubtreeDescendantsBounded(tenantId, categoryId, newParentId);
        int maxSubtreeRelativeDepth = 0;
        for (ProductCategory descendant : descendants) {
            int offset = descendant.getDepth() - category.getDepth();
            if (offset > maxSubtreeRelativeDepth) {
                maxSubtreeRelativeDepth = offset;
            }
        }

        if (newDepth + maxSubtreeRelativeDepth > ProductCategory.MAX_DEPTH) {
            throw new MaxCategoryDepthExceededException(
                    "Moving category would push descendant depth to " + (newDepth + maxSubtreeRelativeDepth)
                            + ", exceeding maximum depth of " + ProductCategory.MAX_DEPTH + "."
            );
        }

        int depthDelta = newDepth - category.getDepth();
        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();

        category.moveTo(newParentId, newDepth);
        category.setUpdatedBy(actorUserId);

        if (depthDelta != 0 && !descendants.isEmpty()) {
            for (ProductCategory descendant : descendants) {
                descendant.shiftDepth(depthDelta);
                descendant.setUpdatedBy(actorUserId);
            }
        }

        try {
            ProductCategory saved = categoryRepository.saveAndFlush(category);
            if (!descendants.isEmpty()) {
                categoryRepository.saveAllAndFlush(descendants);
            }
            return CategoryDto.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public CategoryDto deactivateCategory(UUID categoryId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        lockTenant(tenantId);

        ProductCategory category = categoryRepository.findByTenantIdAndIdWithLock(tenantId, categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        boolean hasActiveChildren = categoryRepository.existsByTenantIdAndParentIdAndIsActiveTrue(tenantId, categoryId);
        category.deactivate(hasActiveChildren);
        category.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());

        return CategoryDto.fromEntity(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryDto activateCategory(UUID categoryId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        lockTenant(tenantId);

        ProductCategory category = categoryRepository.findByTenantIdAndIdWithLock(tenantId, categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        if (category.getParentId() != null) {
            ProductCategory parent = categoryRepository.findByTenantIdAndId(tenantId, category.getParentId())
                    .orElseThrow(() -> new CategoryNotFoundException(category.getParentId()));
            if (!parent.isActive()) {
                throw new InvalidCategoryOperationException(
                        "Cannot activate category while its parent category is inactive."
                );
            }
        }

        category.activate();
        category.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());
        return CategoryDto.fromEntity(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        lockTenant(tenantId);

        ProductCategory category = categoryRepository.findByTenantIdAndIdWithLock(tenantId, categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        if (categoryRepository.existsByTenantIdAndParentId(tenantId, categoryId)) {
            throw new CategoryConflictException(
                    "Category has child categories; deactivate or move them before deletion."
            );
        }
        if (productRepository.existsByTenantIdAndCategoryId(tenantId, categoryId)) {
            throw new CategoryConflictException(
                    "Category is referenced by products; deactivate it instead of deleting."
            );
        }

        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public CategoryDto getCategory(UUID categoryId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return categoryRepository.findByTenantIdAndId(tenantId, categoryId)
                .map(CategoryDto::fromEntity)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories(boolean includeInactive) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<ProductCategory> categories = includeInactive
                ? categoryRepository.findAllByTenantIdOrderByDepthAscSortOrderAscNameAsc(tenantId)
                : categoryRepository.findAllByTenantIdAndIsActiveTrueOrderByDepthAscSortOrderAscNameAsc(tenantId);
        return categories.stream().map(CategoryDto::fromEntity).toList();
    }

    private int resolveValidatedDestinationDepth(UUID tenantId, UUID movingCategoryId, UUID newParentId) {
        if (newParentId == null) {
            return 0;
        }

        ProductCategory newParent = categoryRepository.findByTenantIdAndId(tenantId, newParentId)
                .orElseThrow(() -> new CategoryNotFoundException(newParentId));

        if (!newParent.isActive()) {
            throw new InvalidCategoryOperationException("Cannot move category under an inactive parent category.");
        }

        // Bounded ancestor walk: at most MAX_DEPTH + 1 hops
        Set<UUID> visited = new HashSet<>();
        UUID cursorId = newParent.getParentId();
        int hops = 0;
        while (cursorId != null) {
            if (Objects.equals(cursorId, movingCategoryId)) {
                throw new CategoryHierarchyCycleException(
                        "Cannot move category under one of its descendants."
                );
            }
            if (!visited.add(cursorId) || ++hops > ProductCategory.MAX_DEPTH + 1) {
                throw new CategoryHierarchyCycleException("Detected invalid or cyclic ancestor chain.");
            }
            UUID currentAncestorId = cursorId;
            ProductCategory ancestor = categoryRepository.findByTenantIdAndId(tenantId, currentAncestorId)
                    .orElseThrow(() -> new CategoryNotFoundException(currentAncestorId));
            cursorId = ancestor.getParentId();
        }

        int newDepth = newParent.getDepth() + 1;
        ProductCategory.validateDepth(newDepth);
        return newDepth;
    }

    private List<ProductCategory> loadSubtreeDescendantsBounded(UUID tenantId, UUID rootId, UUID forbiddenTargetId) {
        List<ProductCategory> descendants = new ArrayList<>();
        List<UUID> currentFrontier = List.of(rootId);
        Set<UUID> seen = new HashSet<>();
        seen.add(rootId);

        int levelGuard = 0;
        while (!currentFrontier.isEmpty()) {
            if (++levelGuard > ProductCategory.MAX_DEPTH + 2) {
                throw new CategoryHierarchyCycleException("Category subtree depth exceeded safety traversal bound.");
            }
            List<ProductCategory> children = categoryRepository.findAllByTenantIdAndParentIdIn(tenantId, currentFrontier);
            if (children.isEmpty()) {
                break;
            }
            List<UUID> nextFrontier = new ArrayList<>(children.size());
            for (ProductCategory child : children) {
                if (forbiddenTargetId != null && Objects.equals(child.getId(), forbiddenTargetId)) {
                    throw new CategoryHierarchyCycleException("Cannot move category under one of its descendants.");
                }
                if (!seen.add(child.getId())) {
                    throw new CategoryHierarchyCycleException("Cycle detected within category descendants.");
                }
                descendants.add(child);
                nextFrontier.add(child.getId());
            }
            currentFrontier = nextFrontier;
        }
        return descendants;
    }

    private void assertSiblingNameUnique(UUID tenantId, UUID parentId, String name, UUID excludedCategoryId) {
        boolean exists;
        if (parentId == null) {
            exists = excludedCategoryId == null
                    ? categoryRepository.existsByTenantIdAndParentIdIsNullAndNameIgnoreCase(tenantId, name)
                    : categoryRepository.existsByTenantIdAndParentIdIsNullAndNameIgnoreCaseAndIdNot(tenantId, name, excludedCategoryId);
        } else {
            exists = excludedCategoryId == null
                    ? categoryRepository.existsByTenantIdAndParentIdAndNameIgnoreCase(tenantId, parentId, name)
                    : categoryRepository.existsByTenantIdAndParentIdAndNameIgnoreCaseAndIdNot(tenantId, parentId, name, excludedCategoryId);
        }
        if (exists) {
            throw new DuplicateCategoryException(
                    "A sibling category named '" + name + "' already exists at this hierarchy level."
            );
        }
    }

    private void lockTenant(UUID tenantId) {
        tenantRepository.findByIdWithLock(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found with ID: " + tenantId));
    }
}
