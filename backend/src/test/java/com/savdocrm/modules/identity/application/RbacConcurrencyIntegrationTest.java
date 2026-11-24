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
import com.savdocrm.modules.identity.domain.exception.LastActiveOwnerException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RbacConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_concurrency_test")
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

    @Test
    @DisplayName("Scenario A: Concurrent OWNER revocations serialize safely and prevent last owner removal")
    void testConcurrentOwnerRevocations() throws Exception {
        // Setup Tenant with exactly 2 eligible owners: U1 and U2
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Revocation Concurrency Store")
                .code("CONC_REV_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        String uid1 = UUID.randomUUID().toString().substring(0, 8);
        User u1 = userRepository.save(User.builder()
                .username("u1_" + uid1)
                .email("u1_" + uid1 + "@example.com")
                .passwordHash("hashed")
                .firstName("User")
                .lastName("One")
                .status(UserStatus.ACTIVE)
                .build());

        String uid2 = UUID.randomUUID().toString().substring(0, 8);
        User u2 = userRepository.save(User.builder()
                .username("u2_" + uid2)
                .email("u2_" + uid2 + "@example.com")
                .passwordHash("hashed")
                .firstName("User")
                .lastName("Two")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership m1 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenant.getId())
                .userId(u1.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        TenantMembership m2 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenant.getId())
                .userId(u2.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(tenant.getId(), m1.getId());
        Role ownerRole = roleRepository.findByTenantIdAndCode(tenant.getId(), SystemRoles.OWNER).orElseThrow();

        UserRoleAssignment assign1 = userRoleAssignmentRepository
                .findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenant.getId(), m1.getId()).get(0);

        UserRoleAssignment assign2 = UserRoleAssignment.builder()
                .membershipId(m2.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assign2.setTenantId(tenant.getId());
        assign2 = userRoleAssignmentRepository.save(assign2);

        // Verify initial state: exactly 2 eligible owners
        assertThat(userRoleAssignmentRepository.countEligibleOwners(tenant.getId(), null, null, null)).isEqualTo(2);

        // Execute concurrent revocations in 2 separate database transactions
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger lastOwnerExceptionCount = new AtomicInteger(0);

        UUID a1Id = assign1.getId();
        UUID a2Id = assign2.getId();
        UUID tId = tenant.getId();
        UUID u1Id = u1.getId();
        UUID u2Id = u2.getId();

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                roleAssignmentService.revokeRoleAssignment(tId, a1Id, u2Id);
                successCount.incrementAndGet();
            } catch (LastActiveOwnerException ex) {
                lastOwnerExceptionCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                roleAssignmentService.revokeRoleAssignment(tId, a2Id, u1Id);
                successCount.incrementAndGet();
            } catch (LastActiveOwnerException ex) {
                lastOwnerExceptionCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one revocation succeeded, and the second was blocked by LastActiveOwnerException
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(lastOwnerExceptionCount.get()).isEqualTo(1);

        // Tenant MUST retain exactly 1 eligible active owner
        long finalOwners = userRoleAssignmentRepository.countEligibleOwners(tId, null, null, null);
        assertThat(finalOwners).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario B: Concurrent OWNER assignment and global user suspension serialize safely")
    void testConcurrentOwnerAssignmentAndGlobalSuspension() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Assignment Suspension Store")
                .code("CONC_AS_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        String oUid = UUID.randomUUID().toString().substring(0, 8);
        User initialOwner = userRepository.save(User.builder()
                .username("owner_" + oUid)
                .email("owner_" + oUid + "@example.com")
                .passwordHash("hashed")
                .firstName("Initial")
                .lastName("Owner")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership initialMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenant.getId())
                .userId(initialOwner.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(tenant.getId(), initialMembership.getId());

        String targetUid = UUID.randomUUID().toString().substring(0, 8);
        User targetUser = userRepository.save(User.builder()
                .username("target_" + targetUid)
                .email("target_" + targetUid + "@example.com")
                .passwordHash("hashed")
                .firstName("Target")
                .lastName("Candidate")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership targetMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenant.getId())
                .userId(targetUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role ownerRole = roleRepository.findByTenantIdAndCode(tenant.getId(), SystemRoles.OWNER).orElseThrow();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicReference<String> assignmentOutcome = new AtomicReference<>("PENDING");
        AtomicReference<String> suspensionOutcome = new AtomicReference<>("PENDING");

        UUID tId = tenant.getId();
        UUID targetMemId = targetMembership.getId();
        UUID roleId = ownerRole.getId();
        UUID actorId = initialOwner.getId();
        UUID targetUserId = targetUser.getId();

        Callable<Void> assignTask = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                AssignRoleCommand cmd = new AssignRoleCommand(tId, targetMemId, roleId, RoleScopeType.TENANT, null);
                roleAssignmentService.assignRole(cmd, actorId);
                assignmentOutcome.set("SUCCESS");
            } catch (Exception ex) {
                assignmentOutcome.set("FAILED: " + ex.getClass().getSimpleName());
            }
            return null;
        };

        Callable<Void> suspendTask = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                userService.suspendGlobalUser(targetUserId);
                suspensionOutcome.set("SUCCESS");
            } catch (Exception ex) {
                suspensionOutcome.set("FAILED: " + ex.getClass().getSimpleName());
            }
            return null;
        };

        Future<Void> f1 = executor.submit(assignTask);
        Future<Void> f2 = executor.submit(suspendTask);

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Invariant check: Store MUST have at least 1 eligible active owner
        long finalOwners = userRoleAssignmentRepository.countEligibleOwners(tId, null, null, null);
        assertThat(finalOwners).isGreaterThanOrEqualTo(1);

        // Target user cannot be simultaneously ACTIVE OWNER while SUSPENDED
        User reloadedTarget = userRepository.findById(targetUserId).orElseThrow();
        if (reloadedTarget.getStatus() == UserStatus.SUSPENDED) {
            // If user is suspended, they MUST NOT count as an eligible owner
            long targetAsOwner = userRoleAssignmentRepository.countEligibleOwners(tId, null, null, initialOwner.getId());
            assertThat(targetAsOwner).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Scenario C: Concurrent membership suspension preserves last active owner")
    void testConcurrentMembershipSuspension() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Membership Suspension Concurrency Store")
                .code("CONC_MEM_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        String uid1 = UUID.randomUUID().toString().substring(0, 8);
        User u1 = userRepository.save(User.builder()
                .username("u1_" + uid1)
                .email("u1_" + uid1 + "@example.com")
                .passwordHash("hashed")
                .firstName("User")
                .lastName("One")
                .status(UserStatus.ACTIVE)
                .build());

        String uid2 = UUID.randomUUID().toString().substring(0, 8);
        User u2 = userRepository.save(User.builder()
                .username("u2_" + uid2)
                .email("u2_" + uid2 + "@example.com")
                .passwordHash("hashed")
                .firstName("User")
                .lastName("Two")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership m1 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenant.getId())
                .userId(u1.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        TenantMembership m2 = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenant.getId())
                .userId(u2.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(tenant.getId(), m1.getId());
        Role ownerRole = roleRepository.findByTenantIdAndCode(tenant.getId(), SystemRoles.OWNER).orElseThrow();

        UserRoleAssignment assign2 = UserRoleAssignment.builder()
                .membershipId(m2.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        assign2.setTenantId(tenant.getId());
        userRoleAssignmentRepository.save(assign2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger lastOwnerExceptionCount = new AtomicInteger(0);

        UUID tId = tenant.getId();
        UUID m1Id = m1.getId();
        UUID m2Id = m2.getId();
        UUID u1Id = u1.getId();
        UUID u2Id = u2.getId();

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                userService.suspendTenantMembership(tId, m1Id, u2Id);
                successCount.incrementAndGet();
            } catch (LastActiveOwnerException ex) {
                lastOwnerExceptionCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                userService.suspendTenantMembership(tId, m2Id, u1Id);
                successCount.incrementAndGet();
            } catch (LastActiveOwnerException ex) {
                lastOwnerExceptionCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(lastOwnerExceptionCount.get()).isEqualTo(1);

        long finalOwners = userRoleAssignmentRepository.countEligibleOwners(tId, null, null, null);
        assertThat(finalOwners).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario D: Concurrent tenant bootstrap creates exactly ONE OWNER assignment idempotently")
    void testConcurrentTenantBootstrap() throws Exception {
        String cUid = UUID.randomUUID().toString().substring(0, 8);
        User creator = userRepository.save(User.builder()
                .username("creator_" + cUid)
                .email("creator_" + cUid + "@example.com")
                .passwordHash("hashed")
                .firstName("Legacy")
                .lastName("Creator")
                .status(UserStatus.ACTIVE)
                .build());

        Tenant unbootstrapped = Tenant.builder()
                .name("Concurrent Unbootstrapped Store")
                .code("CONC_BOOT_" + UUID.randomUUID().toString().substring(0, 8))
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build();
        unbootstrapped.setCreatedBy(creator.getId());
        unbootstrapped = tenantRepository.save(unbootstrapped);

        TenantMembership creatorMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(unbootstrapped.getId())
                .userId(creator.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<TenantBootstrapResult> results = Collections.synchronizedList(new ArrayList<>());

        UUID tId = unbootstrapped.getId();
        UUID mId = creatorMembership.getId();
        UUID creatorId = creator.getId();

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            TenantBootstrapResult res = tenantBootstrapService.bootstrapExistingTenantOwner(tId, mId, creatorId);
            results.add(res);
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            TenantBootstrapResult res = tenantBootstrapService.bootstrapExistingTenantOwner(tId, mId, creatorId);
            results.add(res);
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // One must be SUCCESS, the other ALREADY_BOOTSTRAPPED
        assertThat(results).containsExactlyInAnyOrder(TenantBootstrapResult.SUCCESS, TenantBootstrapResult.ALREADY_BOOTSTRAPPED);

        // Exactly 1 eligible active owner
        long ownerCount = userRoleAssignmentRepository.countEligibleOwners(tId, null, null, null);
        assertThat(ownerCount).isEqualTo(1);

        // Exactly 1 active OWNER assignment in the database
        List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                .findAllByTenantIdAndMembershipIdAndIsActiveTrue(tId, mId);
        assertThat(assignments).hasSize(1);
    }
}
