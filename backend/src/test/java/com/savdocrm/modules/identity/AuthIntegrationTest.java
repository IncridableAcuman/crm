package com.savdocrm.modules.identity;

import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.LoginRequest;
import com.savdocrm.modules.identity.application.dto.RefreshTokenRequest;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.application.dto.SelectTenantRequest;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.infrastructure.RefreshTokenRepository;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
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
import org.springframework.http.MediaType;
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
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_auth_test")
            .withUsername("auth_test_user")
            .withPassword("auth_test_password");

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
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private com.savdocrm.modules.tenancy.infrastructure.BranchRepository branchRepository;

    @Autowired
    private TenantMembershipRepository tenantMembershipRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        tenantMembershipRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should successfully register a new user with store and issue tenant-scoped token")
    void shouldRegisterUserSuccessfullyWithStore() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("retailer@example.com")
                .username("retailer_bob")
                .password("StrongPassword123!")
                .firstName("Bob")
                .lastName("Smith")
                .phoneNumber("+1234567890")
                .storeName("Bob Grocery Store")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.email").value("retailer@example.com"))
                .andExpect(jsonPath("$.data.user.username").value("retailer_bob"))
                .andExpect(jsonPath("$.data.activeTenant.tenantName").value("Bob Grocery Store"))
                .andExpect(jsonPath("$.data.availableTenants.length()").value(1));

        assertThat(userRepository.findByEmailIgnoreCase("retailer@example.com")).isPresent();
        assertThat(tenantRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Should successfully register a global user without store")
    void shouldRegisterUserWithoutStore() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("solouser@example.com")
                .username("solouser")
                .password("Password123!")
                .firstName("Alice")
                .lastName("Wonder")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.activeTenant").doesNotExist())
                .andExpect(jsonPath("$.data.availableTenants.length()").value(0));
    }

    @Test
    @DisplayName("Should reject registration with duplicate email case-insensitively")
    void shouldRejectRegistrationWithDuplicateEmailCaseInsensitive() throws Exception {
        RegisterRequest first = RegisterRequest.builder()
                .email("test.duplicate@example.com")
                .username("dupuser1")
                .password("Password123!")
                .firstName("First")
                .lastName("User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        RegisterRequest duplicate = RegisterRequest.builder()
                .email("TEST.DUPLICATE@EXAMPLE.COM")
                .username("dupuser2")
                .password("Password123!")
                .firstName("Second")
                .lastName("User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Should reject registration with invalid payload")
    void shouldRejectRegistrationWithInvalidPayload() throws Exception {
        RegisterRequest invalid = RegisterRequest.builder()
                .email("not-an-email")
                .username("ab") // min 3 chars
                .password("short") // min 8 chars
                .firstName("")
                .lastName("")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Should login successfully with email or username and return global token")
    void shouldLoginSuccessfullyWithEmailOrUsername() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("logintest@example.com")
                .username("loginuser")
                .password("LoginPass123!")
                .firstName("Login")
                .lastName("Tester")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        // Login using email
        LoginRequest emailLogin = LoginRequest.builder()
                .emailOrUsername("LOGINTEST@EXAMPLE.COM")
                .password("LoginPass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.username").value("loginuser"));

        // Login using username
        LoginRequest usernameLogin = LoginRequest.builder()
                .emailOrUsername("loginuser")
                .password("LoginPass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usernameLogin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject login with invalid password")
    void shouldRejectLoginWithInvalidPassword() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("badpwd@example.com")
                .username("badpwduser")
                .password("CorrectPassword123!")
                .firstName("Bad")
                .lastName("Pass")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest badLogin = LoginRequest.builder()
                .emailOrUsername("badpwd@example.com")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication Failed"));
    }

    @Test
    @DisplayName("Should select active tenant store and issue tenant-scoped access token")
    void shouldSelectTenantSuccessfullyAndIssueTenantScopedToken() throws Exception {
        // Register user with store
        RegisterRequest register = RegisterRequest.builder()
                .email("multitenant@example.com")
                .username("multitenant_user")
                .password("TenantPass123!")
                .firstName("Multi")
                .lastName("Tenant")
                .storeName("Super Store")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = regResult.getResponse().getContentAsString();
        UUID tenantId = UUID.fromString(objectMapper.readTree(body).get("data").get("activeTenant").get("tenantId").asText());

        // Perform login to get fresh global token
        LoginRequest login = LoginRequest.builder()
                .emailOrUsername("multitenant@example.com")
                .password("TenantPass123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String globalToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Select tenant
        SelectTenantRequest selectRequest = SelectTenantRequest.builder()
                .tenantId(tenantId)
                .build();

        mockMvc.perform(post("/api/v1/auth/select-tenant")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.activeTenant.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.data.activeTenant.current").value(true));
    }

    @Test
    @DisplayName("Should reject tenant selection when user is not a member")
    void shouldRejectTenantSelectionWhenNotMember() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("unauthorized.tenant@example.com")
                .username("unauthtenant")
                .password("SecretPass123!")
                .firstName("No")
                .lastName("Member")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Create a different tenant directly
        Tenant alienTenant = tenantRepository.save(Tenant.builder()
                .name("Alien Store")
                .code("ALIEN")
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build());

        SelectTenantRequest selectRequest = SelectTenantRequest.builder()
                .tenantId(alienTenant.getId())
                .build();

        mockMvc.perform(post("/api/v1/auth/select-tenant")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should rotate refresh token and revoke previous token")
    void shouldRotateRefreshTokenSuccessfully() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("rotatetest@example.com")
                .username("rotateuser")
                .password("RotatePass123!")
                .firstName("Rotate")
                .lastName("Token")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String initialRefreshToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        RefreshTokenRequest refreshReq = RefreshTokenRequest.builder()
                .refreshToken(initialRefreshToken)
                .build();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();

        String newRefreshToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);
    }

    @Test
    @DisplayName("Should detect refresh token reuse and immediately revoke entire token family")
    void shouldDetectRefreshTokenReuseAndRevokeEntireFamily() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("reusetest@example.com")
                .username("reuseuser")
                .password("ReusePass123!")
                .firstName("Reuse")
                .lastName("Detection")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String token1 = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        // 1st Refresh: uses token1, issues token2, revokes token1
        RefreshTokenRequest refreshReq1 = RefreshTokenRequest.builder().refreshToken(token1).build();
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isOk())
                .andReturn();

        String token2 = objectMapper.readTree(res1.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        // ATTACKER (or replay): presents token1 again!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token: token reuse detected."));

        // LEGITIMATE USER: tries to use token2 now -> must be rejected because entire family was revoked!
        RefreshTokenRequest refreshReq2 = RefreshTokenRequest.builder().refreshToken(token2).build();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq2)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should revoke refresh token on logout")
    void shouldLogoutAndRevokeRefreshToken() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("logouttest@example.com")
                .username("logoutuser")
                .password("LogoutPass123!")
                .firstName("Log")
                .lastName("Out")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String refreshToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        RefreshTokenRequest logoutReq = RefreshTokenRequest.builder().refreshToken(refreshToken).build();
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk());

        // Subsequent refresh must be rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should access /me with valid JWT and reject unauthenticated requests")
    void shouldAccessProtectedEndpointWithTokenAndRejectWithoutToken() throws Exception {
        // Unauthenticated request -> 401
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        // Register
        RegisterRequest register = RegisterRequest.builder()
                .email("metest@example.com")
                .username("meuser")
                .password("MePassword123!")
                .firstName("Me")
                .lastName("User")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Authenticated request -> 200
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value("metest@example.com"))
                .andExpect(jsonPath("$.data.user.username").value("meuser"));

        // ThreadLocal must always be clean
        assertThat(TenantContextHolder.getContext()).isEmpty();
    }
}
