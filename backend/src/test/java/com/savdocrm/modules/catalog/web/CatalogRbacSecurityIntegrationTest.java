package com.savdocrm.modules.catalog.web;

import com.savdocrm.modules.catalog.application.ProductService;
import com.savdocrm.modules.catalog.application.dto.BarcodeInput;
import com.savdocrm.modules.catalog.application.dto.CreateProductCommand;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.modules.catalog.web.dto.request.CreateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateVariantRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductStatusRequest;
import com.savdocrm.modules.identity.application.AuthService;
import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.Permission;
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
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.multitenancy.TenantExecutionTemplate;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class CatalogRbacSecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_catalog_rbac_test")
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
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuthService authService;
    @Autowired
    private ProductService productService;
    @Autowired
    private TenantExecutionTemplate tenantExecutionTemplate;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantMembershipRepository membershipRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;
    @Autowired
    private UnitOfMeasureRepository unitRepository;

    private MockMvc mockMvc;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID tenantAPcsId;
    private UUID tenantAProductId;
    private UUID tenantAVariantId;
    private UUID tenantABarcodeId;

    private Branch branchA1;
    private Branch branchA2;
    private Branch inactiveBranchA;

    private String ownerTokenA;
    private String adminTokenA;
    private String viewerTokenA;
    private String tenantManagerTokenA;
    private String branchCashierTokenA;
    private String branchManagerTokenA;
    private String inactiveBranchCashierTokenA;
    private String createOnlyCustomTokenA;
    private String noRoleTokenA;
    private String globalTokenA;
    private String ownerTokenB;

    private User suspendedTargetUser;
    private TenantMembership suspendedTargetMembership;
    private String suspendedUserToken;
    private String suspendedMembershipToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        TenantContextHolder.clear();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse regA = authService.register(RegisterRequest.builder()
                .email("sec_a_" + suffix + "@example.com")
                .username("sec_a_" + suffix)
                .password("StrongPassword123!")
                .firstName("Owner")
                .lastName("A")
                .storeName("Sec Store A " + suffix)
                .build(), "127.0.0.1", "JUnit");
        tenantAId = regA.getActiveTenant().getTenantId();
        ownerTokenA = regA.getAccessToken();

        AuthResponse regB = authService.register(RegisterRequest.builder()
                .email("sec_b_" + suffix + "@example.com")
                .username("sec_b_" + suffix)
                .password("StrongPassword123!")
                .firstName("Owner")
                .lastName("B")
                .storeName("Sec Store B " + suffix)
                .build(), "127.0.0.1", "JUnit");
        tenantBId = regB.getActiveTenant().getTenantId();
        ownerTokenB = regB.getAccessToken();

        List<Branch> branchesA = branchRepository.findAllByTenantIdAndIsActiveTrue(tenantAId);
        branchA1 = branchesA.get(0);
        Branch b2 = Branch.builder().name("Branch A2").code("BR-A2-" + suffix).isMain(false).isActive(true).build();
        b2.setTenantId(tenantAId);
        branchA2 = branchRepository.save(b2);

        Branch bInactive = Branch.builder().name("Inactive").code("BR-OFF-" + suffix).isMain(false).isActive(false).build();
        bInactive.setTenantId(tenantAId);
        inactiveBranchA = branchRepository.save(bInactive);

        tenantAPcsId = unitRepository.findByTenantIdAndCode(tenantAId, "PCS").orElseThrow().getId();
        ProductDto seeded = tenantExecutionTemplate.executeAsTenant(tenantAId, () ->
                productService.createProduct(new CreateProductCommand(
                        null, tenantAPcsId, "Security Sample", null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                        List.of(new CreateVariantCommand(
                                "SEC-SKU-1", "Default", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true,
                                List.of(new BarcodeInput("4609991112223", BarcodeType.EAN13, true))
                        ))
                ))
        );
        tenantAProductId = seeded.id();
        tenantAVariantId = seeded.variants().get(0).id();
        tenantABarcodeId = seeded.variants().get(0).barcodes().get(0).id();

        adminTokenA = createUserWithRole("admin_" + suffix, SystemRoles.ADMIN, RoleScopeType.TENANT, null);
        viewerTokenA = createUserWithRole("viewer_" + suffix, SystemRoles.VIEWER, RoleScopeType.TENANT, null);
        tenantManagerTokenA = createUserWithRole("t_mgr_" + suffix, SystemRoles.MANAGER, RoleScopeType.TENANT, null);
        branchCashierTokenA = createUserWithRole("cash_" + suffix, SystemRoles.CASHIER, RoleScopeType.BRANCH, branchA1.getId());
        branchManagerTokenA = createUserWithRole("b_mgr_" + suffix, SystemRoles.MANAGER, RoleScopeType.BRANCH, branchA1.getId());
        inactiveBranchCashierTokenA = createUserWithRole("off_cash_" + suffix, SystemRoles.CASHIER, RoleScopeType.BRANCH, inactiveBranchA.getId());

        // Custom role holding ONLY catalog.products.read and catalog.products.create
        Role createOnlyRole = Role.builder()
                .code("PROD_CREATOR_" + suffix.toUpperCase())
                .name("Product Creator")
                .isSystem(false)
                .isActive(true)
                .build();
        createOnlyRole.setTenantId(tenantAId);
        createOnlyRole = roleRepository.save(createOnlyRole);
        Permission readPerm = permissionRepository.findByCode(Permissions.CATALOG_PRODUCTS_READ).orElseThrow();
        Permission createPerm = permissionRepository.findByCode(Permissions.CATALOG_PRODUCTS_CREATE).orElseThrow();
        rolePermissionRepository.save(RolePermission.builder().tenantId(tenantAId).roleId(createOnlyRole.getId()).permissionId(readPerm.getId()).build());
        rolePermissionRepository.save(RolePermission.builder().tenantId(tenantAId).roleId(createOnlyRole.getId()).permissionId(createPerm.getId()).build());
        User customUser = createUser("custom_" + suffix, UserStatus.ACTIVE);
        TenantMembership customMem = createMembership(tenantAId, customUser.getId(), MembershipStatus.ACTIVE);
        UserRoleAssignment customAssign = UserRoleAssignment.builder()
                .membershipId(customMem.getId())
                .roleId(createOnlyRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(true)
                .build();
        customAssign.setTenantId(tenantAId);
        assignmentRepository.save(customAssign);
        createOnlyCustomTokenA = jwtTokenProvider.generateTenantAccessToken(customUser.getId(), customUser.getEmail(), tenantAId, customMem.getId());

        // User with active membership but zero role assignments
        User noRoleUser = createUser("norole_" + suffix, UserStatus.ACTIVE);
        TenantMembership noRoleMem = createMembership(tenantAId, noRoleUser.getId(), MembershipStatus.ACTIVE);
        noRoleTokenA = jwtTokenProvider.generateTenantAccessToken(noRoleUser.getId(), noRoleUser.getEmail(), tenantAId, noRoleMem.getId());
        globalTokenA = jwtTokenProvider.generateGlobalAccessToken(noRoleUser.getId(), noRoleUser.getEmail());

        // Suspended user and suspended membership tokens
        suspendedTargetUser = createUser("susp_u_" + suffix, UserStatus.SUSPENDED);
        TenantMembership suspUserMem = createMembership(tenantAId, suspendedTargetUser.getId(), MembershipStatus.ACTIVE);
        assignRole(suspUserMem.getId(), SystemRoles.OWNER, RoleScopeType.TENANT, null);
        suspendedUserToken = jwtTokenProvider.generateTenantAccessToken(
                suspendedTargetUser.getId(), suspendedTargetUser.getEmail(), tenantAId, suspUserMem.getId()
        );

        User activeUserSuspMem = createUser("susp_m_" + suffix, UserStatus.ACTIVE);
        suspendedTargetMembership = createMembership(tenantAId, activeUserSuspMem.getId(), MembershipStatus.SUSPENDED);
        assignRole(suspendedTargetMembership.getId(), SystemRoles.OWNER, RoleScopeType.TENANT, null);
        suspendedMembershipToken = jwtTokenProvider.generateTenantAccessToken(
                activeUserSuspMem.getId(), activeUserSuspMem.getEmail(), tenantAId, suspendedTargetMembership.getId()
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Fail-closed gates: unauthenticated -> 401; global token, no-role, suspended user/membership -> 403")
    void unauthenticatedMissingTenantAndSuspendedGates() throws Exception {
        // 401 Unauthenticated
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/units")).andExpect(status().isUnauthorized());

        // 403 Missing TenantContext (global token)
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + globalTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + globalTokenA))
                .andExpect(status().isForbidden());

        // 403 Missing permission (no role)
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + noRoleTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + noRoleTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest(null, "NOPE", "Nope", null, 0))))
                .andExpect(status().isForbidden());

        // 403 Suspended User
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + suspendedUserToken))
                .andExpect(status().isForbidden());

        // 403 Suspended Membership
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + suspendedMembershipToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TENANT and BRANCH scopes enforce read-vs-write boundaries and never grant tenant-wide reads to branch-only roles")
    void tenantAndBranchScopeSemantics() throws Exception {
        // Scenario 1: TENANT VIEWER can read across tenant (without branchId) and active branches, but cannot write
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + viewerTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products")
                        .param("branchId", branchA2.getId().toString())
                        .header("Authorization", "Bearer " + viewerTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products")
                        .param("branchId", inactiveBranchA.getId().toString())
                        .header("Authorization", "Bearer " + viewerTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + viewerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleProductRequest("VIEWER-TRY"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/products/{id}", tenantAProductId)
                        .header("Authorization", "Bearer " + viewerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductRequest(null, tenantAPcsId, "No", null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + viewerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest(null, "VNO", "Viewer No", null, 0))))
                .andExpect(status().isForbidden());

        // TENANT MANAGER can create/update products (201/200) but CANNOT delete/archive products or manage categories/units (403)
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tenantManagerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleProductRequest("TMGR-OK"))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/products/{id}", tenantAProductId)
                        .header("Authorization", "Bearer " + tenantManagerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductRequest(null, tenantAPcsId, "Security Sample Edited", null, null))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/{id}/archive", tenantAProductId)
                        .header("Authorization", "Bearer " + tenantManagerTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/products/{id}/status", tenantAProductId)
                        .header("Authorization", "Bearer " + tenantManagerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductStatusRequest(ProductStatus.ARCHIVED))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/products/{id}", tenantAProductId)
                        .header("Authorization", "Bearer " + tenantManagerTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + tenantManagerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUnitRequest("TRAY", "Tray", "tr", false, 0))))
                .andExpect(status().isForbidden());

        // TENANT ADMIN (holds catalog.products.delete + catalog.categories.manage) CAN archive & restore & create unit
        mockMvc.perform(post("/api/v1/products/{id}/archive", tenantAProductId)
                        .header("Authorization", "Bearer " + adminTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/v1/products/{id}/restore", tenantAProductId)
                        .header("Authorization", "Bearer " + adminTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Custom role with read + create only CANNOT update or archive/delete (403)
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + createOnlyCustomTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleProductRequest("CUST-OK"))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/products/{id}", tenantAProductId)
                        .header("Authorization", "Bearer " + createOnlyCustomTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductRequest(null, tenantAPcsId, "No Update", null, null))))
                .andExpect(status().isForbidden());

        // Scenario 2 & Scenario 6: BRANCH CASHIER (branchA1 only) CAN read when ?branchId=branchA1,
        // CANNOT read when ?branchId=branchA2, and CANNOT bypass branch scope by omitting branchId!
        mockMvc.perform(get("/api/v1/products")
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get("/api/v1/products/lookup")
                        .param("code", "4609991112223")
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/{id}", tenantAProductId)
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/{pId}/variants", tenantAProductId)
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/{pId}/variants/{vId}/barcodes", tenantAProductId, tenantAVariantId)
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories")
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories/tree")
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/units")
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isOk());

        // Missing branchId MUST fail closed with 403 across all optional-branchId catalog read endpoints
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products/lookup").param("code", "4609991112223")
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products/{id}", tenantAProductId)
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products/{pId}/variants", tenantAProductId)
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/categories/tree").header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/units").header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());

        // Wrong branch (branchA2) -> 403 Forbidden for branchA1 CASHIER
        mockMvc.perform(get("/api/v1/products")
                        .param("branchId", branchA2.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products/lookup")
                        .param("code", "4609991112223")
                        .param("branchId", branchA2.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/categories")
                        .param("branchId", branchA2.getId().toString())
                        .header("Authorization", "Bearer " + branchCashierTokenA))
                .andExpect(status().isForbidden());

        // Scenario 3: Inactive branch assignment -> 403 Forbidden both without branchId and with inactiveBranchA
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + inactiveBranchCashierTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products")
                        .param("branchId", inactiveBranchA.getId().toString())
                        .header("Authorization", "Bearer " + inactiveBranchCashierTokenA))
                .andExpect(status().isForbidden());

        // BRANCH MANAGER (branchA1) can read with branchA1 (200) but cannot omit branchId (403) or execute TENANT writes (403)
        mockMvc.perform(get("/api/v1/products")
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + branchManagerTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + branchManagerTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + branchManagerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleProductRequest("BMGR-NO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Newly registered tenant OWNER satisfies 6 canonical predicates and receives all 33 permissions on /api/v1/me/permissions")
    void newlyRegisteredOwnerReceivesFullCatalogOnMePermissions() throws Exception {
        assertThat(assignmentRepository.countEligibleOwners(tenantAId, null, null, null)).isEqualTo(1);
        assertThat(assignmentRepository.findUnbootstrappedActiveTenantIds()).doesNotContain(tenantAId);

        mockMvc.perform(get("/api/v1/me/permissions")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(tenantAId.toString()))
                .andExpect(jsonPath("$.data.tenantPermissions.length()").value(33))
                .andExpect(jsonPath("$.data.tenantPermissions", hasItem("tenancy.branches.read")))
                .andExpect(jsonPath("$.data.tenantPermissions", hasItem("identity.roles.read")))
                .andExpect(jsonPath("$.data.tenantPermissions", hasItem("catalog.products.read")))
                .andExpect(jsonPath("$.data.tenantPermissions", hasItem("catalog.products.delete")))
                .andExpect(jsonPath("$.data.tenantPermissions", hasItem("catalog.categories.manage")));

        mockMvc.perform(get("/api/v1/branches").header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/role-assignments").header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cross-tenant resource IDs fail closed with 404 Not Found across all catalog endpoints")
    void crossTenantResourceAccessFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", tenantAProductId)
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/products/{pId}/variants/{vId}", tenantAProductId, tenantAVariantId)
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}",
                        tenantAProductId, tenantAVariantId, tenantABarcodeId)
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/units/{id}", tenantAPcsId)
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/products/lookup")
                        .param("code", "4609991112223")
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());

        // Cross-tenant branchId passed by Tenant B OWNER -> 403 Forbidden
        mockMvc.perform(get("/api/v1/products")
                        .param("branchId", branchA1.getId().toString())
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isForbidden());
    }

    private CreateProductRequest sampleProductRequest(String sku) {
        return new CreateProductRequest(
                null, tenantAPcsId, "Test Item " + sku, null, null, ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantRequest(sku, "Default", Map.of(), BigDecimal.ONE, BigDecimal.TEN, true, List.of()))
        );
    }

    private String createUserWithRole(String username, String roleCode, RoleScopeType scopeType, UUID branchId) {
        User user = createUser(username, UserStatus.ACTIVE);
        TenantMembership mem = createMembership(tenantAId, user.getId(), MembershipStatus.ACTIVE);
        assignRole(mem.getId(), roleCode, scopeType, branchId);
        return jwtTokenProvider.generateTenantAccessToken(user.getId(), user.getEmail(), tenantAId, mem.getId());
    }

    private User createUser(String username, UserStatus status) {
        return userRepository.save(User.builder()
                .email(username + "@example.com")
                .username(username)
                .passwordHash("hashed")
                .firstName("Test")
                .lastName("User")
                .status(status)
                .build());
    }

    private TenantMembership createMembership(UUID tenantId, UUID userId, MembershipStatus status) {
        return membershipRepository.save(TenantMembership.builder()
                .tenantId(tenantId)
                .userId(userId)
                .status(status)
                .build());
    }

    private void assignRole(UUID membershipId, String roleCode, RoleScopeType scopeType, UUID branchId) {
        Role role = roleRepository.findByTenantIdAndCode(tenantAId, roleCode).orElseThrow();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(membershipId)
                .roleId(role.getId())
                .scopeType(scopeType)
                .branchId(branchId)
                .isActive(true)
                .build();
        assignment.setTenantId(tenantAId);
        assignmentRepository.save(assignment);
    }
}
