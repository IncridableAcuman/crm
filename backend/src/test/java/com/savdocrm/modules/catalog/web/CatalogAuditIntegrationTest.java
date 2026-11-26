package com.savdocrm.modules.catalog.web;

import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.web.dto.request.AddBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.BarcodeInputRequest;
import com.savdocrm.modules.catalog.web.dto.request.ConvertProductToVariableRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateVariantRequest;
import com.savdocrm.modules.catalog.web.dto.request.MoveCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateActivationStatusRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductStatusRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateVariantRequest;
import com.savdocrm.modules.identity.application.PermissionAuthorizationService;
import com.savdocrm.modules.identity.application.dto.LoginRequest;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.application.dto.SelectTenantRequest;
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
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class CatalogAuditIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "testSecretKeyForJwtAuthenticationIntegrationTestingSavdoCrm1234567890!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_catalog_audit_test")
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
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantMembershipRepository membershipRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PermissionAuthorizationService authorizationService;

    private MockMvc mockMvc;

    private String ownerAEmail;
    private UUID tenantAId;
    private UUID tenantBId;
    private String ownerTokenA;
    private String ownerTokenB;
    private UUID branchAId;
    private UUID pcsAId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        TenantContextHolder.clear();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ownerAEmail = "audit_a_" + suffix + "@example.com";
        RegisterRequest regA = RegisterRequest.builder()
                .email(ownerAEmail)
                .username("audit_a_" + suffix)
                .password("StrongPassword123!")
                .firstName("Audit")
                .lastName("OwnerA")
                .storeName("Audit Store A " + suffix)
                .build();

        MvcResult regARes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regA)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode regAData = objectMapper.readTree(regARes.getResponse().getContentAsString()).get("data");
        ownerTokenA = regAData.get("accessToken").asText();
        tenantAId = UUID.fromString(regAData.get("activeTenant").get("tenantId").asText());
        branchAId = branchRepository.findAllByTenantIdAndIsActiveTrue(tenantAId).get(0).getId();

        MvcResult unitsARes = mockMvc.perform(get("/api/v1/units")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode unit : objectMapper.readTree(unitsARes.getResponse().getContentAsString()).get("data")) {
            if ("PCS".equals(unit.get("code").asText())) {
                pcsAId = UUID.fromString(unit.get("id").asText());
                break;
            }
        }
        assertThat(pcsAId).isNotNull();

        RegisterRequest regB = RegisterRequest.builder()
                .email("audit_b_" + suffix + "@example.com")
                .username("audit_b_" + suffix)
                .password("StrongPassword123!")
                .firstName("Audit")
                .lastName("OwnerB")
                .storeName("Audit Store B " + suffix)
                .build();
        MvcResult regBRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode regBData = objectMapper.readTree(regBRes.getResponse().getContentAsString()).get("data");
        ownerTokenB = regBData.get("accessToken").asText();
        tenantBId = UUID.fromString(regBData.get("activeTenant").get("tenantId").asText());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("4.1 Auth lifecycle, expired/invalid tokens, suspended tenant, logout revocation, and tenant spoofing defense")
    void authLifecycleTenantSpoofingAndSuspension() throws Exception {
        // Invalid credentials -> 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ownerAEmail, "WrongPassword!"))))
                .andExpect(status().isUnauthorized());

        // Valid login -> global token -> catalog blocked (403)
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ownerAEmail, "StrongPassword123!"))))
                .andExpect(status().isOk())
                .andReturn();
        String globalToken = objectMapper.readTree(loginRes.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
        Cookie refreshCookie = loginRes.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(refreshCookie).isNotNull();

        // Spoofed X-Tenant-Id header cannot bypass missing tenant context
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + globalToken)
                        .header("X-Tenant-Id", tenantAId.toString()))
                .andExpect(status().isForbidden());

        // Invalid tenant selection (Tenant B where user A has no membership) -> 403
        mockMvc.perform(post("/api/v1/auth/select-tenant")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantBId))))
                .andExpect(status().isForbidden());

        // Non-existent tenant selection -> 400
        mockMvc.perform(post("/api/v1/auth/select-tenant")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(UUID.randomUUID()))))
                .andExpect(status().isBadRequest());

        // Valid tenant selection -> tenant token + rotated refresh cookie
        MvcResult selectRes = mockMvc.perform(post("/api/v1/auth/select-tenant")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantAId))))
                .andExpect(status().isOk())
                .andReturn();
        String selectedTokenA = objectMapper.readTree(selectRes.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
        Cookie tenantRefreshCookie = selectRes.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(tenantRefreshCookie).isNotNull();

        // Fabricated tenantId in JSON body, query param, AND X-Tenant-Id header while authenticated as Tenant A
        // must be ignored: resource belongs strictly to Tenant A and is invisible to Tenant B
        String spoofedPayload = """
                {
                  "tenantId": "%s",
                  "unitId": "%s",
                  "name": "Spoof Guard Item",
                  "productType": "SIMPLE",
                  "variantOptions": [],
                  "status": "ACTIVE",
                  "variants": [{
                    "sku": "SPOOF-01",
                    "name": "Default",
                    "optionValues": {},
                    "costPrice": 1.00,
                    "sellingPrice": 5.00,
                    "isDefault": true,
                    "barcodes": []
                  }]
                }
                """.formatted(tenantBId, pcsAId);

        MvcResult createdSpoofRes = mockMvc.perform(post("/api/v1/products")
                        .param("tenantId", tenantBId.toString())
                        .header("Authorization", "Bearer " + selectedTokenA)
                        .header("X-Tenant-Id", tenantBId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofedPayload))
                .andExpect(status().isCreated())
                .andReturn();
        UUID createdInAId = UUID.fromString(objectMapper.readTree(createdSpoofRes.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Tenant B cannot see the item; Tenant A sees it
        mockMvc.perform(get("/api/v1/products/{id}", createdInAId)
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/{id}", createdInAId)
                        .header("Authorization", "Bearer " + selectedTokenA)
                        .header("X-Tenant-Id", tenantBId.toString()))
                .andExpect(status().isOk());

        // Expired & malformed JWT tokens -> 401
        String expiredJwt = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", ownerAEmail)
                .claim("tenantId", tenantAId.toString())
                .claim("tokenType", "ACCESS")
                .issuer("savdocrm-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());

        // Logout revokes refresh session; subsequent refresh fails (401)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Origin", "http://localhost:3000")
                        .cookie(tenantRefreshCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(tenantRefreshCookie))
                .andExpect(status().isUnauthorized());

        // Suspended tenant rejected on both live API requests and select-tenant (403)
        Tenant tenantA = tenantRepository.findById(tenantAId).orElseThrow();
        tenantA.suspend();
        tenantRepository.saveAndFlush(tenantA);

        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + selectedTokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/select-tenant")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantAId))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4.2–4.4 Cross-tenant mutations, inactive role assignment, unknown permission, and branch write blocks")
    void crossTenantMutationsAndRbacEdgeCases() throws Exception {
        // Seed category, custom unit, and product in Tenant A
        UUID catAId = createCategory(ownerTokenA, null, "ELEC", "Electronics");
        UUID unitAId = createUnit(ownerTokenA, "CASE", "Case", "cs", false, 0);
        MvcResult prodRes = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                catAId, unitAId, "Phone X", "Smart", "TechCo",
                                ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                                List.of(new CreateVariantRequest(
                                        "PHX-1", "Default", Map.of(), BigDecimal.TEN, BigDecimal.valueOf(500), true,
                                        List.of(new BarcodeInputRequest("4607778889991", BarcodeType.EAN13, true))
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode prodData = objectMapper.readTree(prodRes.getResponse().getContentAsString()).get("data");
        UUID prodAId = UUID.fromString(prodData.get("id").asText());
        UUID varAId = UUID.fromString(prodData.get("variants").get(0).get("id").asText());
        UUID barAId = UUID.fromString(prodData.get("variants").get(0).get("barcodes").get(0).get("id").asText());

        // Tenant B list/search/tree cannot see Tenant A items
        mockMvc.perform(get("/api/v1/products").param("search", "Phone X")
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/categories/tree")
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Tenant B cannot read or mutate/archive/delete ANY Tenant A catalog resource (404 Not Found)
        mockMvc.perform(get("/api/v1/categories/{id}", catAId).header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/categories/{id}", catAId)
                        .header("Authorization", "Bearer " + ownerTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCategoryRequest("Hacked", null, 0))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/categories/{id}", catAId).header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/units/{id}", unitAId)
                        .header("Authorization", "Bearer " + ownerTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUnitRequest("Hacked", "hk", false, 0))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/units/{id}", unitAId).header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/products/{id}", prodAId)
                        .header("Authorization", "Bearer " + ownerTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductRequest(null, pcsAId, "Hacked", null, null))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/products/{id}/archive", prodAId).header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/products/{id}", prodAId).header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/products/{pId}/variants/{vId}", prodAId, varAId)
                        .header("Authorization", "Bearer " + ownerTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVariantRequest("HACK", "Hacked", Map.of(), BigDecimal.ONE, BigDecimal.ONE))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}", prodAId, varAId, barAId)
                        .header("Authorization", "Bearer " + ownerTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBarcodeRequest("999", BarcodeType.OTHER, true))))
                .andExpect(status().isNotFound());

        // Inactive role assignment -> 403
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User inactiveRoleUser = userRepository.save(User.builder()
                .email("inact_role_" + suffix + "@example.com")
                .username("inact_role_" + suffix)
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .firstName("Inact")
                .lastName("Role")
                .status(UserStatus.ACTIVE)
                .build());
        TenantMembership mem = membershipRepository.save(TenantMembership.builder()
                .tenantId(tenantAId)
                .userId(inactiveRoleUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());
        Role viewerRole = roleRepository.findByTenantIdAndCode(tenantAId, SystemRoles.VIEWER).orElseThrow();
        UserRoleAssignment inactiveAssignment = UserRoleAssignment.builder()
                .membershipId(mem.getId())
                .roleId(viewerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .isActive(false)
                .build();
        inactiveAssignment.setTenantId(tenantAId);
        assignmentRepository.saveAndFlush(inactiveAssignment);

        String inactiveRoleToken = jwtTokenProvider.generateTenantAccessToken(
                inactiveRoleUser.getId(), inactiveRoleUser.getEmail(), tenantAId, mem.getId()
        );
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + inactiveRoleToken))
                .andExpect(status().isForbidden());

        // Unknown permission code fails closed
        assertThat(authorizationService.hasPermission(tenantAId, mem.getId(), "catalog.unknown.action")).isFalse();

        // Branch-only manager cannot mutate categories or units (TENANT write gate)
        User branchMgr = userRepository.save(User.builder()
                .email("bmgr_" + suffix + "@example.com")
                .username("bmgr_" + suffix)
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .firstName("Branch")
                .lastName("Mgr")
                .status(UserStatus.ACTIVE)
                .build());
        TenantMembership bmMem = membershipRepository.save(TenantMembership.builder()
                .tenantId(tenantAId)
                .userId(branchMgr.getId())
                .status(MembershipStatus.ACTIVE)
                .build());
        Role mgrRole = roleRepository.findByTenantIdAndCode(tenantAId, SystemRoles.MANAGER).orElseThrow();
        UserRoleAssignment bmAssign = UserRoleAssignment.builder()
                .membershipId(bmMem.getId())
                .roleId(mgrRole.getId())
                .scopeType(RoleScopeType.BRANCH)
                .branchId(branchAId)
                .isActive(true)
                .build();
        bmAssign.setTenantId(tenantAId);
        assignmentRepository.saveAndFlush(bmAssign);
        String branchMgrToken = jwtTokenProvider.generateTenantAccessToken(
                branchMgr.getId(), branchMgr.getEmail(), tenantAId, bmMem.getId()
        );

        mockMvc.perform(get("/api/v1/categories/{id}", catAId)
                        .param("branchId", branchAId.toString())
                        .header("Authorization", "Bearer " + branchMgrToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/units/{id}", unitAId)
                        .param("branchId", branchAId.toString())
                        .header("Authorization", "Bearer " + branchMgrToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + branchMgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest(null, "BM", "Blocked", null, 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/products/{id}", prodAId)
                        .header("Authorization", "Bearer " + branchMgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductRequest(null, pcsAId, "Blocked", null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5.1–5.5 Complete catalog business workflows & invariant enforcement (archived barcode lock, category depth/inactive move, UOM reference guards)")
    void fullBusinessWorkflowsAndInvariants() throws Exception {
        // 5.5 Units: custom create, precision narrowing guard, referenced delete guard
        UUID customUnitId = createUnit(ownerTokenA, "BOX24", "Box 24", "bx", true, 2);

        // 5.4 Categories: 5-level depth 0..4, reject 6th level, reject inactive parent move, reject deleting referenced
        UUID d0 = createCategory(ownerTokenA, null, "D0", "Depth 0");
        UUID d1 = createCategory(ownerTokenA, d0, "D1", "Depth 1");
        UUID d2 = createCategory(ownerTokenA, d1, "D2", "Depth 2");
        UUID d3 = createCategory(ownerTokenA, d2, "D3", "Depth 3");
        UUID d4 = createCategory(ownerTokenA, d3, "D4", "Depth 4");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest(d4, "D5", "Too Deep", null, 0))))
                .andExpect(status().isBadRequest());

        UUID inactiveCat = createCategory(ownerTokenA, null, "INACT", "Inactive Parent");
        mockMvc.perform(post("/api/v1/categories/{id}/deactivate", inactiveCat)
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/categories/{id}/move", d4)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveCategoryRequest(inactiveCat))))
                .andExpect(status().isBadRequest());

        // 5.1–5.3 Product lifecycle, multiple-default rejection, archived barcode lock
        // Creating product with 2 explicit defaults -> 409 Conflict
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                d0, customUnitId, "Two Defaults", null, null, ProductType.VARIABLE, List.of("Size"), ProductStatus.DRAFT,
                                List.of(
                                        new CreateVariantRequest("TD-1", "S", Map.of("Size", "S"), BigDecimal.ONE, BigDecimal.TEN, true, List.of()),
                                        new CreateVariantRequest("TD-2", "M", Map.of("Size", "M"), BigDecimal.ONE, BigDecimal.TEN, true, List.of())
                                )
                        ))))
                .andExpect(status().isConflict());

        // Create DRAFT SIMPLE product
        MvcResult draftRes = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                d0, customUnitId, "Draft Tea", "Green", "LeafCo",
                                ProductType.SIMPLE, List.of(), ProductStatus.DRAFT,
                                List.of(new CreateVariantRequest(
                                        "TEA-01", "Default", Map.of(), BigDecimal.ONE, BigDecimal.valueOf(6), true,
                                        List.of(new BarcodeInputRequest("4601234567890", BarcodeType.EAN13, true))
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode draftJson = objectMapper.readTree(draftRes.getResponse().getContentAsString()).get("data");
        UUID prodId = UUID.fromString(draftJson.get("id").asText());
        UUID varId = UUID.fromString(draftJson.get("variants").get(0).get("id").asText());
        UUID barId = UUID.fromString(draftJson.get("variants").get(0).get("barcodes").get(0).get("id").asText());

        // Cannot delete category or unit referenced by product -> 409; cannot narrow UOM precision -> 409
        mockMvc.perform(delete("/api/v1/categories/{id}", d0).header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/units/{id}", customUnitId).header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/units/{id}", customUnitId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUnitRequest("Box 24", "bx", false, 0))))
                .andExpect(status().isConflict());

        // Update metadata -> Activate -> Archive
        mockMvc.perform(put("/api/v1/products/{id}", prodId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductRequest(d0, customUnitId, "Premium Tea", "Green", "LeafCo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Premium Tea"));

        mockMvc.perform(patch("/api/v1/products/{id}/status", prodId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductStatusRequest(ProductStatus.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Invalid restore on ACTIVE product -> 400
        mockMvc.perform(post("/api/v1/products/{id}/restore", prodId)
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isBadRequest());

        // Archive -> reject barcode mutations AND variant mutations -> 400
        mockMvc.perform(post("/api/v1/products/{id}/archive", prodId)
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/products/{pId}/variants/{vId}/barcodes", prodId, varId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddBarcodeRequest("4609998887771", BarcodeType.EAN13, false))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}", prodId, varId, barId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBarcodeRequest("4609998887772", BarcodeType.EAN13, true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}", prodId, varId, barId)
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isBadRequest());

        // Invalid transition ARCHIVED -> DRAFT -> 400
        mockMvc.perform(patch("/api/v1/products/{id}/status", prodId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductStatusRequest(ProductStatus.DRAFT))))
                .andExpect(status().isBadRequest());

        // Restore -> Convert SIMPLE -> VARIABLE -> reject invalid re-conversion
        mockMvc.perform(post("/api/v1/products/{id}/restore", prodId).header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/products/{id}/convert-to-variable", prodId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConvertProductToVariableRequest(
                                List.of("Weight"), Map.of("Weight", "100g")
                        ))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/{id}/convert-to-variable", prodId)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConvertProductToVariableRequest(
                                List.of("Flavor"), Map.of("Flavor", "Mint")
                        ))))
                .andExpect(status().isBadRequest());
    }

    private UUID createCategory(String token, UUID parentId, String code, String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest(parentId, code, name, null, 0))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText());
    }

    private UUID createUnit(
            String token,
            String code,
            String name,
            String symbol,
            boolean allowDecimal,
            int precision
    ) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUnitRequest(code, name, symbol, allowDecimal, precision))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText());
    }
}
