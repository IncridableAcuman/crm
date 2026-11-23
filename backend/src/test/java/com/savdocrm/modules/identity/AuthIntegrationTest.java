package com.savdocrm.modules.identity;

import com.savdocrm.modules.identity.application.dto.LoginRequest;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.application.dto.SelectTenantRequest;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.RefreshToken;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.infrastructure.RefreshTokenRepository;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import jakarta.servlet.http.Cookie;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        registry.add("jwt.cookie-secure", () -> "false"); // local test environment
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
    private BranchRepository branchRepository;

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
    @DisplayName("Should register user, create initial store, and deliver refresh token via HttpOnly cookie")
    void shouldRegisterUserSuccessfullyWithStoreAndSetHttpOnlyCookie() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("retailer@example.com")
                .username("retailer_bob")
                .password("StrongPassword123!")
                .firstName("Bob")
                .lastName("Smith")
                .phoneNumber("+1234567890")
                .storeName("Bob Grocery Store")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()) // In-memory only; never in JSON body
                .andExpect(jsonPath("$.data.user.email").value("retailer@example.com"))
                .andExpect(jsonPath("$.data.activeTenant.tenantName").value("Bob Grocery Store"))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refreshCookie.getValue()).isNotBlank();

        assertThat(userRepository.findByEmailIgnoreCase("retailer@example.com")).isPresent();
        assertThat(tenantRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Should register a global user without initial store")
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
                .andExpect(header().exists("Set-Cookie"));
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
    @DisplayName("Should login with email or username and deliver HttpOnly refresh cookie")
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

        // Login with email
        LoginRequest emailLogin = LoginRequest.builder()
                .emailOrUsername("LOGINTEST@EXAMPLE.COM")
                .password("LoginPass123!")
                .build();

        MvcResult emailResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.user.username").value("loginuser"))
                .andReturn();

        Cookie emailCookie = emailResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(emailCookie).isNotNull();
        assertThat(emailCookie.isHttpOnly()).isTrue();

        // Login with username
        LoginRequest usernameLogin = LoginRequest.builder()
                .emailOrUsername("loginuser")
                .password("LoginPass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usernameLogin)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
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
    @DisplayName("Should select active tenant store and issue tenant-scoped token and cookie")
    void shouldSelectTenantSuccessfullyAndIssueTenantScopedToken() throws Exception {
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

        SelectTenantRequest selectRequest = SelectTenantRequest.builder()
                .tenantId(tenantId)
                .build();

        MvcResult selectResult = mockMvc.perform(post("/api/v1/auth/select-tenant")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.activeTenant.tenantId").value(tenantId.toString()))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        Cookie tenantCookie = selectResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(tenantCookie).isNotNull();
        assertThat(tenantCookie.isHttpOnly()).isTrue();
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
    @DisplayName("Should rotate refresh token via cookie and invalidate previous token")
    void shouldRotateRefreshTokenViaCookieSuccessfully() throws Exception {
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

        Cookie initialCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(initialCookie).isNotNull();

        // Rotate via cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initialCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        Cookie newCookie = refreshResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(newCookie).isNotNull();
        assertThat(newCookie.getValue()).isNotEqualTo(initialCookie.getValue());
    }

    @Test
    @DisplayName("Should handle concurrent refresh requests safely without creating inconsistent sessions or false reuse")
    void shouldHandleConcurrentRefreshRequestsGracefully() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("concurrent@example.com")
                .username("concurrentuser")
                .password("Concurrent123!")
                .firstName("Concurrent")
                .lastName("Test")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie initialCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(initialCookie).isNotNull();

        int concurrentThreads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrentThreads; i++) {
            tasks.add(() -> {
                MvcResult mvcRes = mockMvc.perform(post("/api/v1/auth/refresh")
                                .cookie(initialCookie))
                        .andReturn();
                return mvcRes.getResponse().getStatus();
            });
        }

        List<Future<Integer>> results = executor.invokeAll(tasks);
        executor.shutdown();

        // All concurrent requests in the grace window must successfully receive 200 OK
        for (Future<Integer> future : results) {
            assertThat(future.get()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Should detect refresh token reuse outside grace period and revoke entire token family")
    void shouldDetectRefreshTokenReuseOutsideGracePeriodAndRevokeEntireFamily() throws Exception {
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

        Cookie token1Cookie = regResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(token1Cookie).isNotNull();

        // 1st Refresh: uses token1, issues token2, revokes token1
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie token2Cookie = res1.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(token2Cookie).isNotNull();

        // Simulate token1 presented outside the grace period (> 10s ago)
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        for (RefreshToken r : allTokens) {
            if (r.isRevoked()) {
                r.setRevokedAt(Instant.now().minusSeconds(30));
                refreshTokenRepository.save(r);
            }
        }

        // ATTACKER: presents token1 replay outside grace window
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token: token reuse detected."));

        // LEGITIMATE USER: token2 must now also be rejected because whole family was revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token2Cookie))
                .andExpect(status().isUnauthorized());

        // Verify all tokens in family are marked revoked in database
        UUID tokenFamily = allTokens.get(0).getTokenFamily();
        List<RefreshToken> familyTokens = refreshTokenRepository.findAllByTokenFamily(tokenFamily);
        assertThat(familyTokens).allMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("Should revoke session and clear HttpOnly cookie on logout")
    void shouldLogoutAndClearCookie() throws Exception {
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

        Cookie refreshCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(refreshCookie).isNotNull();

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        // Verify cookie is cleared (Max-Age=0)
        Cookie clearedCookie = logoutResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isEqualTo(0);

        // Subsequent refresh must fail with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject requests when global user is suspended")
    void shouldRejectRequestsWhenUserIsSuspended() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("suspended.user@example.com")
                .username("suspended_user")
                .password("Password123!")
                .firstName("Suspended")
                .lastName("Account")
                .storeName("Suspended Store")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Suspend user in DB
        User user = userRepository.findByEmailIgnoreCase("suspended.user@example.com").orElseThrow();
        user.suspend();
        userRepository.save(user);

        // Subsequent request must receive 403 Forbidden
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User account is suspended, disabled, or no longer exists."));
    }

    @Test
    @DisplayName("Should reject tenant-scoped requests when tenant store is suspended")
    void shouldRejectRequestsWhenTenantIsSuspended() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("store.suspended@example.com")
                .username("store_owner")
                .password("Password123!")
                .firstName("Store")
                .lastName("Owner")
                .storeName("Suspended Retailer")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = regResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("data").get("accessToken").asText();
        UUID tenantId = UUID.fromString(objectMapper.readTree(body).get("data").get("activeTenant").get("tenantId").asText());

        // Suspend tenant store in DB
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.suspend();
        tenantRepository.save(tenant);

        // Subsequent request must receive 403 Forbidden
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tenant store is suspended, inactive, or not found."));
    }

    @Test
    @DisplayName("Should reject tenant-scoped requests when membership is suspended")
    void shouldRejectRequestsWhenMembershipIsSuspended() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("member.suspended@example.com")
                .username("member_user")
                .password("Password123!")
                .firstName("Member")
                .lastName("User")
                .storeName("Membership Store")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = regResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("data").get("accessToken").asText();
        UUID tenantId = UUID.fromString(objectMapper.readTree(body).get("data").get("activeTenant").get("tenantId").asText());
        User user = userRepository.findByEmailIgnoreCase("member.suspended@example.com").orElseThrow();

        // Suspend membership in DB
        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElseThrow();
        membership.suspend();
        tenantMembershipRepository.save(membership);

        // Subsequent request must receive 403 Forbidden
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User does not have an active membership in the active store."));
    }

    @Test
    @DisplayName("Should access /me with valid JWT and guarantee TenantContext cleanup")
    void shouldAccessProtectedEndpointWithTokenAndRejectWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

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

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value("metest@example.com"));

        // ThreadLocal cleanup guarantee: Context must be empty
        assertThat(TenantContextHolder.getContext()).isEmpty();
    }
}
