package com.savdocrm.modules.catalog.infrastructure.persistence;

import com.savdocrm.modules.catalog.domain.ProductBarcode;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for ProductBarcode.
 * Note: ProductBarcode intentionally does not extend BaseTenantEntity because the approved M05.2
 * product_barcodes table omits a version column; therefore this repository provides explicit
 * tenant-scoped methods mirroring TenantAwareRepository without requiring @Version.
 */
@Repository
public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, UUID> {

    Optional<ProductBarcode> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ProductBarcode b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<ProductBarcode> findByTenantIdAndIdWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );

    Page<ProductBarcode> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndId(UUID tenantId, UUID id);

    void deleteByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ProductBarcode> findByTenantIdAndBarcode(UUID tenantId, String barcode);

    boolean existsByTenantIdAndBarcode(UUID tenantId, String barcode);

    boolean existsByTenantIdAndBarcodeAndIdNot(UUID tenantId, String barcode, UUID id);

    List<ProductBarcode> findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(UUID tenantId, UUID variantId);

    List<ProductBarcode> findAllByTenantIdAndVariantIdIn(UUID tenantId, Collection<UUID> variantIds);

    Optional<ProductBarcode> findByTenantIdAndVariantIdAndIsPrimaryTrue(UUID tenantId, UUID variantId);

    void deleteAllByTenantIdAndVariantId(UUID tenantId, UUID variantId);

    default Optional<ProductBarcode> findByIdInCurrentTenant(UUID id) {
        return findByTenantIdAndId(TenantContextHolder.getRequiredTenantId(), id);
    }

    default Page<ProductBarcode> findAllInCurrentTenant(Pageable pageable) {
        return findAllByTenantId(TenantContextHolder.getRequiredTenantId(), pageable);
    }

    default boolean existsByIdInCurrentTenant(UUID id) {
        return existsByTenantIdAndId(TenantContextHolder.getRequiredTenantId(), id);
    }

    default void deleteByIdInCurrentTenant(UUID id) {
        deleteByTenantIdAndId(TenantContextHolder.getRequiredTenantId(), id);
    }
}
