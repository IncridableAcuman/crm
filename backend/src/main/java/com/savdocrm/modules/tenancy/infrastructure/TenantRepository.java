package com.savdocrm.modules.tenancy.infrastructure;

import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);

    List<Tenant> findAllByStatus(TenantStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Tenant t WHERE t.id = :id")
    Optional<Tenant> findByIdWithLock(@org.springframework.data.repository.query.Param("id") UUID id);
}
