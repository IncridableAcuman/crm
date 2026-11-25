package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.SystemUnitImmutableException;
import com.savdocrm.shared.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "units_of_measure",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_units_of_measure_tenant_id", columnNames = {"tenant_id", "id"}),
                @UniqueConstraint(name = "uq_units_of_measure_tenant_code", columnNames = {"tenant_id", "code"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitOfMeasure extends BaseTenantEntity {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_SYMBOL_LENGTH = 15;
    public static final int MIN_DECIMAL_PRECISION = 0;
    public static final int MAX_DECIMAL_PRECISION = 3;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "symbol", nullable = false, length = 15)
    private String symbol;

    @Column(name = "allow_decimal", nullable = false)
    @Builder.Default
    private boolean allowDecimal = false;

    @Column(name = "decimal_precision", nullable = false)
    @Builder.Default
    private short decimalPrecision = 0;

    @Column(name = "is_system", nullable = false, updatable = false)
    @Builder.Default
    private boolean isSystem = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    public static UnitOfMeasure createCustom(
            UUID tenantId,
            String rawCode,
            String rawName,
            String rawSymbol,
            boolean allowDecimal,
            int decimalPrecision
    ) {
        return createInternal(tenantId, rawCode, rawName, rawSymbol, allowDecimal, decimalPrecision, false);
    }

    public static UnitOfMeasure createSystem(
            UUID tenantId,
            StandardUnits.StandardUnitTemplate template
    ) {
        Objects.requireNonNull(template, "Standard unit template cannot be null.");
        return createInternal(
                tenantId,
                template.code(),
                template.name(),
                template.symbol(),
                template.allowDecimal(),
                template.decimalPrecision(),
                true
        );
    }

    private static UnitOfMeasure createInternal(
            UUID tenantId,
            String rawCode,
            String rawName,
            String rawSymbol,
            boolean allowDecimal,
            int decimalPrecision,
            boolean isSystem
    ) {
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null.");
        validatePrecisionRules(allowDecimal, decimalPrecision);

        UnitOfMeasure unit = UnitOfMeasure.builder()
                .code(CatalogValidation.normalizeUnitCode(rawCode))
                .name(CatalogValidation.normalizeRequiredText(rawName, "Unit name", MAX_NAME_LENGTH))
                .symbol(CatalogValidation.normalizeRequiredText(rawSymbol, "Unit symbol", MAX_SYMBOL_LENGTH))
                .allowDecimal(allowDecimal)
                .decimalPrecision((short) decimalPrecision)
                .isSystem(isSystem)
                .isActive(true)
                .build();
        unit.setTenantId(tenantId);
        return unit;
    }

    public void update(String rawName, String rawSymbol, boolean allowDecimal, int decimalPrecision) {
        assertMutable("Built-in system units of measure cannot be modified.");
        validatePrecisionRules(allowDecimal, decimalPrecision);
        this.name = CatalogValidation.normalizeRequiredText(rawName, "Unit name", MAX_NAME_LENGTH);
        this.symbol = CatalogValidation.normalizeRequiredText(rawSymbol, "Unit symbol", MAX_SYMBOL_LENGTH);
        this.allowDecimal = allowDecimal;
        this.decimalPrecision = (short) decimalPrecision;
    }

    public void deactivate() {
        assertMutable("Built-in system units of measure cannot be deactivated.");
        this.isActive = false;
    }

    public void activate() {
        assertMutable("Built-in system units of measure cannot be modified.");
        this.isActive = true;
    }

    public void assertDeletable() {
        assertMutable("Built-in system units of measure cannot be deleted.");
    }

    public boolean isPrecisionNarrowedBy(boolean newAllowDecimal, int newDecimalPrecision) {
        if (this.allowDecimal && !newAllowDecimal) {
            return true;
        }
        return newDecimalPrecision < this.decimalPrecision;
    }

    private void assertMutable(String message) {
        if (this.isSystem) {
            throw new SystemUnitImmutableException(message);
        }
    }

    public static void validatePrecisionRules(boolean allowDecimal, int decimalPrecision) {
        if (decimalPrecision < MIN_DECIMAL_PRECISION || decimalPrecision > MAX_DECIMAL_PRECISION) {
            throw new IllegalArgumentException(
                    "Decimal precision must be between " + MIN_DECIMAL_PRECISION + " and " + MAX_DECIMAL_PRECISION + "."
            );
        }
        if (!allowDecimal && decimalPrecision != 0) {
            throw new IllegalArgumentException("Decimal precision must be 0 when allowDecimal is false.");
        }
    }
}
