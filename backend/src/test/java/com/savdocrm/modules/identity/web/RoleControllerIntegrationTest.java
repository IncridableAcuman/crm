package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.TenantBootstrapService;
import com.savdocrm.modules.identity.application.dto.CreateRoleRequest;
import com.savdocrm.modules.identity.application.dto.RolePermissionUpdateRequest;
import com.savdocrm.modules.identity.application.dto.UpdateRoleRequest;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.Permissions;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RolePermissionRepository;
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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class RoleControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_role_controller_test")
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
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private User ownerUserA;
    private TenantMembership ownerMembershipA;
    private String ownerTokenA;

    private User adminUserA;
    private TenantMembership adminMembershipA;
    private String adminTokenA;

    private User managerUserA;
    private TenantMembership managerMembershipA;
    private String managerTokenA;

    private User viewerUserA;
    private TenantMembership viewerMembershipA;
    private String viewerTokenA;

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

        // Admin in Tenant A (holds ADMIN role - has identity.roles.manage, but lacks system.settings.manage)
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
        UserRoleAssignment adminAssignment = UserRoleAssignment.builder()
                .membershipId(adminMembershipA.getId())
                .roleId(adminRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        adminAssignment.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(adminAssignment);

        adminTokenA = jwtTokenProvider.generateTenantAccessToken(
                adminUserA.getId(), adminUserA.getEmail(), tenantA.getId(), adminMembershipA.getId());

        // Manager in Tenant A (holds MANAGER role)
        managerUserA = userRepository.save(User.builder()
                .email("manager.a." + suffix + "@example.com")
                .username("managera_" + suffix)
                .passwordHash("hashed")
                .firstName("Manager")
                .lastName("A")
                .status(UserStatus.ACTIVE)
                .build());

        managerMembershipA = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(managerUserA.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role managerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.MANAGER).orElseThrow();
        UserRoleAssignment managerAssignment = UserRoleAssignment.builder()
                .membershipId(managerMembershipA.getId())
                .roleId(managerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        managerAssignment.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(managerAssignment);

        managerTokenA = jwtTokenProvider.generateTenantAccessToken(
                managerUserA.getId(), managerUserA.getEmail(), tenantA.getId(), managerMembershipA.getId());

        // Viewer in Tenant A (holds VIEWER role, lacks identity.roles.manage)
        viewerUserA = userRepository.save(User.builder()
                .email("viewer.a." + suffix + "@example.com")
                .username("viewera_" + suffix)
                .passwordHash("hashed")
                .firstName("Viewer")
                .lastName("A")
                .status(UserStatus.ACTIVE)
                .build());

        viewerMembershipA = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(tenantA.getId())
                .userId(viewerUserA.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role viewerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.VIEWER).orElseThrow();
        UserRoleAssignment viewerAssignment = UserRoleAssignment.builder()
                .membershipId(viewerMembershipA.getId())
                .roleId(viewerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        viewerAssignment.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(viewerAssignment);

        viewerTokenA = jwtTokenProvider.generateTenantAccessToken(
                viewerUserA.getId(), viewerUserA.getEmail(), tenantA.getId(), viewerMembershipA.getId());

        // Tenant B setup (for cross-tenant checks)
        tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant B " + suffix)
                .code("tenant-b-" + suffix)
                .currencyCode("EUR")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

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
    @DisplayName("GET /api/v1/roles returns all roles for active tenant (system + custom)")
    void shouldListRolesForActiveTenant() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[?(@.code == 'OWNER')].isSystem").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/roles enforces tenant isolation: Tenant A cannot see custom role of Tenant B")
    void shouldEnforceTenantIsolationOnRolesList() throws Exception {
        // Create custom role in Tenant B
        Role customRoleB = Role.builder()
                .code("TENANT_B_EXCLUSIVE")
                .name("Tenant B Exclusive")
                .isSystem(false)
                .isActive(true)
                .build();
        customRoleB.setTenantId(tenantB.getId());
        roleRepository.save(customRoleB);

        // Tenant A lists roles -> should NOT see TENANT_B_EXCLUSIVE
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'TENANT_B_EXCLUSIVE')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/roles/{roleId} returns role details and permissions")
    void shouldGetRoleById() throws Exception {
        Role adminRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.ADMIN).orElseThrow();

        mockMvc.perform(get("/api/v1/roles/" + adminRole.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(SystemRoles.ADMIN))
                .andExpect(jsonPath("$.data.name").value("Administrator"))
                .andExpect(jsonPath("$.data.isSystem").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/roles/{roleId} returns 404 for role belonging to another tenant")
    void shouldReturn404WhenAccessingOtherTenantRole() throws Exception {
        Role roleB = roleRepository.findByTenantIdAndCode(tenantB.getId(), SystemRoles.ADMIN).orElseThrow();

        mockMvc.perform(get("/api/v1/roles/" + roleB.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("POST /api/v1/roles creates custom role with valid permissions")
    void shouldCreateCustomRoleSuccessfully() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "SUPERVISOR",
                "Shift Supervisor",
                "Supervises floor operations",
                Set.of(Permissions.SALES_ORDERS_READ, Permissions.SALES_ORDERS_CREATE)
        );

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SUPERVISOR"))
                .andExpect(jsonPath("$.data.name").value("Shift Supervisor"))
                .andExpect(jsonPath("$.data.isSystem").value(false))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.permissions").isArray());

        Role saved = roleRepository.findByTenantIdAndCode(tenantA.getId(), "SUPERVISOR").orElseThrow();
        assertThat(saved.isSystem()).isFalse();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/roles rejects duplicate role code in same tenant with 409 Conflict")
    void shouldRejectDuplicateRoleCodeInSameTenant() throws Exception {
        CreateRoleRequest request1 = new CreateRoleRequest(
                "DISPATCHER", "Dispatcher 1", "desc", Set.of()
        );
        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CreateRoleRequest request2 = new CreateRoleRequest(
                "DISPATCHER", "Dispatcher 2", "desc", Set.of()
        );
        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    @DisplayName("POST /api/v1/roles rejects collision with built-in system role code (409 Conflict)")
    void shouldRejectCollisionWithSystemRoleCode() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "ADMIN", "Custom Admin", "desc", Set.of()
        );

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    @DisplayName("POST /api/v1/roles rejects invalid role code format with 400 Bad Request")
    void shouldRejectInvalidRoleCodeFormat() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "invalid-role-code", "Invalid Code", "desc", Set.of()
        );

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{roleId} updates custom role name and description")
    void shouldUpdateCustomRole() throws Exception {
        Role customRole = Role.builder()
                .code("LEAD_CASHIER")
                .name("Initial Name")
                .description("Initial Desc")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(tenantA.getId());
        customRole = roleRepository.save(customRole);

        UpdateRoleRequest update = new UpdateRoleRequest("Senior Cashier Lead", "Updated Lead Description");

        mockMvc.perform(put("/api/v1/roles/" + customRole.getId())
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Senior Cashier Lead"))
                .andExpect(jsonPath("$.data.description").value("Updated Lead Description"));
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{roleId} rejects updating system roles with 403 Forbidden")
    void shouldRejectUpdatingSystemRole() throws Exception {
        Role ownerRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.OWNER).orElseThrow();

        UpdateRoleRequest update = new UpdateRoleRequest("Renamed Owner", "Attempted change");

        mockMvc.perform(put("/api/v1/roles/" + ownerRole.getId())
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("System Role Immutable"));
    }

    @Test
    @DisplayName("DELETE /api/v1/roles/{roleId} physically deletes unassigned custom role")
    void shouldPhysicallyDeleteUnassignedCustomRole() throws Exception {
        Role customRole = Role.builder()
                .code("UNUSED_ROLE")
                .name("Unused Role")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(tenantA.getId());
        customRole = roleRepository.save(customRole);

        mockMvc.perform(delete("/api/v1/roles/" + customRole.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(roleRepository.findById(customRole.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/v1/roles/{roleId} soft-deactivates custom role when assignments exist")
    void shouldSoftDeactivateCustomRoleWithAssignments() throws Exception {
        Role customRole = Role.builder()
                .code("ROLE_WITH_ASSIGNMENTS")
                .name("Assigned Role")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(tenantA.getId());
        customRole = roleRepository.save(customRole);

        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(viewerMembershipA.getId())
                .roleId(customRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        assignment.setTenantId(tenantA.getId());
        userRoleAssignmentRepository.save(assignment);

        mockMvc.perform(delete("/api/v1/roles/" + customRole.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk());

        Role deactivated = roleRepository.findById(customRole.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse(); // soft-deactivated
    }

    @Test
    @DisplayName("DELETE /api/v1/roles/{roleId} rejects deleting system roles with 403 Forbidden")
    void shouldRejectDeletingSystemRole() throws Exception {
        Role adminRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.ADMIN).orElseThrow();

        mockMvc.perform(delete("/api/v1/roles/" + adminRole.getId())
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("System Role Immutable"));
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{roleId}/permissions rejects modifying system role permissions")
    void shouldRejectModifyingSystemRolePermissions() throws Exception {
        Role cashierRole = roleRepository.findByTenantIdAndCode(tenantA.getId(), SystemRoles.CASHIER).orElseThrow();

        RolePermissionUpdateRequest update = new RolePermissionUpdateRequest(Set.of(Permissions.SALES_ORDERS_READ));

        mockMvc.perform(put("/api/v1/roles/" + cashierRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("System Role Immutable"));
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{roleId}/permissions rejects unknown permission code (400 Bad Request)")
    void shouldRejectUnknownPermissionCode() throws Exception {
        Role customRole = Role.builder()
                .code("CUSTOM_AUDIT")
                .name("Custom Audit")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(tenantA.getId());
        customRole = roleRepository.save(customRole);

        RolePermissionUpdateRequest update = new RolePermissionUpdateRequest(Set.of("invalid.bogus.permission"));

        mockMvc.perform(put("/api/v1/roles/" + customRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{roleId}/permissions enforces Grant-What-You-Hold for non-owner actor")
    void shouldEnforceGrantWhatYouHoldOnPermissionUpdate() throws Exception {
        // Admin in Tenant A has ADMIN permissions (has identity.roles.manage, but lacks system.settings.manage)
        Role customRole = Role.builder()
                .code("CUSTOM_TEST_ROLE")
                .name("Custom Test")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(tenantA.getId());
        customRole = roleRepository.save(customRole);

        // Admin tries to grant system.settings.manage (which only OWNER has)
        RolePermissionUpdateRequest update = new RolePermissionUpdateRequest(Set.of(Permissions.SYSTEM_SETTINGS_MANAGE));

        mockMvc.perform(put("/api/v1/roles/" + customRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + adminTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Permission Denied"));
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{roleId}/permissions allows OWNER to grant any canonical permission")
    void shouldAllowOwnerToGrantAnyCanonicalPermission() throws Exception {
        Role customRole = Role.builder()
                .code("FINANCE_ASSISTANT")
                .name("Finance Assistant")
                .isSystem(false)
                .isActive(true)
                .build();
        customRole.setTenantId(tenantA.getId());
        customRole = roleRepository.save(customRole);

        RolePermissionUpdateRequest update = new RolePermissionUpdateRequest(
                Set.of(Permissions.FINANCE_ACCOUNTS_READ, Permissions.FINANCE_EXPENSES_READ)
        );

        mockMvc.perform(put("/api/v1/roles/" + customRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.permissions").isArray());

        Set<String> assignedPerms = rolePermissionRepository.findPermissionCodesByTenantIdAndRoleId(tenantA.getId(), customRole.getId());
        assertThat(assignedPerms).containsExactlyInAnyOrder(Permissions.FINANCE_ACCOUNTS_READ, Permissions.FINANCE_EXPENSES_READ);
    }
}
