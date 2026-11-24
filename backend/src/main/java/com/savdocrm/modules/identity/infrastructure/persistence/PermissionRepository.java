package com.savdocrm.modules.identity.infrastructure.persistence;

import com.savdocrm.modules.identity.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);

    List<Permission> findAllByOrderByCodeAsc();

    List<Permission> findAllByModule(String module);

    List<Permission> findAllByCodeIn(Set<String> codes);
}
