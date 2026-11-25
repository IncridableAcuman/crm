package com.savdocrm.modules.catalog.infrastructure.persistence;

import com.savdocrm.modules.catalog.domain.UnitOfMeasure;
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
public interface UnitOfMeasureRepository extends TenantAwareRepository<UnitOfMeasure, UUID> {

    Optional<UnitOfMeasure> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<UnitOfMeasure> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    List<UnitOfMeasure> findAllByTenantIdOrderByIsSystemDescCodeAsc(UUID tenantId);

    List<UnitOfMeasure> findAllByTenantIdAndIsActiveTrueOrderByIsSystemDescCodeAsc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UnitOfMeasure u WHERE u.tenantId = :tenantId AND u.id = :id")
    Optional<UnitOfMeasure> findByTenantIdAndIdWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );
}
