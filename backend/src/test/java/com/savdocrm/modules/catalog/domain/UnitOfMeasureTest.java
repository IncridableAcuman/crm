package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.SystemUnitImmutableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitOfMeasureTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("StandardUnits defines exactly the 8 approved system units")
    void standardUnits_containsApprovedEight() {
        assertThat(StandardUnits.TEMPLATES)
                .extracting(StandardUnits.StandardUnitTemplate::code)
                .containsExactly("PCS", "KG", "G", "L", "ML", "M", "BOX", "PACK");
    }

    @Test
    @DisplayName("Unit validates decimal precision 0..3 and allowDecimal consistency")
    void unit_validatesDecimalPrecisionRules() {
        UnitOfMeasure custom = UnitOfMeasure.createCustom(tenantId, " roll ", " Roll ", " rl ", true, 2);
        assertThat(custom.getCode()).isEqualTo("ROLL");
        assertThat(custom.getDecimalPrecision()).isEqualTo((short) 2);
        assertThat(custom.isPrecisionNarrowedBy(true, 1)).isTrue();
        assertThat(custom.isPrecisionNarrowedBy(false, 0)).isTrue();
        assertThat(custom.isPrecisionNarrowedBy(true, 3)).isFalse();

        assertThatThrownBy(() -> UnitOfMeasure.createCustom(tenantId, "BAD1", "Bad", "b", false, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UnitOfMeasure.createCustom(tenantId, "BAD2", "Bad", "b", true, 4))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UnitOfMeasure.createCustom(tenantId, "BAD3", "Bad", "b", true, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UnitOfMeasure.createCustom(tenantId, "OK", "   ", "b", false, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UnitOfMeasure.createCustom(tenantId, "OK", "Valid", "   ", false, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("System units cannot be modified, deactivated, activated, or deleted")
    void systemUnit_isImmutable() {
        UnitOfMeasure kg = UnitOfMeasure.createSystem(tenantId, StandardUnits.TEMPLATES.get(1));
        assertThat(kg.isSystem()).isTrue();

        assertThatThrownBy(() -> kg.update("Kilogram Edited", "kg", true, 3))
                .isInstanceOf(SystemUnitImmutableException.class);
        assertThatThrownBy(kg::deactivate)
                .isInstanceOf(SystemUnitImmutableException.class);
        assertThatThrownBy(kg::activate)
                .isInstanceOf(SystemUnitImmutableException.class);
        assertThatThrownBy(kg::assertDeletable)
                .isInstanceOf(SystemUnitImmutableException.class);
    }
}
