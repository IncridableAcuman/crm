package com.savdocrm.shared.repository;

import com.savdocrm.shared.domain.BaseTenantEntity;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface TenantAwareRepository<T extends BaseTenantEntity, ID> extends JpaRepository<T, ID> {

    Optional<T> findByTenantIdAndId(UUID tenantId, ID id);

    Page<T> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndId(UUID tenantId, ID id);

    void deleteByTenantIdAndId(UUID tenantId, ID id);

    default Optional<T> findByIdInCurrentTenant(ID id) {
        return findByTenantIdAndId(TenantContextHolder.getRequiredTenantId(), id);
    }

    default Page<T> findAllInCurrentTenant(Pageable pageable) {
        return findAllByTenantId(TenantContextHolder.getRequiredTenantId(), pageable);
    }

    default boolean existsByIdInCurrentTenant(ID id) {
        return existsByTenantIdAndId(TenantContextHolder.getRequiredTenantId(), id);
    }

    default void deleteByIdInCurrentTenant(ID id) {
        deleteByTenantIdAndId(TenantContextHolder.getRequiredTenantId(), id);
    }
}
