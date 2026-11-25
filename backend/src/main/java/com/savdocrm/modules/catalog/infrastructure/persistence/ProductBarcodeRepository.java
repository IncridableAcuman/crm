package com.savdocrm.modules.catalog.infrastructure.persistence;

import com.savdocrm.modules.catalog.domain.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, UUID> {

    Optional<ProductBarcode> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ProductBarcode> findByTenantIdAndBarcode(UUID tenantId, String barcode);

    boolean existsByTenantIdAndBarcode(UUID tenantId, String barcode);

    boolean existsByTenantIdAndBarcodeAndIdNot(UUID tenantId, String barcode, UUID id);

    List<ProductBarcode> findAllByTenantIdAndVariantIdOrderByIsPrimaryDescCreatedAtAsc(UUID tenantId, UUID variantId);

    List<ProductBarcode> findAllByTenantIdAndVariantIdIn(UUID tenantId, Collection<UUID> variantIds);

    Optional<ProductBarcode> findByTenantIdAndVariantIdAndIsPrimaryTrue(UUID tenantId, UUID variantId);

    void deleteAllByTenantIdAndVariantId(UUID tenantId, UUID variantId);
}
