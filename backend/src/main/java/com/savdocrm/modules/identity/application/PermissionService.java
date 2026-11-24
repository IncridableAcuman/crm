package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.application.dto.PermissionDto;
import com.savdocrm.modules.identity.infrastructure.persistence.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only access to the system permissions master catalog.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
                .map(PermissionDto::fromEntity)
                .toList();
    }
}
