package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.domain.exception.TenantBootstrapNotAllowedException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TenantBootstrapServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_bootstrap_test")
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
    private AuthService authService;

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

    @Test
    @DisplayName("New tenant registration atomically seeds system roles and assigns OWNER to creator")
    void testNewTenantRegistrationAtomicallyBootstrapsOwner() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = RegisterRequest.builder()
                .email("owner_" + unique + "@example.com")
                .username("owner_" + unique)
                .password("StrongPassword123!")
                .firstName("Store")
                .lastName("Owner")
                .storeName("Bootstrap Store")
                .build();

        AuthResponse response = authService.register(request, "127.0.0.1", "Mozilla/5.0");
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getActiveTenant()).isNotNull();

        UUID tenantId = response.getActiveTenant().getTenantId();
        UUID userId = response.getUser().getId();

        // 1. Verify 7 system roles were created
        List<Role> roles = roleRepository.findAllByTenantId(tenantId);
        assertThat(roles).hasSize(7);
        assertThat(roles.stream().map(Role::getCode))
                .containsExactlyInAnyOrder(
                        SystemRoles.OWNER,
                        SystemRoles.ADMIN,
                        SystemRoles.MANAGER,
                        SystemRoles.CASHIER,
                        SystemRoles.WAREHOUSE_MANAGER,
                        SystemRoles.ACCOUNTANT,
                        SystemRoles.VIEWER
                );

        // 2. Verify creator has an active OWNER assignment at TENANT scope
        long ownerCount = userRoleAssignmentRepository.countEligibleOwners(tenantId, null, null, null);
        assertThat(ownerCount).isEqualTo(1);

        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, userId).orElseThrow();
        List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                .findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenantId, membership.getId());
        assertThat(assignments).hasSize(1);
        UserRoleAssignment ownerAssignment = assignments.get(0);
        assertThat(ownerAssignment.getScopeType()).isEqualTo(RoleScopeType.TENANT);
        assertThat(ownerAssignment.getBranchId()).isNull();
        assertThat(ownerAssignment.isActive()).isTrue();
    }

    @Test
    @DisplayName("Bootstrap existing unbootstrapped tenant succeeds for system executor")
    void testBootstrapExistingTenantOwner_SuccessForUnbootstrappedTenant() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User creator = userRepository.save(User.builder()
                .username("creator_" + uid)
                .email("legacy_creator_" + uid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Legacy")
                .lastName("Creator")
                .status(UserStatus.ACTIVE)
                .build());

        Tenant unbootstrappedTenant = Tenant.builder()
                .name("Legacy Store")
                .code("LEGACY_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build();
        unbootstrappedTenant.setCreatedBy(creator.getId());
        unbootstrappedTenant = tenantRepository.save(unbootstrappedTenant);

        TenantMembership creatorMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(unbootstrappedTenant.getId())
                .userId(creator.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        // Verify it is initially unbootstrapped
        assertThat(tenantBootstrapService.findUnbootstrappedTenants()).contains(unbootstrappedTenant.getId());

        // Execute bootstrap via system context (actorUserId = null)
        TenantBootstrapResult result = tenantBootstrapService.bootstrapExistingTenantOwner(
                unbootstrappedTenant.getId(), creatorMembership.getId(), null);

        assertThat(result).isEqualTo(TenantBootstrapResult.SUCCESS);

        // Verify eligible owner exists now
        long ownerCount = userRoleAssignmentRepository.countEligibleOwners(unbootstrappedTenant.getId(), null, null, null);
        assertThat(ownerCount).isEqualTo(1);

        // No longer in unbootstrapped list
        assertThat(tenantBootstrapService.findUnbootstrappedTenants()).doesNotContain(unbootstrappedTenant.getId());

        // Calling it again returns ALREADY_BOOTSTRAPPED (idempotent)
        TenantBootstrapResult retryResult = tenantBootstrapService.bootstrapExistingTenantOwner(
                unbootstrappedTenant.getId(), creatorMembership.getId(), null);
        assertThat(retryResult).isEqualTo(TenantBootstrapResult.ALREADY_BOOTSTRAPPED);
    }

    @Test
    @DisplayName("Ordinary non-creator user cannot bootstrap or self-elevate to OWNER")
    void testOrdinaryUserCannotBootstrapOwner() {
        String cUid = UUID.randomUUID().toString().substring(0, 8);
        User creator = userRepository.save(User.builder()
                .username("creator_" + cUid)
                .email("orig_creator_" + cUid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Original")
                .lastName("Creator")
                .status(UserStatus.ACTIVE)
                .build());

        Tenant unbootstrappedTenant = Tenant.builder()
                .name("Unbootstrapped Store")
                .code("UNBOOT_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build();
        unbootstrappedTenant.setCreatedBy(creator.getId());
        Tenant savedTenant = tenantRepository.save(unbootstrappedTenant);
        UUID targetTenantId = savedTenant.getId();

        // Attacker is a normal member
        String aUid = UUID.randomUUID().toString().substring(0, 8);
        User attacker = userRepository.save(User.builder()
                .username("attacker_" + aUid)
                .email("attacker_" + aUid + "@example.com")
                .passwordHash("hashed_pw")
                .firstName("Attacker")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership attackerMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(targetTenantId)
                .userId(attacker.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        assertThatThrownBy(() -> tenantBootstrapService.bootstrapExistingTenantOwner(
                targetTenantId, attackerMembership.getId(), attacker.getId()))
                .isInstanceOf(TenantBootstrapNotAllowedException.class)
                .hasMessageContaining("Ordinary tenant users/administrators cannot bootstrap or self-elevate to OWNER");
    }
}
