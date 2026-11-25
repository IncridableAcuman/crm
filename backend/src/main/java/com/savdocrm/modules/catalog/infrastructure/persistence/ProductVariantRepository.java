package com.savdocrm.modules.catalog.infrastructure.persistence;

import com.savdocrm.modules.catalog.domain.ProductVariant;
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
public interface ProductVariantRepository extends TenantAwareRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByTenantIdAndSku(UUID tenantId, String sku);

    boolean existsByTenantIdAndSku(UUID tenantId, String sku);

    boolean existsByTenantIdAndSkuAndIdNot(UUID tenantId, String sku, UUID id);

    List<ProductVariant> findAllByTenantIdAndProductIdOrderByIsDefaultDescCreatedAtAsc(UUID tenantId, UUID productId);

    List<ProductVariant> findAllByTenantIdAndProductIdAndIsActiveTrue(UUID tenantId, UUID productId);

    Optional<ProductVariant> findByTenantIdAndProductIdAndIsDefaultTrue(UUID tenantId, UUID productId);

    long countByTenantIdAndProductIdAndIsActiveTrue(UUID tenantId, UUID productId);

    boolean existsByTenantIdAndProductIdAndIsActiveTrue(UUID tenantId, UUID productId);

    List<ProductVariant> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.tenantId = :tenantId AND v.id = :id")
    Optional<ProductVariant> findByTenantIdAndIdWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.tenantId = :tenantId AND v.productId = :productId ORDER BY v.isDefault DESC, v.createdAt ASC")
    List<ProductVariant> findAllByTenantIdAndProductIdWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId
    );
}
