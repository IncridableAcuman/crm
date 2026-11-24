package com.savdocrm.modules.identity.domain;

import com.savdocrm.modules.identity.domain.exception.SystemRoleImmutableException;
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

@Entity
@Table(
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_roles_tenant_code", columnNames = {"tenant_id", "code"}),
                @UniqueConstraint(name = "uq_roles_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseTenantEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean isSystem = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    public boolean isOwner() {
        return SystemRoles.isOwner(this.code);
    }

    public void update(String name, String description) {
        if (this.isSystem) {
            throw new SystemRoleImmutableException("Built-in system roles cannot be modified.");
        }
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Role name cannot be blank.");
        }
        this.name = name.trim();
        this.description = description != null ? description.trim() : null;
    }

    public void deactivate() {
        if (this.isSystem) {
            throw new SystemRoleImmutableException("Built-in system roles cannot be deactivated.");
        }
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
