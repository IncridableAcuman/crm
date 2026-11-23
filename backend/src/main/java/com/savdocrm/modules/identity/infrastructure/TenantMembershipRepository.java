package com.savdocrm.modules.identity.infrastructure;

import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<TenantMembership> findAllByUserIdAndStatus(UUID userId, MembershipStatus status);

    List<TenantMembership> findAllByUserId(UUID userId);

    boolean existsByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, MembershipStatus status);
}
