package com.savdocrm.modules.catalog.domain;

import com.savdocrm.modules.catalog.domain.exception.InvalidBarcodeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Product barcode entity associated with a ProductVariant.
 * Note: product_barcodes intentionally omits a version column in the M05.2 schema;
 * concurrency on barcode primary swaps is serialized by locking the owning ProductVariant.
 */
@Entity
@Table(
        name = "product_barcodes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_product_barcodes_tenant_id", columnNames = {"tenant_id", "id"}),
                @UniqueConstraint(name = "uq_product_barcodes_tenant_barcode", columnNames = {"tenant_id", "barcode"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @Column(name = "barcode", nullable = false, length = 100)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_type", nullable = false, length = 30)
    @Builder.Default
    private BarcodeType barcodeType = BarcodeType.EAN13;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static ProductBarcode create(
            UUID tenantId,
            UUID variantId,
            String rawBarcode,
            BarcodeType barcodeType,
            boolean isPrimary
    ) {
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null.");
        Objects.requireNonNull(variantId, "Variant ID cannot be null.");
        if (barcodeType == null) {
            throw new InvalidBarcodeException("Barcode type cannot be null.");
        }

        return ProductBarcode.builder()
                .tenantId(tenantId)
                .variantId(variantId)
                .barcode(CatalogValidation.normalizeBarcode(rawBarcode))
                .barcodeType(barcodeType)
                .isPrimary(isPrimary)
                .build();
    }

    public void update(String rawBarcode, BarcodeType barcodeType, boolean isPrimary) {
        if (barcodeType == null) {
            throw new InvalidBarcodeException("Barcode type cannot be null.");
        }
        this.barcode = CatalogValidation.normalizeBarcode(rawBarcode);
        this.barcodeType = barcodeType;
        this.isPrimary = isPrimary;
    }

    public void markAsPrimary() {
        this.isPrimary = true;
    }

    public void unmarkAsPrimary() {
        this.isPrimary = false;
    }
}
