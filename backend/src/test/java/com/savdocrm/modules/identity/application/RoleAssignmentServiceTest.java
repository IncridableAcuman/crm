package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.application.dto.AssignRoleCommand;
import com.savdocrm.modules.identity.application.dto.UserRoleAssignmentDto;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.domain.exception.CrossTenantAssignmentException;
import com.savdocrm.modules.identity.domain.exception.DuplicateRoleAssignmentException;
import com.savdocrm.modules.identity.domain.exception.PermissionDeniedException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Branch;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class RoleAssignmentServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_role_assignment_test")
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
        registry.add("jwt.secret", () -> "testSecretKeyForJwtAuthenticationIntegrationTestingSavdoCrm1234567890!");
    }

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private TenantBootstrapService tenantBootstrapService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantMembershipRepository tenantMembershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private Tenant testTenant;
    private Branch branchA;
    private User ownerUser;
    private TenantMembership ownerMembership;
    private User targetUser;
    private TenantMembership targetMembership;

    @BeforeEach
    void setUp() {
        testTenant = tenantRepository.save(Tenant.builder()
                .name("Assignment Store")
                .code("ASSIGN_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        Branch bA = Branch.builder()
                .name("Branch Alpha")
                .code("ALPHA")
                .isMain(true)
                .isActive(true)
                .build();
        bA.setTenantId(testTenant.getId());
        branchA = branchRepository.save(bA);

        String ownerUid = UUID.randomUUID().toString().substring(0, 8);
        ownerUser = userRepository.save(User.builder()
                .username("owner_" + ownerUid)
                .email("owner_" + ownerUid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Owner")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build());

        ownerMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(ownerUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        String targetUid = UUID.randomUUID().toString().substring(0, 8);
        targetUser = userRepository.save(User.builder()
                .username("target_" + targetUid)
                .email("target_" + targetUid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Target")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build());

        targetMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(targetUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(testTenant.getId(), ownerMembership.getId());
        setupSecurityContext(testTenant.getId(), ownerMembership.getId(), ownerUser.getId());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private void setupSecurityContext(UUID tenantId, UUID membershipId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .email("test@example.com")
                .password("")
                .activeTenantId(tenantId)
                .activeMembershipId(membershipId)
                .active(true)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContextHolder.setContext(TenantContext.of(tenantId));
    }

    @Test
    @DisplayName("Grant-What-You-Hold: non-owner actor cannot assign roles with permissions they lack")
    void testGrantWhatYouHold_ActorCannotGrantPermissionsTheyLack() {
        String cashierUid = UUID.randomUUID().toString().substring(0, 8);
        User cashierUser = userRepository.save(User.builder()
                .username("cashier_" + cashierUid)
                .email("cashier_" + cashierUid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Cashier")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership cashierMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(cashierUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();
        Role managerRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.MANAGER).orElseThrow();

        // Assign CASHIER to cashierMembership
        UserRoleAssignment cashierAssign = UserRoleAssignment.builder()
                .membershipId(cashierMembership.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        cashierAssign.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(cashierAssign);

        setupSecurityContext(testTenant.getId(), cashierMembership.getId(), cashierUser.getId());

        // Cashier attempts to assign MANAGER to targetMembership -> must be rejected
        AssignRoleCommand cmd = new AssignRoleCommand(
                testTenant.getId(),
                targetMembership.getId(),
                managerRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        assertThatThrownBy(() -> roleAssignmentService.assignRole(cmd, cashierUser.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("Owner Guard: only an active eligible OWNER can assign the OWNER role")
    void testOwnerGuard_OnlyOwnerCanAssignOwner() {
        String adminUid = UUID.randomUUID().toString().substring(0, 8);
        User adminUser = userRepository.save(User.builder()
                .username("admin_" + adminUid)
                .email("admin_" + adminUid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Admin")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership adminMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(adminUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role adminRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.ADMIN).orElseThrow();
        Role ownerRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.OWNER).orElseThrow();

        // Assign ADMIN to adminMembership
        UserRoleAssignment adminAssign = UserRoleAssignment.builder()
                .membershipId(adminMembership.getId())
                .roleId(adminRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        adminAssign.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(adminAssign);

        setupSecurityContext(testTenant.getId(), adminMembership.getId(), adminUser.getId());

        // ADMIN attempts to assign OWNER to targetMembership -> must be rejected
        AssignRoleCommand cmd = new AssignRoleCommand(
                testTenant.getId(),
                targetMembership.getId(),
                ownerRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        assertThatThrownBy(() -> roleAssignmentService.assignRole(cmd, adminUser.getId()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Only an eligible active OWNER can assign the OWNER role");

        // Now test that OWNER CAN assign OWNER
        setupSecurityContext(testTenant.getId(), ownerMembership.getId(), ownerUser.getId());
        UserRoleAssignmentDto dto = roleAssignmentService.assignRole(cmd, ownerUser.getId());
        assertThat(dto).isNotNull();
        assertThat(dto.roleCode()).isEqualTo(SystemRoles.OWNER);
    }

    @Test
    @DisplayName("Self-Elevation prevention: actor cannot assign roles to themselves")
    void testSelfElevationBlocked() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();

        AssignRoleCommand cmd = new AssignRoleCommand(
                testTenant.getId(),
                ownerMembership.getId(),
                cashierRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        assertThatThrownBy(() -> roleAssignmentService.assignRole(cmd, ownerUser.getId()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Self-assignment or self-elevation is strictly prohibited");
    }

    @Test
    @DisplayName("Duplicate active assignment is blocked")
    void testDuplicateAssignmentBlocked() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();

        AssignRoleCommand cmd = new AssignRoleCommand(
                testTenant.getId(),
                targetMembership.getId(),
                cashierRole.getId(),
                RoleScopeType.BRANCH,
                branchA.getId()
        );

        // First assignment succeeds
        UserRoleAssignmentDto first = roleAssignmentService.assignRole(cmd, ownerUser.getId());
        assertThat(first).isNotNull();

        // Second assignment with same role and branch fails
        assertThatThrownBy(() -> roleAssignmentService.assignRole(cmd, ownerUser.getId()))
                .isInstanceOf(DuplicateRoleAssignmentException.class);
    }

    @Test
    @DisplayName("Revocation preserves historical audit record (isActive=false, revokedAt!=null)")
    void testHistoricalAuditPreservedOnRevocation() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();

        AssignRoleCommand cmd = new AssignRoleCommand(
                testTenant.getId(),
                targetMembership.getId(),
                cashierRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        UserRoleAssignmentDto assigned = roleAssignmentService.assignRole(cmd, ownerUser.getId());
        UUID assignmentId = assigned.id();

        roleAssignmentService.revokeRoleAssignment(testTenant.getId(), assignmentId, ownerUser.getId());

        // Verify the record is preserved in database
        UserRoleAssignment revoked = userRoleAssignmentRepository.findById(assignmentId).orElseThrow();
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Cross-tenant assignment is rejected")
    void testCrossTenantAssignmentRejected() {
        Tenant tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant B")
                .code("TENANT_B_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        tenantBootstrapService.seedSystemRolesAndPermissions(tenantB.getId());
        Role roleB = roleRepository.findByTenantIdAndCode(tenantB.getId(), SystemRoles.CASHIER).orElseThrow();

        // Try assigning role from tenant B to target membership in testTenant
        AssignRoleCommand cmd = new AssignRoleCommand(
                testTenant.getId(),
                targetMembership.getId(),
                roleB.getId(),
                RoleScopeType.TENANT,
                null
        );

        assertThatThrownBy(() -> roleAssignmentService.assignRole(cmd, ownerUser.getId()))
                .isInstanceOf(CrossTenantAssignmentException.class);
    }
}
