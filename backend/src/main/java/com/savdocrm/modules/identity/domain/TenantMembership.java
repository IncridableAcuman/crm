package com.savdocrm.modules.identity.domain;

import com.savdocrm.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "tenant_memberships",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_memberships_tenant_user", columnNames = {"tenant_id", "user_id"}),
                @UniqueConstraint(name = "uq_memberships_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMembership extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    public boolean isActive() {
        return this.status == MembershipStatus.ACTIVE;
    }

    public void suspend() {
        this.status = MembershipStatus.SUSPENDED;
    }

    public void activate() {
        this.status = MembershipStatus.ACTIVE;
    }
}
