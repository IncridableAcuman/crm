package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.CreateUnitCommand;
import com.savdocrm.modules.catalog.application.dto.UnitOfMeasureDto;
import com.savdocrm.modules.catalog.application.dto.UpdateUnitCommand;
import com.savdocrm.modules.catalog.domain.CatalogValidation;
import com.savdocrm.modules.catalog.domain.StandardUnits;
import com.savdocrm.modules.catalog.domain.UnitOfMeasure;
import com.savdocrm.modules.catalog.domain.exception.DuplicateUnitCodeException;
import com.savdocrm.modules.catalog.domain.exception.UnitOfMeasureConflictException;
import com.savdocrm.modules.catalog.domain.exception.UnitOfMeasureNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final ProductRepository productRepository;

    /**
     * Idempotently seeds the 8 standard system units of measure for the active tenant.
     * Intended to be invoked inside the tenant registration/bootstrap transaction.
     */
    @Transactional
    public void initializeStandardUnitsForCurrentTenant() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        log.debug("Initializing standard units of measure for tenant {}", tenantId);

        for (StandardUnits.StandardUnitTemplate template : StandardUnits.TEMPLATES) {
            if (!unitOfMeasureRepository.existsByTenantIdAndCode(tenantId, template.code())) {
                UnitOfMeasure unit = UnitOfMeasure.createSystem(tenantId, template);
                try {
                    unitOfMeasureRepository.saveAndFlush(unit);
                } catch (DataIntegrityViolationException ex) {
                    throw CatalogConstraintTranslator.translate(ex);
                }
            }
        }
    }

    @Transactional
    public UnitOfMeasureDto createCustomUnit(CreateUnitCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "CreateUnitCommand cannot be null.");

        String normalizedCode = CatalogValidation.normalizeUnitCode(command.code());
        if (unitOfMeasureRepository.existsByTenantIdAndCode(tenantId, normalizedCode)) {
            throw new DuplicateUnitCodeException("Unit of measure code '" + normalizedCode + "' already exists in this tenant.");
        }

        UUID actorUserId = CatalogActorResolver.resolveOptionalActorUserId();
        UnitOfMeasure unit = UnitOfMeasure.createCustom(
                tenantId,
                normalizedCode,
                command.name(),
                command.symbol(),
                command.allowDecimal(),
                command.decimalPrecision()
        );
        unit.setCreatedBy(actorUserId);
        unit.setUpdatedBy(actorUserId);

        try {
            return UnitOfMeasureDto.fromEntity(unitOfMeasureRepository.saveAndFlush(unit));
        } catch (DataIntegrityViolationException ex) {
            throw CatalogConstraintTranslator.translate(ex);
        }
    }

    @Transactional
    public UnitOfMeasureDto updateCustomUnit(UUID unitId, UpdateUnitCommand command) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Objects.requireNonNull(command, "UpdateUnitCommand cannot be null.");

        UnitOfMeasure unit = unitOfMeasureRepository.findByTenantIdAndIdWithLock(tenantId, unitId)
                .orElseThrow(() -> new UnitOfMeasureNotFoundException(unitId));

        if (unit.isPrecisionNarrowedBy(command.allowDecimal(), command.decimalPrecision())
                && productRepository.existsByTenantIdAndUnitId(tenantId, unitId)) {
            throw new UnitOfMeasureConflictException(
                    "Cannot narrow decimal precision for unit '" + unit.getCode() + "' because products reference it."
            );
        }

        unit.update(command.name(), command.symbol(), command.allowDecimal(), command.decimalPrecision());
        unit.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());

        return UnitOfMeasureDto.fromEntity(unitOfMeasureRepository.saveAndFlush(unit));
    }

    @Transactional
    public UnitOfMeasureDto deactivateCustomUnit(UUID unitId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UnitOfMeasure unit = unitOfMeasureRepository.findByTenantIdAndIdWithLock(tenantId, unitId)
                .orElseThrow(() -> new UnitOfMeasureNotFoundException(unitId));

        unit.deactivate();
        unit.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());
        return UnitOfMeasureDto.fromEntity(unitOfMeasureRepository.saveAndFlush(unit));
    }

    @Transactional
    public UnitOfMeasureDto activateCustomUnit(UUID unitId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UnitOfMeasure unit = unitOfMeasureRepository.findByTenantIdAndIdWithLock(tenantId, unitId)
                .orElseThrow(() -> new UnitOfMeasureNotFoundException(unitId));

        unit.activate();
        unit.setUpdatedBy(CatalogActorResolver.resolveOptionalActorUserId());
        return UnitOfMeasureDto.fromEntity(unitOfMeasureRepository.saveAndFlush(unit));
    }

    @Transactional
    public void deleteCustomUnit(UUID unitId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        UnitOfMeasure unit = unitOfMeasureRepository.findByTenantIdAndIdWithLock(tenantId, unitId)
                .orElseThrow(() -> new UnitOfMeasureNotFoundException(unitId));

        unit.assertDeletable();

        if (productRepository.existsByTenantIdAndUnitId(tenantId, unitId)) {
            throw new UnitOfMeasureConflictException(
                    "Cannot delete unit '" + unit.getCode() + "' because existing products reference it; deactivate it instead."
            );
        }

        unitOfMeasureRepository.delete(unit);
    }

    @Transactional(readOnly = true)
    public UnitOfMeasureDto getUnit(UUID unitId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return unitOfMeasureRepository.findByTenantIdAndId(tenantId, unitId)
                .map(UnitOfMeasureDto::fromEntity)
                .orElseThrow(() -> new UnitOfMeasureNotFoundException(unitId));
    }

    @Transactional(readOnly = true)
    public List<UnitOfMeasureDto> listUnits(boolean includeInactive) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<UnitOfMeasure> units = includeInactive
                ? unitOfMeasureRepository.findAllByTenantIdOrderByIsSystemDescCodeAsc(tenantId)
                : unitOfMeasureRepository.findAllByTenantIdAndIsActiveTrueOrderByIsSystemDescCodeAsc(tenantId);
        return units.stream().map(UnitOfMeasureDto::fromEntity).toList();
    }
}
