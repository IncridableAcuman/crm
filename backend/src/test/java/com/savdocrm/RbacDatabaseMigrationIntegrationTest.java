package com.savdocrm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class RbacDatabaseMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_rbac_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID branchAId;
    private UUID branchBId;
    private UUID user1Id;
    private UUID user2Id;
    private UUID memberA1Id;
    private UUID memberB2Id;
    private UUID roleAOwnerId;
    private UUID roleAManagerId;
    private UUID roleBOwnerId;

    @BeforeEach
    void setUpTestData() {
        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();
        branchAId = UUID.randomUUID();
        branchBId = UUID.randomUUID();
        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();
        memberA1Id = UUID.randomUUID();
        memberB2Id = UUID.randomUUID();
        roleAOwnerId = UUID.randomUUID();
        roleAManagerId = UUID.randomUUID();
        roleBOwnerId = UUID.randomUUID();

        // Clean operational tables (order respecting FKs)
        jdbcTemplate.execute("DELETE FROM user_role_assignments");
        jdbcTemplate.execute("DELETE FROM role_permissions");
        jdbcTemplate.execute("DELETE FROM roles");
        jdbcTemplate.execute("DELETE FROM tenant_memberships");
        jdbcTemplate.execute("DELETE FROM branches");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenants");

        // Insert Tenants
        jdbcTemplate.update("INSERT INTO tenants (id, name, code, currency_code, timezone, status) VALUES (?, ?, ?, 'USD', 'UTC', 'ACTIVE')",
                tenantAId, "Tenant A", "tenant-a-" + tenantAId.toString().substring(0, 8));
        jdbcTemplate.update("INSERT INTO tenants (id, name, code, currency_code, timezone, status) VALUES (?, ?, ?, 'EUR', 'UTC', 'ACTIVE')",
                tenantBId, "Tenant B", "tenant-b-" + tenantBId.toString().substring(0, 8));

        // Insert Branches
        jdbcTemplate.update("INSERT INTO branches (id, tenant_id, name, code) VALUES (?, ?, 'Main Branch A', 'MAIN-A')",
                branchAId, tenantAId);
        jdbcTemplate.update("INSERT INTO branches (id, tenant_id, name, code) VALUES (?, ?, 'Main Branch B', 'MAIN-B')",
                branchBId, tenantBId);

        // Insert Users
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, first_name, last_name) VALUES (?, ?, ?, 'hash', 'Alice', 'Admin')",
                user1Id, "alice_" + user1Id.toString().substring(0, 8), "alice_" + user1Id.toString().substring(0, 8) + "@example.com");
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, first_name, last_name) VALUES (?, ?, ?, 'hash', 'Bob', 'Buyer')",
                user2Id, "bob_" + user2Id.toString().substring(0, 8), "bob_" + user2Id.toString().substring(0, 8) + "@example.com");

        // Insert Memberships
        jdbcTemplate.update("INSERT INTO tenant_memberships (id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                memberA1Id, tenantAId, user1Id);
        jdbcTemplate.update("INSERT INTO tenant_memberships (id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                memberB2Id, tenantBId, user2Id);

        // Insert Roles for Tenant A and Tenant B
        jdbcTemplate.update("INSERT INTO roles (id, tenant_id, code, name, is_system, is_active) VALUES (?, ?, 'OWNER', 'Owner', true, true)",
                roleAOwnerId, tenantAId);
        jdbcTemplate.update("INSERT INTO roles (id, tenant_id, code, name, is_system, is_active) VALUES (?, ?, 'MANAGER', 'Store Manager', true, true)",
                roleAManagerId, tenantAId);
        jdbcTemplate.update("INSERT INTO roles (id, tenant_id, code, name, is_system, is_active) VALUES (?, ?, 'OWNER', 'Owner', true, true)",
                roleBOwnerId, tenantBId);
    }

    // =========================================================================
    // 1. Schema Structure & Constraint Tests
    // =========================================================================

    @Test
    @DisplayName("Verify RBAC tables exist with expected schema")
    void shouldVerifyRbacTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('permissions', 'roles', 'role_permissions', 'user_role_assignments')",
                String.class
        );
        assertThat(tables).containsExactlyInAnyOrder("permissions", "roles", "role_permissions", "user_role_assignments");
    }

    @Test
    @DisplayName("Verify permission code format check constraint rejects invalid dot notation")
    void shouldRejectInvalidPermissionCodeFormat() {
        UUID invalidId = UUID.randomUUID();
        // Violates chk_permissions_code_format: code does not equal module.resource.action
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO permissions (id, code, module, resource, action, description) VALUES (?, 'invalid.code', 'catalog', 'products', 'read', 'desc')",
                invalidId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify user_role_assignments scope check constraints")
    void shouldEnforceScopeTypeAndBranchCheckConstraints() {
        UUID assignmentId = UUID.randomUUID();

        // 1. Invalid scope_type (e.g. WAREHOUSE or GLOBAL)
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'WAREHOUSE', true)",
                assignmentId, tenantAId, memberA1Id, roleAOwnerId
        )).isInstanceOf(DataIntegrityViolationException.class);

        // 2. TENANT scope with non-null branch_id must fail
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, is_active) VALUES (?, ?, ?, ?, 'TENANT', ?, true)",
                assignmentId, tenantAId, memberA1Id, roleAOwnerId, branchAId
        )).isInstanceOf(DataIntegrityViolationException.class);

        // 3. BRANCH scope with null branch_id must fail
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, is_active) VALUES (?, ?, ?, ?, 'BRANCH', NULL, true)",
                assignmentId, tenantAId, memberA1Id, roleAManagerId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 2. Tenant Isolation & Composite Foreign Key Tests
    // =========================================================================

    @Test
    @DisplayName("Verify composite FK blocks cross-tenant membership assignment")
    void shouldBlockCrossTenantMembershipAssignment() {
        UUID assignmentId = UUID.randomUUID();
        // Attempt to assign memberB2 (from Tenant B) inside Tenant A's assignment
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'TENANT', true)",
                assignmentId, tenantAId, memberB2Id, roleAOwnerId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify composite FK blocks cross-tenant role assignment")
    void shouldBlockCrossTenantRoleAssignment() {
        UUID assignmentId = UUID.randomUUID();
        // Attempt to assign roleBOwner (from Tenant B) inside Tenant A's assignment
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'TENANT', true)",
                assignmentId, tenantAId, memberA1Id, roleBOwnerId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify composite FK blocks cross-tenant branch assignment")
    void shouldBlockCrossTenantBranchAssignment() {
        UUID assignmentId = UUID.randomUUID();
        // Attempt to assign branchB (from Tenant B) inside Tenant A's branch-scoped assignment
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, is_active) VALUES (?, ?, ?, ?, 'BRANCH', ?, true)",
                assignmentId, tenantAId, memberA1Id, roleAManagerId, branchBId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify composite FK blocks cross-tenant role_permissions association")
    void shouldBlockCrossTenantRolePermissionsAssociation() {
        UUID rpId = UUID.randomUUID();
        UUID permId = UUID.fromString("00000000-0000-4000-a000-000000000001");
        // Attempt to link roleBOwner (from Tenant B) using tenantAId in role_permissions
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO role_permissions (id, tenant_id, role_id, permission_id) VALUES (?, ?, ?, ?)",
                rpId, tenantAId, roleBOwnerId, permId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify role_permissions rejects non-existent permission reference")
    void shouldRejectNonExistentPermissionInRolePermissions() {
        UUID rpId = UUID.randomUUID();
        UUID nonExistentPermId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO role_permissions (id, tenant_id, role_id, permission_id) VALUES (?, ?, ?, ?)",
                rpId, tenantAId, roleAOwnerId, nonExistentPermId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 3. Assignment Uniqueness & Historical Assignments
    // =========================================================================

    @Test
    @DisplayName("Verify partial unique index prevents duplicate active TENANT role assignments")
    void shouldPreventDuplicateActiveTenantAssignments() {
        UUID assign1 = UUID.randomUUID();
        UUID assign2 = UUID.randomUUID();

        // 1. Initial active TENANT assignment succeeds
        jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'TENANT', true)",
                assign1, tenantAId, memberA1Id, roleAOwnerId
        );

        // 2. Duplicate active assignment must be rejected by uq_assignments_active_tenant
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'TENANT', true)",
                assign2, tenantAId, memberA1Id, roleAOwnerId
        )).isInstanceOf(DataIntegrityViolationException.class);

        // 3. Deactivate assign1 (historical assignment)
        jdbcTemplate.update("UPDATE user_role_assignments SET is_active = false, revoked_at = CURRENT_TIMESTAMP WHERE id = ?", assign1);

        // 4. Now a new active assignment can be created
        int inserted = jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'TENANT', true)",
                assign2, tenantAId, memberA1Id, roleAOwnerId
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("Verify partial unique index prevents duplicate active BRANCH assignments on same branch")
    void shouldPreventDuplicateActiveBranchAssignmentsOnSameBranch() {
        UUID assign1 = UUID.randomUUID();
        UUID assign2 = UUID.randomUUID();
        UUID assignOtherBranch = UUID.randomUUID();
        UUID branchA2Id = UUID.randomUUID();

        // Create second branch in Tenant A
        jdbcTemplate.update("INSERT INTO branches (id, tenant_id, name, code) VALUES (?, ?, 'Second Branch A', 'SECOND-A')",
                branchA2Id, tenantAId);

        // 1. Initial active BRANCH assignment on Branch A succeeds
        jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, is_active) VALUES (?, ?, ?, ?, 'BRANCH', ?, true)",
                assign1, tenantAId, memberA1Id, roleAManagerId, branchAId
        );

        // 2. Duplicate active assignment on SAME Branch A fails
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, is_active) VALUES (?, ?, ?, ?, 'BRANCH', ?, true)",
                assign2, tenantAId, memberA1Id, roleAManagerId, branchAId
        )).isInstanceOf(DataIntegrityViolationException.class);

        // 3. Assignment for SAME member and role on a DIFFERENT branch (Branch A2) SUCCEEDS
        int insertedOther = jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, branch_id, is_active) VALUES (?, ?, ?, ?, 'BRANCH', ?, true)",
                assignOtherBranch, tenantAId, memberA1Id, roleAManagerId, branchA2Id
        );
        assertThat(insertedOther).isEqualTo(1);
    }

    // =========================================================================
    // 4. Permission Catalog Tests
    // =========================================================================

    @Test
    @DisplayName("Verify all 33 canonical permissions exist and have valid structure")
    void shouldVerifyAll33CanonicalPermissionsExist() {
        List<Map<String, Object>> permissions = jdbcTemplate.queryForList(
                "SELECT code, module, resource, action FROM permissions ORDER BY code"
        );
        assertThat(permissions).hasSize(33);

        for (Map<String, Object> perm : permissions) {
            String code = (String) perm.get("code");
            String module = (String) perm.get("module");
            String resource = (String) perm.get("resource");
            String action = (String) perm.get("action");

            assertThat(code).isEqualTo(module + "." + resource + "." + action);
            assertThat(code).matches("^[a-z_]+\\.[a-z_]+\\.[a-z_]+$");
        }
    }

    @Test
    @DisplayName("Verify duplicate permission code cannot be inserted")
    void shouldRejectDuplicatePermissionCode() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO permissions (id, code, module, resource, action, description) VALUES (?, 'catalog.products.read', 'catalog', 'products', 'read', 'duplicate')",
                UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 5. System Role Seeding & Permission Matrix Tests
    // =========================================================================

    @Test
    @DisplayName("Verify system roles and permission matrix seeding for tenant")
    void shouldVerifySystemRoleSeedingAndPermissionMatrix() {
        // Run seeding logic for Tenant A
        jdbcTemplate.update(
                "INSERT INTO roles (id, tenant_id, code, name, description, is_system, is_active, version) " +
                        "VALUES (?, ?, 'ADMIN', 'Administrator', 'Store Administrator', true, true, 0) " +
                        "ON CONFLICT (tenant_id, code) DO NOTHING",
                UUID.randomUUID(), tenantAId
        );

        // Link all permissions to OWNER
        jdbcTemplate.update(
                "INSERT INTO role_permissions (id, tenant_id, role_id, permission_id) " +
                        "SELECT gen_random_uuid(), ?, ?, p.id FROM permissions p " +
                        "ON CONFLICT (role_id, permission_id) DO NOTHING",
                tenantAId, roleAOwnerId
        );

        // Verify OWNER has all 33 permissions
        Integer ownerPermCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE role_id = ?",
                Integer.class,
                roleAOwnerId
        );
        assertThat(ownerPermCount).isEqualTo(33);

        // Create a custom role in Tenant A
        UUID customRoleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO roles (id, tenant_id, code, name, is_system, is_active) VALUES (?, ?, 'CUSTOM_LEAD', 'Custom Lead', false, true)",
                customRoleId, tenantAId
        );

        // Verify custom role has 0 permissions initially (does not receive future permissions automatically)
        Integer customPermCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE role_id = ?",
                Integer.class,
                customRoleId
        );
        assertThat(customPermCount).isEqualTo(0);
    }

    // =========================================================================
    // 6. Existing Tenant Bootstrap Detection & Invariant Verification
    // =========================================================================

    @Test
    @DisplayName("Verify query accurately detects tenants lacking an active OWNER assignment")
    void shouldDetectTenantsWithoutActiveOwnerAssignment() {
        // Tenant A has Member A1, but NO user_role_assignment has been assigned yet
        // Tenant B has Member B2, and we assign OWNER to Member B2
        jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'TENANT', true)",
                UUID.randomUUID(), tenantBId, memberB2Id, roleBOwnerId
        );

        // Query identifying un-bootstrapped tenants
        List<UUID> unbootstrappedTenants = jdbcTemplate.queryForList(
                "SELECT t.id FROM tenants t " +
                        "WHERE t.status = 'ACTIVE' " +
                        "  AND NOT EXISTS ( " +
                        "      SELECT 1 FROM tenant_memberships m " +
                        "      JOIN user_role_assignments a ON a.tenant_id = m.tenant_id AND a.membership_id = m.id " +
                        "      JOIN roles r ON r.tenant_id = a.tenant_id AND r.id = a.role_id " +
                        "      WHERE m.tenant_id = t.id " +
                        "        AND m.status = 'ACTIVE' " +
                        "        AND r.code = 'OWNER' " +
                        "        AND a.is_active = true " +
                        "  )",
                UUID.class
        );

        assertThat(unbootstrappedTenants).contains(tenantAId);
        assertThat(unbootstrappedTenants).doesNotContain(tenantBId);
    }

    @Test
    @DisplayName("Verify existing M01-M03 tenant data is preserved during migration and properly seeded")
    void shouldVerifyExistingTenantUpgradeAndBootstrap() {
        UUID legacyTenantId = UUID.randomUUID();
        UUID legacyBranchId = UUID.randomUUID();
        UUID legacyUserId = UUID.randomUUID();
        UUID legacyMemberId = UUID.randomUUID();

        // 1. Simulate existing M01-M03 tenant, branch, user, and membership
        jdbcTemplate.update("INSERT INTO tenants (id, name, code, currency_code, timezone, status) VALUES (?, 'Legacy Store', ?, 'USD', 'UTC', 'ACTIVE')",
                legacyTenantId, "legacy-" + legacyTenantId.toString().substring(0, 8));
        jdbcTemplate.update("INSERT INTO branches (id, tenant_id, name, code) VALUES (?, ?, 'Legacy Main Branch', 'LEGACY-01')",
                legacyBranchId, legacyTenantId);
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, first_name, last_name) VALUES (?, ?, ?, 'hash', 'Legacy', 'Founder')",
                legacyUserId, "founder_" + legacyUserId.toString().substring(0, 8), "founder_" + legacyUserId.toString().substring(0, 8) + "@example.com");
        jdbcTemplate.update("INSERT INTO tenant_memberships (id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                legacyMemberId, legacyTenantId, legacyUserId);

        // 2. Execute changeset 14 seed SQL for the legacy tenant
        jdbcTemplate.execute(
                "INSERT INTO roles (id, tenant_id, code, name, description, is_system, is_active, created_at, updated_at, version) " +
                "SELECT gen_random_uuid(), t.id, 'OWNER', 'Owner', 'Store Owner / Founder with unrestricted administrative access', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0 " +
                "FROM tenants t WHERE t.id = '" + legacyTenantId + "' AND NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.code = 'OWNER');"
        );
        for (String roleCode : List.of("ADMIN", "MANAGER", "CASHIER", "WAREHOUSE_MANAGER", "ACCOUNTANT", "VIEWER")) {
            jdbcTemplate.execute(
                    "INSERT INTO roles (id, tenant_id, code, name, description, is_system, is_active, created_at, updated_at, version) " +
                    "SELECT gen_random_uuid(), t.id, '" + roleCode + "', '" + roleCode + "', 'System role', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0 " +
                    "FROM tenants t WHERE t.id = '" + legacyTenantId + "' AND NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.code = '" + roleCode + "');"
            );
        }

        // 3. Verify all 7 system roles were created for legacy tenant
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND is_system = true",
                Integer.class,
                legacyTenantId
        );
        assertThat(roleCount).isEqualTo(7);

        // 4. Seed role_permissions for OWNER
        jdbcTemplate.execute(
                "INSERT INTO role_permissions (id, tenant_id, role_id, permission_id, created_at) " +
                "SELECT gen_random_uuid(), r.tenant_id, r.id, p.id, CURRENT_TIMESTAMP " +
                "FROM roles r CROSS JOIN permissions p " +
                "WHERE r.tenant_id = '" + legacyTenantId + "' AND r.code = 'OWNER' " +
                "  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);"
        );

        UUID legacyOwnerRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE tenant_id = ? AND code = 'OWNER'",
                UUID.class,
                legacyTenantId
        );
        Integer permCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE role_id = ?",
                Integer.class,
                legacyOwnerRoleId
        );
        assertThat(permCount).isEqualTo(33);

        // 5. Existing tenant data remains 100% intact
        String branchName = jdbcTemplate.queryForObject("SELECT name FROM branches WHERE id = ?", String.class, legacyBranchId);
        assertThat(branchName).isEqualTo("Legacy Main Branch");

        // 6. Verify legacy tenant is detected as lacking active OWNER until explicitly bootstrapped
        Integer isUnbootstrapped = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants t WHERE t.id = ? AND NOT EXISTS ( " +
                "  SELECT 1 FROM tenant_memberships m " +
                "  JOIN user_role_assignments a ON a.tenant_id = m.tenant_id AND a.membership_id = m.id " +
                "  JOIN roles r ON r.tenant_id = a.tenant_id AND r.id = a.role_id " +
                "  WHERE m.tenant_id = t.id AND m.status = 'ACTIVE' AND r.code = 'OWNER' AND a.is_active = true" +
                ")",
                Integer.class,
                legacyTenantId
        );
        assertThat(isUnbootstrapped).isEqualTo(1);

        // 7. Perform explicit owner bootstrap for legacy membership
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_role_assignments (id, tenant_id, membership_id, role_id, scope_type, is_active) VALUES (?, ?, ?, ?, 'TENANT', true)",
                assignmentId, legacyTenantId, legacyMemberId, legacyOwnerRoleId
        );

        // 8. Now legacy tenant is fully bootstrapped
        Integer remainingUnbootstrapped = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants t WHERE t.id = ? AND NOT EXISTS ( " +
                "  SELECT 1 FROM tenant_memberships m " +
                "  JOIN user_role_assignments a ON a.tenant_id = m.tenant_id AND a.membership_id = m.id " +
                "  JOIN roles r ON r.tenant_id = a.tenant_id AND r.id = a.role_id " +
                "  WHERE m.tenant_id = t.id AND m.status = 'ACTIVE' AND r.code = 'OWNER' AND a.is_active = true" +
                ")",
                Integer.class,
                legacyTenantId
        );
        assertThat(remainingUnbootstrapped).isEqualTo(0);
    }
}
