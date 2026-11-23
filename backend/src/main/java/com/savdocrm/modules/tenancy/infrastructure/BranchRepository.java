package com.savdocrm.modules.tenancy.infrastructure;

import com.savdocrm.modules.tenancy.domain.Branch;
import com.savdocrm.shared.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends TenantAwareRepository<Branch, UUID> {

    Optional<Branch> findByTenantIdAndCode(UUID tenantId, String code);

    List<Branch> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    default Optional<Branch> findByCodeInCurrentTenant(String code) {
        return findByTenantIdAndCode(com.savdocrm.shared.multitenancy.TenantContextHolder.getRequiredTenantId(), code);
    }
}
