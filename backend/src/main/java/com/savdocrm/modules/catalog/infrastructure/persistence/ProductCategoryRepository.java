package com.savdocrm.modules.catalog.infrastructure.persistence;

import com.savdocrm.modules.catalog.domain.ProductCategory;
import com.savdocrm.shared.repository.TenantAwareRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends TenantAwareRepository<ProductCategory, UUID> {

    Optional<ProductCategory> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndParentId(UUID tenantId, UUID parentId);

    boolean existsByTenantIdAndParentIdAndIsActiveTrue(UUID tenantId, UUID parentId);

    boolean existsByTenantIdAndParentIdIsNullAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndParentIdIsNullAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);

    boolean existsByTenantIdAndParentIdAndNameIgnoreCase(UUID tenantId, UUID parentId, String name);

    boolean existsByTenantIdAndParentIdAndNameIgnoreCaseAndIdNot(UUID tenantId, UUID parentId, String name, UUID id);

    List<ProductCategory> findAllByTenantIdAndParentIdIn(UUID tenantId, Collection<UUID> parentIds);

    List<ProductCategory> findAllByTenantIdOrderByDepthAscSortOrderAscNameAsc(UUID tenantId);

    List<ProductCategory> findAllByTenantIdAndIsActiveTrueOrderByDepthAscSortOrderAscNameAsc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ProductCategory c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<ProductCategory> findByTenantIdAndIdWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );
}
