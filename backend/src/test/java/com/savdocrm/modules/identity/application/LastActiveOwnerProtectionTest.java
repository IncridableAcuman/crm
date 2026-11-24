package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.domain.exception.LastActiveOwnerException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
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
class LastActiveOwnerProtectionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_owner_protection_test")
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
    private UserService userService;

    @Autowired
    private TenantBootstrapService tenantBootstrapService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantMembershipRepository tenantMembershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private Tenant tenantA;
    private User user1;
    private TenantMembership membershipA1;
    private UserRoleAssignment ownerAssignA1;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.save(Tenant.builder()
                .name("Tenant A")
                .code("T_A_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        String uid1 = UUID.randomUUID().toString().substring(0, 8);
        user1 = userRepository.save(User.builder()
                .username("user1_" + uid1)
                .email("user1_" + uid1 + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("User")
                .lastName("One")
                .status(UserStatus.ACTIVE)
                .build());

        membershipA1 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(user1.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(tenantA.getId(), membershipA1.getId());

        ownerAssignA1 = userRoleAssignmentRepository
                .findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenantA.getId(), membershipA1.getId())
                .stream()
                .filter(a -> a.getScopeType() == RoleScopeType.TENANT)
                .findFirst()
                .orElseThrow();

        setupSecurityContext(tenantA.getId(), membershipA1.getId(), user1.getId());
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
    @DisplayName("Cannot revoke last eligible active OWNER assignment")
    void testCannotRevokeLastEligibleActiveOwner() {
        // Attempting to revoke the sole OWNER assignment in tenantA throws LastActiveOwnerException
        assertThatThrownBy(() -> roleAssignmentService.revokeRoleAssignment(tenantA.getId(), ownerAssignA1.getId(), user1.getId()))
                .isInstanceOf(LastActiveOwnerException.class);

        // Verify assignment remains active
        UserRoleAssignment check = userRoleAssignmentRepository.findById(ownerAssignA1.getId()).orElseThrow();
        assertThat(check.isActive()).isTrue();
    }

    @Test
    @DisplayName("Can revoke an OWNER assignment when another eligible active OWNER remains")
    void testCanRevokeOwnerWhenAnotherOwnerExists() {
        // Add a second eligible OWNER in tenantA
        String uid2 = UUID.randomUUID().toString().substring(0, 8);
        User user2 = userRepository.save(User.builder()
                .username("user2_" + uid2)
                .email("user2_" + uid2 + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("User")
                .lastName("Two")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership membershipA2 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(user2.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role ownerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment ownerAssignA2 = UserRoleAssignment.builder()
                .membershipId(membershipA2.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        ownerAssignA2.setTenantId(tenantA.getId());
        ownerAssignA2 = userRoleAssignmentRepository.save(ownerAssignA2);

        // Verify there are now 2 eligible owners
        assertThat(userRoleAssignmentRepository.countEligibleOwners(tenantA.getId(), null, null, null)).isEqualTo(2);

        // User 1 revokes User 2's OWNER assignment -> should succeed
        roleAssignmentService.revokeRoleAssignment(tenantA.getId(), ownerAssignA2.getId(), user1.getId());

        // Verify owner count is now 1
        assertThat(userRoleAssignmentRepository.countEligibleOwners(tenantA.getId(), null, null, null)).isEqualTo(1);
    }

    @Test
    @DisplayName("Suspended global user is not counted as eligible OWNER")
    void testSuspendedUserNotCountedAsEligibleOwner() {
        // Add a second user as OWNER, but suspend them globally
        String uid2 = UUID.randomUUID().toString().substring(0, 8);
        User user2 = userRepository.save(User.builder()
                .username("user2_" + uid2)
                .email("user2_" + uid2 + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("User")
                .lastName("Two")
                .status(UserStatus.SUSPENDED)
                .build());

        TenantMembership membershipA2 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(user2.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role ownerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment ownerAssignA2 = UserRoleAssignment.builder()
                .membershipId(membershipA2.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        ownerAssignA2.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(ownerAssignA2);

        // Even though 2 assignments exist, only 1 is eligible because user2 is SUSPENDED
        assertThat(userRoleAssignmentRepository.countEligibleOwners(tenantA.getId(), null, null, null)).isEqualTo(1);

        // Revoking User 1's assignment must fail
        assertThatThrownBy(() -> roleAssignmentService.revokeRoleAssignment(tenantA.getId(), ownerAssignA1.getId(), user1.getId()))
                .isInstanceOf(LastActiveOwnerException.class);
    }

    @Test
    @DisplayName("Multi-tenant user suspension rolls back if ANY active tenant loses its final eligible OWNER")
    void testMultiTenantGlobalUserSuspension_RollsBackIfAnyTenantLosesFinalOwner() {
        // Create Tenant B where user1 is the SOLE OWNER
        Tenant tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant B")
                .code("T_B_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        TenantMembership membershipB1 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantB.getId())
                .userId(user1.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(tenantB.getId(), membershipB1.getId());

        // In Tenant A, add a second owner (user2) so Tenant A has 2 owners
        String uid2 = UUID.randomUUID().toString().substring(0, 8);
        User user2 = userRepository.save(User.builder()
                .username("user2_" + uid2)
                .email("user2_" + uid2 + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("User")
                .lastName("Two")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership membershipA2 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(user2.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role ownerRoleA = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment ownerAssignA2 = UserRoleAssignment.builder()
                .membershipId(membershipA2.getId())
                .roleId(ownerRoleA.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        ownerAssignA2.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(ownerAssignA2);

        // Attempt to suspend user1 globally -> Tenant B has no other owner -> must throw LastActiveOwnerException
        assertThatThrownBy(() -> userService.suspendGlobalUser(user1.getId()))
                .isInstanceOf(LastActiveOwnerException.class);

        // Verify user1 remains ACTIVE due to transaction rollback
        User user1Reloaded = userRepository.findById(user1.getId()).orElseThrow();
        assertThat(user1Reloaded.isActive()).isTrue();
    }

    @Test
    @DisplayName("Multi-tenant user suspension succeeds when all affected tenants retain other eligible owners")
    void testMultiTenantGlobalUserSuspension_SucceedsIfAllTenantsRetainOtherOwners() {
        // Tenant A: Add user2 as second owner
        String uid2 = UUID.randomUUID().toString().substring(0, 8);
        User user2 = userRepository.save(User.builder()
                .username("user2_" + uid2)
                .email("user2_" + uid2 + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("User")
                .lastName("Two")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership membershipA2 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(user2.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role ownerRoleA = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment assignA2 = UserRoleAssignment.builder()
                .membershipId(membershipA2.getId())
                .roleId(ownerRoleA.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignA2.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(assignA2);

        // Tenant B: user1 and user3 are both owners
        Tenant tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant B")
                .code("T_B_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        TenantMembership membershipB1 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantB.getId())
                .userId(user1.getId())
                .status(MembershipStatus.ACTIVE)
                .build());
        tenantBootstrapService.initializeNewTenantRbac(tenantB.getId(), membershipB1.getId());

        String uid3 = UUID.randomUUID().toString().substring(0, 8);
        User user3 = userRepository.save(User.builder()
                .username("user3_" + uid3)
                .email("user3_" + uid3 + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("User")
                .lastName("Three")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership membershipB3 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantB.getId())
                .userId(user3.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role ownerRoleB = roleRepository.findByTenantIdAndCode(tenantB.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment assignB3 = UserRoleAssignment.builder()
                .membershipId(membershipB3.getId())
                .roleId(ownerRoleB.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assignB3.setTenantId(tenantB.getId());
        userRoleAssignmentRepository.save(assignB3);

        // Suspend user1 -> both tenants retain another owner -> must succeed
        userService.suspendGlobalUser(user1.getId());

        User user1Reloaded = userRepository.findById(user1.getId()).orElseThrow();
        assertThat(user1Reloaded.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Suspending tenant membership rejected if last eligible OWNER")
    void testSuspendTenantMembership_RejectsIfLastActiveOwner() {
        assertThatThrownBy(() -> userService.suspendTenantMembership(tenantA.getId(), membershipA1.getId(), user1.getId()))
                .isInstanceOf(LastActiveOwnerException.class);

        TenantMembership mem = tenantMembershipRepository.findById(membershipA1.getId()).orElseThrow();
        assertThat(mem.isActive()).isTrue();
    }
}
