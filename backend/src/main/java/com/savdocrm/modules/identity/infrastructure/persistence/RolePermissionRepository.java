package com.savdocrm.modules.identity.infrastructure.persistence;

import com.savdocrm.modules.identity.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findAllByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    List<RolePermission> findAllByRoleId(UUID roleId);

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    void deleteAllByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    @Query("""
        SELECT p.code
        FROM RolePermission rp
        JOIN Permission p ON p.id = rp.permissionId
        WHERE rp.tenantId = :tenantId AND rp.roleId = :roleId
    """)
    Set<String> findPermissionCodesByTenantIdAndRoleId(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

    @Query("""
        SELECT rp.roleId, p.code
        FROM RolePermission rp
        JOIN Permission p ON p.id = rp.permissionId
        WHERE rp.tenantId = :tenantId AND rp.roleId IN :roleIds
    """)
    List<Object[]> findRolePermissionPairsByTenantIdAndRoleIds(@Param("tenantId") UUID tenantId, @Param("roleIds") Collection<UUID> roleIds);
}
