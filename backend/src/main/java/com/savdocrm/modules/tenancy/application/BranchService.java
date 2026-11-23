package com.savdocrm.modules.tenancy.application;

import com.savdocrm.modules.tenancy.application.dto.BranchDto;
import com.savdocrm.modules.tenancy.application.dto.CreateBranchCommand;
import com.savdocrm.modules.tenancy.domain.Branch;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    @Transactional
    public BranchDto createBranch(CreateBranchCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        if (branchRepository.existsByTenantIdAndCode(tenantId, command.code())) {
            throw new IllegalArgumentException("Branch with code '" + command.code() + "' already exists for this store.");
        }

        Branch branch = Branch.builder()
                .name(command.name())
                .code(command.code())
                .address(command.address())
                .phone(command.phone())
                .isActive(true)
                .isMain(command.isMain())
                .build();
        branch.setTenantId(tenantId);

        Branch saved = branchRepository.save(branch);
        return BranchDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Optional<BranchDto> getBranchById(UUID branchId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return branchRepository.findByTenantIdAndId(tenantId, branchId).map(BranchDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<BranchDto> getActiveBranches() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return branchRepository.findAllByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(BranchDto::fromEntity)
                .toList();
    }

    @Transactional
    public void deactivateBranch(UUID branchId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Branch branch = branchRepository.findByTenantIdAndId(tenantId, branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found with ID: " + branchId));
        branch.deactivate();
    }
}
