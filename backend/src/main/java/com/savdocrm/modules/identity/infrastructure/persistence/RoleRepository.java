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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Role r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<Role> findByTenantIdAndIdWithLock(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("id") UUID id
    );
}
