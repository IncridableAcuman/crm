package com.savdocrm.modules.identity.infrastructure.persistence;

import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.shared.repository.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleAssignmentRepository extends TenantAwareRepository<UserRoleAssignment, UUID> {

    List<UserRoleAssignment> findAllByTenantIdAndMembershipIdAndIsActiveTrue(UUID tenantId, UUID membershipId);

    List<UserRoleAssignment> findAllByTenantIdAndMembershipId(UUID tenantId, UUID membershipId);

    Optional<UserRoleAssignment> findByTenantIdAndMembershipIdAndRoleIdAndScopeTypeAndIsActiveTrue(
            UUID tenantId, UUID membershipId, UUID roleId, RoleScopeType scopeType);

    Optional<UserRoleAssignment> findByTenantIdAndMembershipIdAndRoleIdAndScopeTypeAndBranchIdAndIsActiveTrue(
            UUID tenantId, UUID membershipId, UUID roleId, RoleScopeType scopeType, UUID branchId);

    boolean existsByTenantIdAndRoleIdAndIsActiveTrue(UUID tenantId, UUID roleId);

    boolean existsByTenantIdAndBranchIdAndIsActiveTrue(UUID tenantId, UUID branchId);

    @Query("""
        SELECT COUNT(DISTINCT m.id)
        FROM TenantMembership m
        JOIN User u ON u.id = m.userId
        JOIN UserRoleAssignment a ON a.membershipId = m.id AND a.tenantId = m.tenantId
        JOIN Role r ON r.id = a.roleId AND r.tenantId = a.tenantId
        WHERE m.tenantId = :tenantId
          AND m.status = com.savdocrm.modules.identity.domain.MembershipStatus.ACTIVE
          AND u.status = com.savdocrm.modules.identity.domain.UserStatus.ACTIVE
          AND r.code = 'OWNER'
          AND r.isActive = true
          AND a.isActive = true
          AND a.scopeType = com.savdocrm.modules.identity.domain.RoleScopeType.TENANT
          AND (:excludedMembershipId IS NULL OR m.id != :excludedMembershipId)
          AND (:excludedAssignmentId IS NULL OR a.id != :excludedAssignmentId)
          AND (:excludedUserId IS NULL OR u.id != :excludedUserId)
    """)
    long countEligibleOwners(
            @Param("tenantId") UUID tenantId,
            @Param("excludedMembershipId") UUID excludedMembershipId,
            @Param("excludedAssignmentId") UUID excludedAssignmentId,
            @Param("excludedUserId") UUID excludedUserId
    );

    @Query("""
        SELECT DISTINCT t.id
        FROM com.savdocrm.modules.tenancy.domain.Tenant t
        JOIN TenantMembership m ON m.tenantId = t.id
        JOIN User u ON u.id = m.userId
        JOIN UserRoleAssignment a ON a.membershipId = m.id AND a.tenantId = m.tenantId
        JOIN Role r ON r.id = a.roleId AND r.tenantId = a.tenantId
        WHERE m.userId = :userId
          AND t.status = com.savdocrm.modules.tenancy.domain.TenantStatus.ACTIVE
          AND m.status = com.savdocrm.modules.identity.domain.MembershipStatus.ACTIVE
          AND u.status = com.savdocrm.modules.identity.domain.UserStatus.ACTIVE
          AND r.code = 'OWNER'
          AND r.isActive = true
          AND a.isActive = true
          AND a.scopeType = com.savdocrm.modules.identity.domain.RoleScopeType.TENANT
    """)
    List<UUID> findActiveTenantIdsWhereUserIsEligibleOwner(@Param("userId") UUID userId);

    @Query("""
        SELECT t.id
        FROM com.savdocrm.modules.tenancy.domain.Tenant t
        WHERE t.status = com.savdocrm.modules.tenancy.domain.TenantStatus.ACTIVE
          AND NOT EXISTS (
              SELECT 1
              FROM TenantMembership m
              JOIN User u ON u.id = m.userId
              JOIN UserRoleAssignment a ON a.membershipId = m.id AND a.tenantId = m.tenantId
              JOIN Role r ON r.id = a.roleId AND r.tenantId = a.tenantId
              WHERE m.tenantId = t.id
                AND m.status = com.savdocrm.modules.identity.domain.MembershipStatus.ACTIVE
                AND u.status = com.savdocrm.modules.identity.domain.UserStatus.ACTIVE
                AND r.code = 'OWNER'
                AND r.isActive = true
                AND a.isActive = true
                AND a.scopeType = com.savdocrm.modules.identity.domain.RoleScopeType.TENANT
          )
    """)
    List<UUID> findUnbootstrappedActiveTenantIds();
}
