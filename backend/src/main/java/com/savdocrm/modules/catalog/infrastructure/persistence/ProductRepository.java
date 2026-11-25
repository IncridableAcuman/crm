package com.savdocrm.modules.catalog.infrastructure.persistence;

import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.shared.repository.TenantAwareRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends TenantAwareRepository<Product, UUID> {

    boolean existsByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    boolean existsByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    List<Product> findAllByTenantIdAndStatus(UUID tenantId, ProductStatus status);

    List<Product> findAllByTenantIdAndStatusNot(UUID tenantId, ProductStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<Product> findByTenantIdAndIdWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );
}
