package com.savdocrm.modules.identity.domain;

import com.savdocrm.modules.identity.domain.exception.SystemRoleImmutableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    @DisplayName("System role: update should throw SystemRoleImmutableException")
    void systemRole_update_throwsException() {
        Role role = Role.builder()
                .code(SystemRoles.OWNER)
                .name("Owner")
                .description("System owner role")
                .isSystem(true)
                .isActive(true)
                .build();
        role.setTenantId(UUID.randomUUID());

        assertThat(role.isOwner()).isTrue();
        assertThatThrownBy(() -> role.update("Renamed Owner", "New description"))
                .isInstanceOf(SystemRoleImmutableException.class)
                .hasMessageContaining("Built-in system roles cannot be modified");
    }

    @Test
    @DisplayName("System role: deactivate should throw SystemRoleImmutableException")
    void systemRole_deactivate_throwsException() {
        Role role = Role.builder()
                .code(SystemRoles.ADMIN)
                .name("Admin")
                .isSystem(true)
                .isActive(true)
                .build();

        assertThatThrownBy(role::deactivate)
                .isInstanceOf(SystemRoleImmutableException.class)
                .hasMessageContaining("Built-in system roles cannot be deactivated");
    }

    @Test
    @DisplayName("Custom role: update and deactivate succeed")
    void customRole_operations_succeed() {
        Role role = Role.builder()
                .code("SALES_REP")
                .name("Sales Representative")
                .description("Handles store sales")
                .isSystem(false)
                .isActive(true)
                .build();
        role.setTenantId(UUID.randomUUID());

        assertThat(role.isOwner()).isFalse();

        role.update("Senior Sales Rep", "Handles enterprise sales");
        assertThat(role.getName()).isEqualTo("Senior Sales Rep");
        assertThat(role.getDescription()).isEqualTo("Handles enterprise sales");

        role.deactivate();
        assertThat(role.isActive()).isFalse();

        role.activate();
        assertThat(role.isActive()).isTrue();
    }

    @Test
    @DisplayName("Custom role: blank name update throws IllegalArgumentException")
    void customRole_blankName_throwsException() {
        Role role = Role.builder()
                .code("CUSTOM")
                .name("Custom")
                .isSystem(false)
                .isActive(true)
                .build();

        assertThatThrownBy(() -> role.update("   ", "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role name cannot be blank");
    }
}
