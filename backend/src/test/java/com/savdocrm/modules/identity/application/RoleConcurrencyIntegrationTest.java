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
import com.savdocrm.modules.identity.domain.exception.RoleNotFoundException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RoleConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_role_concurrency_test")
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
    private RoleService roleService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

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

    private Tenant testTenant;
    private User ownerUser;
    private TenantMembership ownerMembership;
    private User targetUser;
    private TenantMembership targetMembership;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        testTenant = tenantRepository.save(Tenant.builder()
                .name("Concurrency Store " + suffix)
                .code("conc-" + suffix)
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        ownerUser = userRepository.save(User.builder()
                .email("owner." + suffix + "@example.com")
                .username("owner_" + suffix)
                .passwordHash("hashed")
                .firstName("Owner")
                .lastName("Conc")
                .status(UserStatus.ACTIVE)
                .build());

        ownerMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(ownerUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(testTenant.getId(), ownerMembership.getId());

        targetUser = userRepository.save(User.builder()
                .email("target." + suffix + "@example.com")
                .username("target_" + suffix)
                .passwordHash("hashed")
                .firstName("Target")
                .lastName("Conc")
                .status(UserStatus.ACTIVE)
                .build());

        targetMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(targetUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Concurrency: Serialized role deletion and concurrent assignment prevents foreign-key corruption")
    void shouldHandleConcurrentRoleDeletionAndAssignmentDeterministically() throws Exception {
        // Create custom role
        Role customRole = Role.builder()
                .code("CONC_ROLE")
                .name("Concurrent Role")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(testTenant.getId());
        customRole.setCreatedBy(ownerUser.getId());
        customRole = roleRepository.save(customRole);
        final UUID roleId = customRole.getId();

        int iterations = 5;
        for (int i = 0; i < iterations; i++) {
            // Recreate role for each test cycle
            Role currentRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), "CONC_ROLE_" + i).orElse(null);
            if (currentRole == null) {
                currentRole = Role.builder()
                        .code("CONC_ROLE_" + i)
                        .name("Concurrent Role " + i)
                        .isSystem(false)
                        .isActive(true)
                        .build();
                currentRole.setTenantId(testTenant.getId());
                currentRole.setCreatedBy(ownerUser.getId());
                currentRole = roleRepository.save(currentRole);
            }
            final UUID currentRoleId = currentRole.getId();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicReference<Throwable> deleteError = new AtomicReference<>();
            AtomicReference<Throwable> assignError = new AtomicReference<>();

            // Thread 1: Delete or deactivate role
            Future<?> deleteFuture = executor.submit(() -> {
                try {
                    startLatch.await();
                    roleService.deleteOrDeactivateRole(testTenant.getId(), currentRoleId, ownerUser.getId());
                } catch (Throwable t) {
                    deleteError.set(t);
                }
            });

            // Thread 2: Assign role
            Future<?> assignFuture = executor.submit(() -> {
                try {
                    startLatch.await();
                    AssignRoleCommand cmd = new AssignRoleCommand(
                            testTenant.getId(),
                            targetMembership.getId(),
                            currentRoleId,
                            RoleScopeType.TENANT,
                            null
                    );
                    roleAssignmentService.assignRole(cmd, ownerUser.getId());
                } catch (Throwable t) {
                    assignError.set(t);
                }
            });

            startLatch.countDown();
            deleteFuture.get(10, TimeUnit.SECONDS);
            assignFuture.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Verification of invariants:
            Optional<Role> roleOpt = roleRepository.findById(currentRoleId);
            boolean hasAssignments = userRoleAssignmentRepository.existsByTenantIdAndRoleId(testTenant.getId(), currentRoleId);

            if (hasAssignments) {
                // If assignment succeeded, the role MUST NOT be physically deleted!
                // It must be soft-deactivated if delete ran, or active if assign ran.
                assertThat(roleOpt).isPresent();
                // No lost history
                assertThat(userRoleAssignmentRepository.findAllByTenantIdAndMembershipId(testTenant.getId(), targetMembership.getId())).isNotEmpty();
            } else {
                // If assignment did NOT happen, role was physically deleted
                assertThat(roleOpt).isEmpty();
                // Assignment error must be RoleNotFoundException or inactive
                assertThat(assignError.get()).isNotNull();
            }
        }
    }
}
