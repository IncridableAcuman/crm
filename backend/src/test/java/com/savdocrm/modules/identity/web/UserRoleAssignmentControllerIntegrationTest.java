package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.TenantBootstrapService;
import com.savdocrm.modules.identity.application.dto.CreateRoleAssignmentRequest;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Branch;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class UserRoleAssignmentControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_role_assignment_ctrl_test")
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
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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

    private Tenant tenantA;
    private Tenant tenantB;
    private Branch branchA1;
    private Branch branchB1;

    private User ownerUserA;
    private TenantMembership ownerMembershipA;
    private String ownerTokenA;

    private User adminUserA;
    private TenantMembership adminMembershipA;
    private String adminTokenA;

    private User targetUserA;
    private TenantMembership targetMembershipA;

    private User ownerUserB;
    private TenantMembership ownerMembershipB;
    private String ownerTokenB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        TenantContextHolder.clear();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Tenant A setup
        tenantA = tenantRepository.save(Tenant.builder()
                .name("Tenant A " + suffix)
                .code("tenant-a-" + suffix)
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        Branch branchA1Entity = Branch.builder()
                .name("Branch A1")
                .code("BR-A1-" + suffix)
                .isMain(true)
                .isActive(true)
                .build();
        branchA1Entity.setTenantId(tenantA.getId());
        branchA1 = branchRepository.save(branchA1Entity);

        ownerUserA = userRepository.save(User.builder()
                .email("owner.a." + suffix + "@example.com")
                .username("ownera_" + suffix)
                .passwordHash("hashed")
                .firstName("Owner")
                .lastName("A")
                .status(UserStatus.ACTIVE)
                .build());

        ownerMembershipA = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(ownerUserA.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(tenantA.getId(), ownerMembershipA.getId());
        ownerTokenA = jwtTokenProvider.generateTenantAccessToken(
                ownerUserA.getId(), ownerUserA.getEmail(), tenantA.getId(), ownerMembershipA.getId());

        // Admin User in Tenant A
        adminUserA = userRepository.save(User.builder()
                .email("admin.a." + suffix + "@example.com")
                .username("admina_" + suffix)
                .passwordHash("hashed")
                .firstName("Admin")
                .lastName("A")
                .status(UserStatus.ACTIVE)
                .build());

        adminMembershipA = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(adminUserA.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role adminRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.ADMIN).orElseThrow();
        UserRoleAssignment adminAssign = UserRoleAssignment.builder()
                .membershipId(adminMembershipA.getId())
                .roleId(adminRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        adminAssign.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(adminAssign);

        adminTokenA = jwtTokenProvider.generateTenantAccessToken(
                adminUserA.getId(), adminUserA.getEmail(), tenantA.getId(), adminMembershipA.getId());

        // Target User in Tenant A
        targetUserA = userRepository.save(User.builder()
                .email("target.a." + suffix + "@example.com")
                .username("targeta_" + suffix)
                .passwordHash("hashed")
                .firstName("Target")
                .lastName("A")
                .status(UserStatus.ACTIVE)
                .build());

        targetMembershipA = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(targetUserA.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        // Tenant B setup
        tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant B " + suffix)
                .code("tenant-b-" + suffix)
                .currencyCode("EUR")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        Branch branchB1Entity = Branch.builder()
                .name("Branch B1")
                .code("BR-B1-" + suffix)
                .isMain(true)
                .isActive(true)
                .build();
        branchB1Entity.setTenantId(tenantB.getId());
        branchB1 = branchRepository.save(branchB1Entity);

        ownerUserB = userRepository.save(User.builder()
                .email("owner.b." + suffix + "@example.com")
                .username("ownerb_" + suffix)
                .passwordHash("hashed")
                .firstName("Owner")
                .lastName("B")
                .status(UserStatus.ACTIVE)
                .build());

        ownerMembershipB = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantB.getId())
                .userId(ownerUserB.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(tenantB.getId(), ownerMembershipB.getId());
        ownerTokenB = jwtTokenProvider.generateTenantAccessToken(
                ownerUserB.getId(), ownerUserB.getEmail(), tenantB.getId(), ownerMembershipB.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("GET /api/v1/role-assignments lists assignments in active tenant")
    void shouldListRoleAssignmentsForActiveTenant() throws Exception {
        mockMvc.perform(get("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2)); // Owner + Admin
    }

    @Test
    @DisplayName("GET /api/v1/role-assignments enforces tenant isolation: cannot see Tenant B assignments")
    void shouldEnforceTenantIsolationOnRoleAssignments() throws Exception {
        mockMvc.perform(get("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.membershipId == '" + ownerMembershipB.getId() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/members/{membershipId}/roles lists assignments for specific member")
    void shouldListRoleAssignmentsForMember() throws Exception {
        mockMvc.perform(get("/api/v1/members/" + adminMembershipA.getId() + "/roles")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].roleCode").value(SystemRoles.ADMIN));
    }

    @Test
    @DisplayName("GET /api/v1/members/{membershipId}/roles rejects cross-tenant member access (400 Bad Request)")
    void shouldRejectAccessingCrossTenantMemberRoles() throws Exception {
        mockMvc.perform(get("/api/v1/members/" + ownerMembershipB.getId() + "/roles")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments assigns role at TENANT scope")
    void shouldAssignRoleAtTenantScope() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                cashierRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleCode").value(SystemRoles.CASHIER))
                .andExpect(jsonPath("$.data.scopeType").value("TENANT"))
                .andExpect(jsonPath("$.data.branchId").doesNotExist())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments assigns role at BRANCH scope")
    void shouldAssignRoleAtBranchScope() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                cashierRole.getId(),
                RoleScopeType.BRANCH,
                branchA1.getId()
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleCode").value(SystemRoles.CASHIER))
                .andExpect(jsonPath("$.data.scopeType").value("BRANCH"))
                .andExpect(jsonPath("$.data.branchId").value(branchA1.getId().toString()))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments rejects TENANT scope with branchId (400 Bad Request)")
    void shouldRejectTenantScopeWithBranchId() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                cashierRole.getId(),
                RoleScopeType.TENANT,
                branchA1.getId() // Invalid: branchId not allowed for TENANT scope
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments rejects BRANCH scope without branchId (400 Bad Request)")
    void shouldRejectBranchScopeWithoutBranchId() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                cashierRole.getId(),
                RoleScopeType.BRANCH,
                null // Invalid: branchId required for BRANCH scope
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments rejects cross-tenant role assignment (400 Bad Request)")
    void shouldRejectCrossTenantRoleAssignment() throws Exception {
        // Role belongs to Tenant B, request executed in Tenant A
        Role roleB = roleRepository.findByTenantIdAndCode(tenantB.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                roleB.getId(),
                RoleScopeType.TENANT,
                null
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments rejects cross-tenant branch assignment (400 Bad Request)")
    void shouldRejectCrossTenantBranchAssignment() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                cashierRole.getId(),
                RoleScopeType.BRANCH,
                branchB1.getId() // Branch belongs to Tenant B
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments rejects duplicate active assignment (409 Conflict)")
    void shouldRejectDuplicateActiveAssignment() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                cashierRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate
        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments enforces Self-Elevation Guard: actor cannot assign role to self (403 Forbidden)")
    void shouldEnforceSelfElevationGuard() throws Exception {
        Role viewerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.VIEWER).orElseThrow();

        // Admin tries to assign role to their own membership
        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                adminMembershipA.getId(),
                viewerRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + adminTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Permission Denied"));
    }

    @Test
    @DisplayName("POST /api/v1/role-assignments enforces Owner Protection Guard: non-owner cannot assign OWNER (403 Forbidden)")
    void shouldEnforceOwnerProtectionGuardOnAssignment() throws Exception {
        Role ownerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();

        // Admin tries to assign OWNER to target user
        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                targetMembershipA.getId(),
                ownerRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        mockMvc.perform(post("/api/v1/role-assignments")
                        .header("Authorization", "Bearer " + adminTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Permission Denied"));
    }

    @Test
    @DisplayName("DELETE /api/v1/role-assignments/{assignmentId} revokes assignment with soft revocation")
    void shouldRevokeRoleAssignmentSuccessfully() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(targetMembershipA.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        assignment.setTenantId(tenantA.getId());
        assignment = userRoleAssignmentRepository.save(assignment);

        mockMvc.perform(delete("/api/v1/role-assignments/" + assignment.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserRoleAssignment revoked = userRoleAssignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("DELETE /api/v1/role-assignments/{assignmentId} enforces Last Active OWNER Protection (409 Conflict)")
    void shouldEnforceLastActiveOwnerProtectionOnRevocation() throws Exception {
        // Find sole OWNER assignment for Tenant A
        Role ownerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();
        UserRoleAssignment soleOwnerAssignment = userRoleAssignmentRepository
                .findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenantA.getId(), ownerMembershipA.getId())
                .stream()
                .filter(a -> a.getRoleId().equals(ownerRole.getId()))
                .findFirst().orElseThrow();

        // Target user created with another membership to test revoking the sole owner
        // Since ownerUserA cannot self-assign/revoke without another owner anyway, let's create a 2nd admin or try revoking
        // If owner tries to revoke the sole owner assignment:
        mockMvc.perform(delete("/api/v1/role-assignments/" + soleOwnerAssignment.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    @DisplayName("DELETE /api/v1/role-assignments/{assignmentId} allows revoking OWNER when another eligible OWNER exists")
    void shouldAllowRevokingOwnerWhenAnotherOwnerExists() throws Exception {
        Role ownerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();

        // Create second active eligible OWNER for Tenant A
        User secondOwner = userRepository.save(User.builder()
                .email("second.owner@example.com")
                .username("secondowner")
                .passwordHash("hashed")
                .firstName("Second")
                .lastName("Owner")
                .status(UserStatus.ACTIVE)
                .build());

        TenantMembership secondMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(secondOwner.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        UserRoleAssignment secondOwnerAssignment = UserRoleAssignment.builder()
                .membershipId(secondMembership.getId())
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        secondOwnerAssignment.setTenantId(tenantA.getId());
        secondOwnerAssignment = userRoleAssignmentRepository.save(secondOwnerAssignment);

        // First owner revokes second owner's assignment -> should succeed!
        mockMvc.perform(delete("/api/v1/role-assignments/" + secondOwnerAssignment.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserRoleAssignment revoked = userRoleAssignmentRepository.findById(secondOwnerAssignment.getId()).orElseThrow();
        assertThat(revoked.isActive()).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/v1/members/{membershipId}/roles/{assignmentId} rejects mismatched membership ID (400 Bad Request)")
    void shouldRejectRevokingAssignmentWithMismatchedMembershipId() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(targetMembershipA.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        assignment.setTenantId(tenantA.getId());
        assignment = userRoleAssignmentRepository.save(assignment);

        // Attempt to revoke using adminMembershipA in the path instead of targetMembershipA
        mockMvc.perform(delete("/api/v1/members/" + adminMembershipA.getId() + "/roles/" + assignment.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Role assignment " + assignment.getId() + " does not belong to membership: " + adminMembershipA.getId()));
    }

    @Test
    @DisplayName("POST /api/v1/members/{membershipId}/roles rejects mismatched path and body membership (400 Bad Request)")
    void shouldRejectMismatchedMembershipInPathAndBody() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        CreateRoleAssignmentRequest request = new CreateRoleAssignmentRequest(
                adminMembershipA.getId(), // different from path
                cashierRole.getId(),
                RoleScopeType.TENANT,
                null
        );

        mockMvc.perform(post("/api/v1/members/" + targetMembershipA.getId() + "/roles")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Path membershipId and request body membershipId do not match."));
    }
}
