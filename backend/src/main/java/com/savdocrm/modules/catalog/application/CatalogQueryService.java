package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.CategoryDto;
import com.savdocrm.modules.catalog.application.dto.CategoryTreeDto;
import com.savdocrm.modules.catalog.application.dto.ProductDetailDto;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.application.dto.ProductSearchCriteria;
import com.savdocrm.modules.catalog.application.dto.ProductSummaryDto;
import com.savdocrm.modules.catalog.application.dto.UnitOfMeasureDto;
import com.savdocrm.modules.catalog.application.facade.ProductQueryFacade;
import com.savdocrm.modules.catalog.application.facade.VariantCatalogDto;
import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.modules.catalog.domain.ProductCategory;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductVariant;
import com.savdocrm.modules.catalog.domain.UnitOfMeasure;
import com.savdocrm.modules.catalog.domain.exception.ProductVariantNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductBarcodeRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductCategoryRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductVariantRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only query service powering Catalog REST endpoints (paginated search,
 * single-query category tree assembly, enriched product details, and POS lookup).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> ALLOWED_SORT_FIELDS = Map.of(
            "name", "name",
            "createdat", "createdAt",
            "updatedat", "updatedAt",
            "status", "status",
            "brand", "brand",
            "producttype", "productType"
    );

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitOfMeasureRepository unitRepository;
    private final ProductService productService;
    private final ProductQueryFacade productQueryFacade;

    public Page<ProductSummaryDto> searchProducts(
            ProductSearchCriteria criteria,
            int page,
            int size,
            String sortParam
    ) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (page < 0) {
            throw new IllegalArgumentException("Page index must be greater than or equal to 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        Sort sort = parseSafeSort(sortParam);
        Pageable pageable = PageRequest.of(page, size, sort);

        ProductSearchCriteria effective = criteria != null
                ? criteria
                : new ProductSearchCriteria(null, null, null, null, false, null, null, null, null, null, false);

        Set<UUID> resolvedCategoryIds = resolveCategoryFilterIds(
                tenantId, effective.categoryId(), effective.includeSubcategories()
        );
        if (effective.categoryId() != null && effective.includeSubcategories() && resolvedCategoryIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<Product> spec = buildSpecification(tenantId, effective, resolvedCategoryIds);
        Page<Product> productPage = productRepository.findAll(spec, pageable);
        if (productPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<ProductSummaryDto> summaries = assembleSummaries(tenantId, productPage.getContent());
        return new PageImpl<>(summaries, pageable, productPage.getTotalElements());
    }

    public ProductDetailDto getProductDetail(UUID productId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        ProductDto product = productService.getProduct(productId);
        CategoryDto category = product.categoryId() != null
                ? categoryRepository.findByTenantIdAndId(tenantId, product.categoryId())
                        .map(CategoryDto::fromEntity)
                        .orElse(null)
                : null;
        UnitOfMeasureDto unit = unitRepository.findByTenantIdAndId(tenantId, product.unitId())
                .map(UnitOfMeasureDto::fromEntity)
                .orElse(null);
        return new ProductDetailDto(product, category, unit);
    }

    public List<CategoryTreeDto> getCategoryTree(boolean includeInactive) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<ProductCategory> flat = includeInactive
                ? categoryRepository.findAllByTenantIdOrderByDepthAscSortOrderAscNameAsc(tenantId)
                : categoryRepository.findAllByTenantIdAndIsActiveTrueOrderByDepthAscSortOrderAscNameAsc(tenantId);

        Map<UUID, List<ProductCategory>> byParent = new LinkedHashMap<>();
        List<ProductCategory> roots = new ArrayList<>();
        Set<UUID> loadedIds = flat.stream().map(ProductCategory::getId).collect(Collectors.toSet());

        for (ProductCategory cat : flat) {
            if (cat.getParentId() == null || !loadedIds.contains(cat.getParentId())) {
                roots.add(cat);
            } else {
                byParent.computeIfAbsent(cat.getParentId(), k -> new ArrayList<>()).add(cat);
            }
        }

        return roots.stream()
                .map(root -> buildTreeNode(root, byParent))
                .toList();
    }

    public VariantCatalogDto lookupActiveVariant(String rawCode) {
        if (rawCode == null || rawCode.trim().isBlank()) {
            throw new IllegalArgumentException("Lookup code cannot be blank.");
        }
        String trimmed = rawCode.trim();
        return productQueryFacade.findActiveVariantByBarcode(trimmed)
                .or(() -> {
                    String upper = trimmed.toUpperCase(Locale.ROOT);
                    if (!CatalogValidation.SKU_PATTERN.matcher(upper).matches()) {
                        return java.util.Optional.empty();
                    }
                    return productQueryFacade.findActiveVariantBySku(upper);
                })
                .orElseThrow(() -> new ProductVariantNotFoundException("No active product variant found for code."));
    }

    private CategoryTreeDto buildTreeNode(ProductCategory node, Map<UUID, List<ProductCategory>> byParent) {
        List<CategoryTreeDto> children = byParent.getOrDefault(node.getId(), List.of()).stream()
                .map(child -> buildTreeNode(child, byParent))
                .toList();
        return new CategoryTreeDto(
                node.getId(),
                node.getParentId(),
                node.getCode(),
                node.getName(),
                node.getDescription(),
                node.getDepth(),
                node.getSortOrder(),
                node.isActive(),
                children,
                node.getCreatedAt(),
                node.getUpdatedAt(),
                node.getVersion()
        );
    }

    private List<ProductSummaryDto> assembleSummaries(UUID tenantId, List<Product> products) {
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Set<UUID> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> unitIds = products.stream()
                .map(Product::getUnitId)
                .collect(Collectors.toSet());

        Map<UUID, ProductCategory> categoriesById = categoryIds.isEmpty()
                ? Map.of()
                : categoryRepository.findAllByTenantIdAndIdIn(tenantId, categoryIds).stream()
                        .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));

        Map<UUID, UnitOfMeasure> unitsById = unitIds.isEmpty()
                ? Map.of()
                : unitRepository.findAllByTenantIdAndIdIn(tenantId, unitIds).stream()
                        .collect(Collectors.toMap(UnitOfMeasure::getId, Function.identity()));

        List<ProductVariant> variants = variantRepository
                .findAllByTenantIdAndProductIdInOrderByIsDefaultDescCreatedAtAsc(tenantId, productIds);
        Map<UUID, List<ProductVariant>> variantsByProduct = variants.stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));

        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, String> primaryBarcodeByVariant = variantIds.isEmpty()
                ? Map.of()
                : barcodeRepository.findAllByTenantIdAndVariantIdIn(tenantId, variantIds).stream()
                        .filter(ProductBarcode::isPrimary)
                        .collect(Collectors.toMap(
                                ProductBarcode::getVariantId,
                                ProductBarcode::getBarcode,
                                (a, b) -> a
                        ));

        List<ProductSummaryDto> result = new ArrayList<>(products.size());
        for (Product product : products) {
            ProductCategory category = product.getCategoryId() != null ? categoriesById.get(product.getCategoryId()) : null;
            UnitOfMeasure unit = unitsById.get(product.getUnitId());
            List<ProductVariant> productVariants = variantsByProduct.getOrDefault(product.getId(), List.of());

            ProductVariant representative = productVariants.stream()
                    .filter(ProductVariant::isDefault)
                    .findFirst()
                    .orElse(productVariants.isEmpty() ? null : productVariants.get(0));

            BigDecimal minPrice = null;
            BigDecimal maxPrice = null;
            for (ProductVariant v : productVariants) {
                BigDecimal price = v.getSellingPrice();
                if (price != null) {
                    minPrice = minPrice == null || price.compareTo(minPrice) < 0 ? price : minPrice;
                    maxPrice = maxPrice == null || price.compareTo(maxPrice) > 0 ? price : maxPrice;
                }
            }

            result.add(new ProductSummaryDto(
                    product.getId(),
                    product.getName(),
                    product.getBrand(),
                    product.getProductType(),
                    product.getStatus(),
                    product.getCategoryId(),
                    category != null ? category.getName() : null,
                    product.getUnitId(),
                    unit != null ? unit.getCode() : null,
                    unit != null ? unit.getSymbol() : null,
                    productVariants.size(),
                    representative != null ? representative.getSku() : null,
                    representative != null ? primaryBarcodeByVariant.get(representative.getId()) : null,
                    minPrice,
                    maxPrice,
                    product.getCreatedAt(),
                    product.getUpdatedAt(),
                    product.getVersion()
            ));
        }
        return result;
    }

    private Set<UUID> resolveCategoryFilterIds(UUID tenantId, UUID categoryId, boolean includeSubcategories) {
        if (categoryId == null || !includeSubcategories) {
            return Set.of();
        }
        if (!categoryRepository.existsByTenantIdAndId(tenantId, categoryId)) {
            return Set.of();
        }
        Set<UUID> allIds = new HashSet<>();
        allIds.add(categoryId);
        List<UUID> frontier = List.of(categoryId);
        int guard = 0;
        while (!frontier.isEmpty() && ++guard <= ProductCategory.MAX_DEPTH + 2) {
            List<ProductCategory> children = categoryRepository.findAllByTenantIdAndParentIdIn(tenantId, frontier);
            if (children.isEmpty()) {
                break;
            }
            List<UUID> next = new ArrayList<>();
            for (ProductCategory child : children) {
                if (allIds.add(child.getId())) {
                    next.add(child.getId());
                }
            }
            frontier = next;
        }
        return allIds;
    }

    private Specification<Product> buildSpecification(
            UUID tenantId,
            ProductSearchCriteria criteria,
            Set<UUID> expandedCategoryIds
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (criteria.search() != null && !criteria.search().trim().isBlank()) {
                String escaped = escapeLike(criteria.search().trim().toLowerCase(Locale.ROOT));
                String likePattern = "%" + escaped + "%";

                Subquery<Integer> skuSub = query.subquery(Integer.class);
                Root<ProductVariant> vRoot = skuSub.from(ProductVariant.class);
                skuSub.select(cb.literal(1)).where(
                        cb.equal(vRoot.get("tenantId"), tenantId),
                        cb.equal(vRoot.get("productId"), root.get("id")),
                        cb.like(cb.lower(vRoot.get("sku")), likePattern, '\\')
                );

                Subquery<Integer> barcodeSub = query.subquery(Integer.class);
                Root<ProductBarcode> bRoot = barcodeSub.from(ProductBarcode.class);
                Root<ProductVariant> bvRoot = barcodeSub.from(ProductVariant.class);
                barcodeSub.select(cb.literal(1)).where(
                        cb.equal(bRoot.get("tenantId"), tenantId),
                        cb.equal(bvRoot.get("tenantId"), tenantId),
                        cb.equal(bRoot.get("variantId"), bvRoot.get("id")),
                        cb.equal(bvRoot.get("productId"), root.get("id")),
                        cb.like(cb.lower(bRoot.get("barcode")), likePattern, '\\')
                );

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likePattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), likePattern, '\\'),
                        cb.exists(skuSub),
                        cb.exists(barcodeSub)
                ));
            }

            if (criteria.sku() != null && !criteria.sku().trim().isBlank()) {
                String skuPrefix = escapeLike(criteria.sku().trim().toUpperCase(Locale.ROOT)) + "%";
                Subquery<Integer> skuSub = query.subquery(Integer.class);
                Root<ProductVariant> vRoot = skuSub.from(ProductVariant.class);
                skuSub.select(cb.literal(1)).where(
                        cb.equal(vRoot.get("tenantId"), tenantId),
                        cb.equal(vRoot.get("productId"), root.get("id")),
                        cb.like(vRoot.get("sku"), skuPrefix, '\\')
                );
                predicates.add(cb.exists(skuSub));
            }

            if (criteria.barcode() != null && !criteria.barcode().trim().isBlank()) {
                String exactBarcode = criteria.barcode().trim();
                Subquery<Integer> barcodeSub = query.subquery(Integer.class);
                Root<ProductBarcode> bRoot = barcodeSub.from(ProductBarcode.class);
                Root<ProductVariant> bvRoot = barcodeSub.from(ProductVariant.class);
                barcodeSub.select(cb.literal(1)).where(
                        cb.equal(bRoot.get("tenantId"), tenantId),
                        cb.equal(bvRoot.get("tenantId"), tenantId),
                        cb.equal(bRoot.get("variantId"), bvRoot.get("id")),
                        cb.equal(bvRoot.get("productId"), root.get("id")),
                        cb.equal(bRoot.get("barcode"), exactBarcode)
                );
                predicates.add(cb.exists(barcodeSub));
            }

            if (criteria.categoryId() != null) {
                if (criteria.includeSubcategories()) {
                    predicates.add(root.get("categoryId").in(expandedCategoryIds));
                } else {
                    predicates.add(cb.equal(root.get("categoryId"), criteria.categoryId()));
                }
            }

            if (criteria.unitId() != null) {
                predicates.add(cb.equal(root.get("unitId"), criteria.unitId()));
            }

            if (criteria.brand() != null && !criteria.brand().trim().isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(cb.coalesce(root.get("brand"), "")),
                        criteria.brand().trim().toLowerCase(Locale.ROOT)
                ));
            }

            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            } else if (criteria.isActive() == null && !criteria.includeArchived()) {
                predicates.add(cb.notEqual(root.get("status"), ProductStatus.ARCHIVED));
            }

            if (criteria.isActive() != null) {
                if (Boolean.TRUE.equals(criteria.isActive())) {
                    predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
                } else {
                    predicates.add(cb.notEqual(root.get("status"), ProductStatus.ACTIVE));
                }
            }

            if (criteria.productType() != null) {
                predicates.add(cb.equal(root.get("productType"), criteria.productType()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort parseSafeSort(String sortParam) {
        if (sortParam == null || sortParam.trim().isBlank()) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        }
        String[] parts = sortParam.split(",", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Invalid sort format. Expected 'field,asc|desc'.");
        }
        String rawField = parts[0].trim().toLowerCase(Locale.ROOT);
        String mappedField = ALLOWED_SORT_FIELDS.get(rawField);
        if (mappedField == null) {
            throw new IllegalArgumentException("Unsupported sort field: '" + parts[0].trim() + "'.");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String rawDir = parts[1].trim().toLowerCase(Locale.ROOT);
            if ("desc".equals(rawDir)) {
                direction = Sort.Direction.DESC;
            } else if (!"asc".equals(rawDir)) {
                throw new IllegalArgumentException("Invalid sort direction: '" + parts[1].trim() + "'. Use 'asc' or 'desc'.");
            }
        }
        return Sort.by(new Sort.Order(direction, mappedField), Sort.Order.asc("id"));
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
