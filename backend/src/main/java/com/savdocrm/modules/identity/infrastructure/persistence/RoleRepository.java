package com.savdocrm.modules.identity.infrastructure.persistence;

import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.shared.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends TenantAwareRepository<Role, UUID> {

    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<Role> findAllByTenantId(UUID tenantId);

    List<Role> findAllByTenantIdAndIsActiveTrue(UUID tenantId);
}
