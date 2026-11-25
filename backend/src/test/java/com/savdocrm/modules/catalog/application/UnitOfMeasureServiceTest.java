package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.catalog.application.dto.CreateUnitCommand;
import com.savdocrm.modules.catalog.application.dto.UnitOfMeasureDto;
import com.savdocrm.modules.catalog.application.dto.UpdateUnitCommand;
import com.savdocrm.modules.catalog.domain.StandardUnits;
import com.savdocrm.modules.catalog.domain.UnitOfMeasure;
import com.savdocrm.modules.catalog.domain.exception.DuplicateUnitCodeException;
import com.savdocrm.modules.catalog.domain.exception.SystemUnitImmutableException;
import com.savdocrm.modules.catalog.domain.exception.UnitOfMeasureConflictException;
import com.savdocrm.modules.catalog.domain.exception.UnitOfMeasureNotFoundException;
import com.savdocrm.modules.catalog.infrastructure.persistence.ProductRepository;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitOfMeasureServiceTest {

    @Mock
    private UnitOfMeasureRepository unitRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private UnitOfMeasureService unitService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("initializeStandardUnitsForCurrentTenant seeds missing units idempotently")
    void initializeStandardUnits_isIdempotent() {
        when(unitRepository.existsByTenantIdAndCode(tenantId, "PCS")).thenReturn(true);
        when(unitRepository.existsByTenantIdAndCode(tenantId, "KG")).thenReturn(false);
        when(unitRepository.existsByTenantIdAndCode(tenantId, "G")).thenReturn(false);
        when(unitRepository.existsByTenantIdAndCode(tenantId, "L")).thenReturn(false);
        when(unitRepository.existsByTenantIdAndCode(tenantId, "ML")).thenReturn(false);
        when(unitRepository.existsByTenantIdAndCode(tenantId, "M")).thenReturn(false);
        when(unitRepository.existsByTenantIdAndCode(tenantId, "BOX")).thenReturn(false);
        when(unitRepository.existsByTenantIdAndCode(tenantId, "PACK")).thenReturn(false);

        unitService.initializeStandardUnitsForCurrentTenant();

        verify(unitRepository, times(7)).saveAndFlush(any(UnitOfMeasure.class));
    }

    @Test
    @DisplayName("Rejects duplicate unit code and protects system units from edits/deactivation/deletion")
    void customAndSystemUnitGuards() {
        when(unitRepository.existsByTenantIdAndCode(tenantId, "ROLL")).thenReturn(true);
        assertThatThrownBy(() -> unitService.createCustomUnit(
                new CreateUnitCommand("roll", "Roll", "rl", false, 0)
        )).isInstanceOf(DuplicateUnitCodeException.class);

        UUID sysId = UUID.randomUUID();
        UnitOfMeasure sysUnit = UnitOfMeasure.createSystem(tenantId, StandardUnits.TEMPLATES.get(0));
        sysUnit.setId(sysId);
        when(unitRepository.findByTenantIdAndIdWithLock(tenantId, sysId)).thenReturn(Optional.of(sysUnit));

        assertThatThrownBy(() -> unitService.updateCustomUnit(sysId, new UpdateUnitCommand("Edited", "e", false, 0)))
                .isInstanceOf(SystemUnitImmutableException.class);
        assertThatThrownBy(() -> unitService.deactivateCustomUnit(sysId))
                .isInstanceOf(SystemUnitImmutableException.class);
        assertThatThrownBy(() -> unitService.deleteCustomUnit(sysId))
                .isInstanceOf(SystemUnitImmutableException.class);
    }

    @Test
    @DisplayName("Rejects narrowing decimal precision or deleting a custom unit referenced by products")
    void referencedCustomUnitGuards() {
        UUID unitId = UUID.randomUUID();
        UnitOfMeasure custom = UnitOfMeasure.createCustom(tenantId, "ROLL", "Roll", "rl", true, 2);
        custom.setId(unitId);

        when(unitRepository.findByTenantIdAndIdWithLock(tenantId, unitId)).thenReturn(Optional.of(custom));
        when(productRepository.existsByTenantIdAndUnitId(tenantId, unitId)).thenReturn(true);

        assertThatThrownBy(() -> unitService.updateCustomUnit(unitId, new UpdateUnitCommand("Roll", "rl", true, 1)))
                .isInstanceOf(UnitOfMeasureConflictException.class);

        assertThatThrownBy(() -> unitService.deleteCustomUnit(unitId))
                .isInstanceOf(UnitOfMeasureConflictException.class);

        // Increasing precision or keeping precision while referenced succeeds
        when(unitRepository.saveAndFlush(any(UnitOfMeasure.class))).thenAnswer(inv -> inv.getArgument(0));
        UnitOfMeasureDto updated = unitService.updateCustomUnit(unitId, new UpdateUnitCommand("Roll Wide", "rl", true, 3));
        assertThat(updated.decimalPrecision()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("Cross-tenant unit access fails closed with UnitOfMeasureNotFoundException")
    void crossTenantUnitFailsClosed() {
        UUID foreignUnitId = UUID.randomUUID();
        when(unitRepository.findByTenantIdAndId(tenantId, foreignUnitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> unitService.getUnit(foreignUnitId))
                .isInstanceOf(UnitOfMeasureNotFoundException.class);
    }
}
