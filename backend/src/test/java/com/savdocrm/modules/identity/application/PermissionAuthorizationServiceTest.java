package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.Permissions;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RolePermission;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.PermissionRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RolePermissionRepository;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PermissionAuthorizationServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_auth_perm_test")
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
    private PermissionAuthorizationService permissionAuthorizationService;

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
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private Tenant testTenant;
    private Branch branchA;
    private Branch branchB;
    private User testUser;
    private TenantMembership testMembership;

    @BeforeEach
    void setUp() {
        testTenant = tenantRepository.save(Tenant.builder()
                .name("Perm Test Store")
                .code("PERM_TEST_" + UUID.randomUUID().toString().substring(0, 8))
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

        Branch bB = Branch.builder()
                .name("Branch Beta")
                .code("BETA")
                .isMain(false)
                .isActive(true)
                .build();
        bB.setTenantId(testTenant.getId());
        branchB = branchRepository.save(bB);

        String uid = UUID.randomUUID().toString().substring(0, 8);
        testUser = userRepository.save(User.builder()
                .username("user_" + uid)
                .email("user_" + uid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Test")
                .lastName("Perm")
                .status(UserStatus.ACTIVE)
                .build());

        testMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(testUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.seedSystemRolesAndPermissions(testTenant.getId());
        setupSecurityContext(testTenant.getId(), testMembership.getId(), testUser.getId());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
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
        simulateNewRequest();
    }

    private void simulateNewRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("TENANT scope assignment grants permissions across all branches and at tenant level")
    void testTenantScopePermissionGrantedAcrossAllBranches() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        // CASHIER has sales.orders.create
        assertThat(permissionAuthorizationService.hasPermission(Permissions.SALES_ORDERS_CREATE)).isTrue();
        assertThat(permissionAuthorizationService.hasBranchPermission(Permissions.SALES_ORDERS_CREATE, branchA.getId())).isTrue();
        assertThat(permissionAuthorizationService.hasBranchPermission(Permissions.SALES_ORDERS_CREATE, branchB.getId())).isTrue();

        // CASHIER does not have identity.users.manage
        assertThat(permissionAuthorizationService.hasPermission(Permissions.IDENTITY_USERS_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("BRANCH scope assignment grants permissions ONLY to assigned branch")
    void testBranchScopePermissionGrantedOnlyToAssignedBranch() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.BRANCH)
                .branchId(branchA.getId())
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        // Branch A should have permission
        assertThat(permissionAuthorizationService.hasBranchPermission(Permissions.SALES_ORDERS_CREATE, branchA.getId())).isTrue();

        // Branch B should NOT have permission
        assertThat(permissionAuthorizationService.hasBranchPermission(Permissions.SALES_ORDERS_CREATE, branchB.getId())).isFalse();

        // Tenant-level check (without branchId) should NOT have permission
        assertThat(permissionAuthorizationService.hasPermission(Permissions.SALES_ORDERS_CREATE)).isFalse();
    }

    @Test
    @DisplayName("Fail-Closed: suspended user denies all permissions")
    void testLiveEntityFailClosed_UserSuspended() {
        Role ownerRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isTrue();

        // Suspend user
        testUser.suspend();
        userRepository.save(testUser);

        simulateNewRequest();
        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isFalse();
    }

    @Test
    @DisplayName("Fail-Closed: suspended membership denies all permissions")
    void testLiveEntityFailClosed_MembershipSuspended() {
        Role ownerRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isTrue();

        // Suspend membership
        testMembership.suspend();
        tenantMembershipRepository.save(testMembership);

        simulateNewRequest();
        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isFalse();
    }

    @Test
    @DisplayName("Fail-Closed: suspended tenant denies all permissions")
    void testLiveEntityFailClosed_TenantSuspended() {
        Role ownerRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isTrue();

        // Suspend tenant
        testTenant.suspend();
        tenantRepository.save(testTenant);

        simulateNewRequest();
        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isFalse();
    }

    @Test
    @DisplayName("Fail-Closed: deactivated role denies permissions")
    void testLiveEntityFailClosed_RoleDeactivated() {
        // Create custom role
        Role customRole = Role.builder()
                .code("CUSTOM_READER")
                .name("Custom Reader")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(testTenant.getId());
        customRole = roleRepository.save(customRole);

        var perm = permissionRepository.findByCode(Permissions.CATALOG_PRODUCTS_READ).orElseThrow();
        RolePermission rp = RolePermission.builder()
                .tenantId(testTenant.getId())
                .roleId(customRole.getId())
                .permissionId(perm.getId())
                .build();
        rolePermissionRepository.save(rp);

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(customRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isTrue();

        // Deactivate custom role
        customRole.deactivate();
        roleRepository.save(customRole);

        simulateNewRequest();
        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isFalse();
    }

    @Test
    @DisplayName("Fail-Closed: revoked assignment denies permissions")
    void testLiveEntityFailClosed_AssignmentRevoked() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        assignment = userRoleAssignmentRepository.save(assignment);

        assertThat(permissionAuthorizationService.hasPermission(Permissions.SALES_ORDERS_CREATE)).isTrue();

        assignment.revoke();
        userRoleAssignmentRepository.save(assignment);

        simulateNewRequest();
        assertThat(permissionAuthorizationService.hasPermission(Permissions.SALES_ORDERS_CREATE)).isFalse();
    }

    @Test
    @DisplayName("Additive Multi-Role: permissions aggregate across active assignments")
    void testAdditiveMultiRolePermissions() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();
        Role accountantRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.ACCOUNTANT).orElseThrow();

        UserRoleAssignment assign1 = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assign1.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assign1);

        UserRoleAssignment assign2 = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(accountantRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assign2.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assign2);

        // From Cashier
        assertThat(permissionAuthorizationService.hasPermission(Permissions.SALES_ORDERS_CREATE)).isTrue();
        // From Accountant
        assertThat(permissionAuthorizationService.hasPermission(Permissions.FINANCE_ACCOUNTS_READ)).isTrue();
    }

    @Test
    @DisplayName("Missing or mismatched tenant context fails closed")
    void testMissingOrMismatchedTenantContextFailsClosed() {
        Role ownerRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        // Clear tenant context
        TenantContextHolder.clear();
        simulateNewRequest();
        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isFalse();

        // Mismatched tenant context
        TenantContextHolder.setContext(TenantContext.of(UUID.randomUUID()));
        simulateNewRequest();
        assertThat(permissionAuthorizationService.hasPermission(Permissions.CATALOG_PRODUCTS_READ)).isFalse();
    }

    @Test
    @DisplayName("Request-scoped memoization resolves permissions within web request")
    void testRequestScopedMemoization() {
        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(testMembership.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignment.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(assignment);

        // Simulate incoming HTTP request
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            boolean hasPerm1 = permissionAuthorizationService.hasPermission(Permissions.SALES_ORDERS_CREATE);
            boolean hasPerm2 = permissionAuthorizationService.hasPermission(Permissions.SALES_ORDERS_CREATE);
            assertThat(hasPerm1).isTrue();
            assertThat(hasPerm2).isTrue();

            Set<String> effective = permissionAuthorizationService.getEffectiveTenantPermissions(testTenant.getId(), testMembership.getId());
            assertThat(effective).contains(Permissions.SALES_ORDERS_CREATE);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
