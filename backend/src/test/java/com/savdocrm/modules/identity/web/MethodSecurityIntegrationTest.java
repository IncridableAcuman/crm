package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.TenantBootstrapService;
import com.savdocrm.modules.identity.application.dto.CreateRoleRequest;
import com.savdocrm.modules.tenancy.application.dto.CreateBranchCommand;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class MethodSecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_method_security_test")
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

    private Tenant testTenant;
    private Branch branchA;
    private Branch branchB;

    private User ownerUser;
    private TenantMembership ownerMembership;
    private String ownerToken;

    private User cashierUser;
    private TenantMembership cashierMembership;
    private String cashierToken;

    private User noRoleUser;
    private TenantMembership noRoleMembership;
    private String noRoleToken;

    private String globalToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        TenantContextHolder.clear();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        testTenant = tenantRepository.save(Tenant.builder()
                .name("Security Test Tenant " + suffix)
                .code("sec-tenant-" + suffix)
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        Branch branchAEntity = Branch.builder()
                .name("Branch A")
                .code("BR-A-" + suffix)
                .isMain(true)
                .isActive(true)
                .build();
        branchAEntity.setTenantId(testTenant.getId());
        branchA = branchRepository.save(branchAEntity);

        Branch branchBEntity = Branch.builder()
                .name("Branch B")
                .code("BR-B-" + suffix)
                .isMain(false)
                .isActive(true)
                .build();
        branchBEntity.setTenantId(testTenant.getId());
        branchB = branchRepository.save(branchBEntity);

        // 1. OWNER
        ownerUser = userRepository.save(User.builder()
                .email("owner." + suffix + "@example.com")
                .username("owner_" + suffix)
                .passwordHash("hashed")
                .firstName("Owner")
                .lastName("Sec")
                .status(UserStatus.ACTIVE)
                .build());

        ownerMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(ownerUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        tenantBootstrapService.initializeNewTenantRbac(testTenant.getId(), ownerMembership.getId());
        ownerToken = jwtTokenProvider.generateTenantAccessToken(
                ownerUser.getId(), ownerUser.getEmail(), testTenant.getId(), ownerMembership.getId());

        // 2. CASHIER (scoped to Branch A)
        cashierUser = userRepository.save(User.builder()
                .email("cashier." + suffix + "@example.com")
                .username("cashier_" + suffix)
                .passwordHash("hashed")
                .firstName("Cashier")
                .lastName("Sec")
                .status(UserStatus.ACTIVE)
                .build());

        cashierMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(cashierUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        Role cashierRole = roleRepository.findByTenantIdAndCode(testTenant.getId(), SystemRoles.CASHIER).orElseThrow();
        UserRoleAssignment cashierAssign = UserRoleAssignment.builder()
                .membershipId(cashierMembership.getId())
                .roleId(cashierRole.getId())
                .scopeType(RoleScopeType.BRANCH)
                .branchId(branchA.getId())
                .isActive(true)
                .build();
        cashierAssign.setTenantId(testTenant.getId());
        userRoleAssignmentRepository.save(cashierAssign);

        cashierToken = jwtTokenProvider.generateTenantAccessToken(
                cashierUser.getId(), cashierUser.getEmail(), testTenant.getId(), cashierMembership.getId());

        // 3. User with no role assignments
        noRoleUser = userRepository.save(User.builder()
                .email("norole." + suffix + "@example.com")
                .username("norole_" + suffix)
                .passwordHash("hashed")
                .firstName("No")
                .lastName("Role")
                .status(UserStatus.ACTIVE)
                .build());

        noRoleMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(noRoleUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        noRoleToken = jwtTokenProvider.generateTenantAccessToken(
                noRoleUser.getId(), noRoleUser.getEmail(), testTenant.getId(), noRoleMembership.getId());

        // 4. Global token (no tenant claim)
        globalToken = jwtTokenProvider.generateGlobalAccessToken(noRoleUser.getId(), noRoleUser.getEmail());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Fail-Closed: Unauthenticated request to /api/v1/roles returns 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("Fail-Closed: Global token without tenant accessing tenant-protected endpoint returns 403 Forbidden")
    void shouldReturn403WhenTenantContextMissing() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + globalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("Method Security: User with identity.roles.read can GET /api/v1/roles (200 OK)")
    void shouldAllowUserWithRoleReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Method Security: User without identity.roles.read cannot GET /api/v1/roles (403 Forbidden)")
    void shouldDenyUserWithoutRoleReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + noRoleToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("Method Security: User without identity.roles.manage cannot POST /api/v1/roles (403 Forbidden)")
    void shouldDenyUserWithoutRoleManagePermission() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest("NEW_ROLE", "New Role", "desc", Set.of());

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + noRoleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("Method Security: GET /api/v1/permissions returns all 33 permissions for authorized user")
    void shouldReturn33CatalogPermissionsForAuthorizedUser() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(33))
                .andExpect(jsonPath("$.data[0].code").isNotEmpty());
    }

    @Test
    @DisplayName("Method Security: GET /api/v1/permissions rejects user without identity.roles.read (403 Forbidden)")
    void shouldRejectPermissionsCatalogForUnauthorizedUser() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", "Bearer " + noRoleToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("GET /api/v1/me/permissions returns full catalog for OWNER")
    void shouldReturnAllPermissionsForOwner() throws Exception {
        mockMvc.perform(get("/api/v1/me/permissions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantPermissions.length()").value(33));
    }

    @Test
    @DisplayName("GET /api/v1/me/permissions returns branch-scoped permissions for CASHIER")
    void shouldReturnBranchScopedPermissionsForCashier() throws Exception {
        mockMvc.perform(get("/api/v1/me/permissions")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantPermissions").isEmpty())
                .andExpect(jsonPath("$.data.branchPermissions." + branchA.getId()).isArray())
                .andExpect(jsonPath("$.data.branchPermissions." + branchB.getId()).doesNotExist());
    }

    @Test
    @DisplayName("Method Security: User without tenancy.branches.manage cannot POST /api/v1/branches (403 Forbidden)")
    void shouldDenyUserWithoutBranchManagePermission() throws Exception {
        CreateBranchCommand command = new CreateBranchCommand("Unauthorized Branch", "BR-UNAUTH", "123 St", "+998901234567", false);

        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("Method Security: OWNER with tenancy.branches.manage can POST /api/v1/branches (201 Created)")
    void shouldAllowOwnerWithBranchManagePermission() throws Exception {
        CreateBranchCommand command = new CreateBranchCommand("Authorized Branch", "BR-AUTH", "123 St", "+998901234567", false);

        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("BR-AUTH"))
                .andExpect(jsonPath("$.data.isMain").value(false));
    }
}
