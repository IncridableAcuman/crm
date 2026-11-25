package com.savdocrm.modules.tenancy.application;

import com.savdocrm.modules.tenancy.application.dto.CreateTenantCommand;
import com.savdocrm.modules.tenancy.application.dto.TenantDto;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.domain.event.TenantCreatedEvent;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TenantDto createTenant(CreateTenantCommand command) {
        if (tenantRepository.existsByCode(command.code())) {
            throw new IllegalArgumentException("Tenant with code '" + command.code() + "' already exists.");
        }

        Tenant tenant = Tenant.builder()
                .name(command.name())
                .code(command.code())
                .currencyCode(command.currencyCode() != null && !command.currencyCode().isBlank() ? command.currencyCode().toUpperCase() : "USD")
                .timezone(command.timezone() != null && !command.timezone().isBlank() ? command.timezone() : "UTC")
                .status(TenantStatus.ACTIVE)
                .build();

        Tenant saved = tenantRepository.save(tenant);
        eventPublisher.publishEvent(new TenantCreatedEvent(saved.getId()));
        return TenantDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Optional<TenantDto> getTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(TenantDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<TenantDto> getTenantByCode(String code) {
        return tenantRepository.findByCode(code).map(TenantDto::fromEntity);
    }

    @Transactional
    public void suspendTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));
        tenant.suspend();
    }

    @Transactional
    public void activateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));
        tenant.activate();
    }
}
