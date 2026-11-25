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
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private com.savdocrm.shared.security.JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.savdocrm.modules.identity.application.AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        jdbcTemplate.execute("DELETE FROM product_barcodes");
        jdbcTemplate.execute("DELETE FROM product_variants");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM product_categories");
        jdbcTemplate.execute("DELETE FROM units_of_measure");
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
    @DisplayName("Should rotate refresh token via cookie and invalidate previous token (Strict single-use)")
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

        // 1st Rotation: uses initialCookie, issues newCookie, marks old token revoked with successorTokenHash
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initialCookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        Cookie newCookie = refreshResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(newCookie).isNotNull();
        assertThat(newCookie.getValue()).isNotEqualTo(initialCookie.getValue());

        // Verify database audit lineage: old token revoked with "Rotated" reason and successor_token_hash set
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(2);
        RefreshToken oldToken = tokens.stream().filter(RefreshToken::isRevoked).findFirst().orElseThrow();
        assertThat(oldToken.getRevokedReason()).isEqualTo("Rotated");
        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(oldToken.getSuccessorTokenHash()).isNotNull();
    }

    @Test
    @DisplayName("Strict Single-Use: Replay of old token immediately revokes entire token family")
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

        Cookie token1Cookie = regResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(token1Cookie).isNotNull();

        // 1st Refresh: consumes token1, issues token2, revokes token1
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie token2Cookie = res1.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(token2Cookie).isNotNull();

        // ATTACKER / REPLAY: presents revoked token1 immediately (no grace window!)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token: token reuse detected."));

        // LEGITIMATE USER: token2 is now also rejected because whole family was revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token2Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token: token reuse detected."));

        // Verify all tokens in family are marked revoked in database
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).isNotEmpty();
        assertThat(allTokens).allMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("Strict Single-Use: Two simultaneous requests using the same old token — one succeeds, the other triggers full family revocation")
    void shouldHandleTwoSimultaneousRequestsWithSameOldTokenAndRevokeFamilyOnReplay() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("concurrent.strict@example.com")
                .username("concurrent_strict")
                .password("Concurrent123!")
                .firstName("Concurrent")
                .lastName("Strict")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie initialCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(initialCookie).isNotNull();

        // Two simultaneous requests competing for the exact same token
        int concurrentThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrentThreads; i++) {
            tasks.add(() -> {
                MvcResult mvcRes = mockMvc.perform(post("/api/v1/auth/refresh")
                                .cookie(initialCookie)
                                .header("Origin", "http://localhost:5173"))
                        .andReturn();
                return mvcRes.getResponse().getStatus();
            });
        }

        List<Future<Integer>> results = executor.invokeAll(tasks);
        executor.shutdown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : results) {
            statuses.add(future.get());
        }

        // Under strict single-use with pessimistic write locking:
        // Exactly one request succeeds (200), and the concurrent replay fails (401)
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);

        // And because of replay detection on the second request, the family is revoked
        List<RefreshToken> familyTokens = refreshTokenRepository.findAll();
        assertThat(familyTokens).isNotEmpty();
        assertThat(familyTokens).allMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("Lost Response Behavior: Retrying old token after lost response is considered reuse and revokes entire family")
    void shouldRevokeEntireFamilyWhenLostResponseIsRetried() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("lostresponse.strict@example.com")
                .username("lostresp_strict")
                .password("Password123!")
                .firstName("Lost")
                .lastName("Response")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie token1Cookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // 1. Backend successfully rotates Token 1 -> Token 2
        MvcResult rotateRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie token2Cookie = rotateRes.getResponse().getCookie("savdocrm_refresh_token");

        // 2. Scenario: Client never received the HTTP response containing Token 2 (network dropped / tab disconnected).
        // The browser still possesses Token 1 and retries with Token 1.
        // Under strict single-use rotation, Token 1 is revoked. Retrying it is treated as reuse!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token: token reuse detected."));

        // 3. Token family is revoked. Token 2 can no longer be refreshed by anyone.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token2Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());

        // 4. Client must re-authenticate (login)
        LoginRequest login = LoginRequest.builder()
                .emailOrUsername("lostresponse.strict@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
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
                        .cookie(refreshCookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        // Verify cookie is cleared (Max-Age=0)
        Cookie clearedCookie = logoutResult.getResponse().getCookie("savdocrm_refresh_token");
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isEqualTo(0);

        // Subsequent refresh must fail with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should revoke entire session family on logout and prevent restoring session")
    void shouldRevokeEntireSessionFamilyOnLogoutAndPreventRestoringSession() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("logoutfamily@example.com")
                .username("logoutfam")
                .password("Password123!")
                .firstName("Fam")
                .lastName("Revoke")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie token1Cookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // Rotate to Token 2
        MvcResult rotateRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie token2Cookie = rotateRes.getResponse().getCookie("savdocrm_refresh_token");

        // Logout using Token 2
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(token2Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());

        // Verify in DB that all tokens in the family have been revoked
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).isNotEmpty();
        assertThat(tokens).allMatch(RefreshToken::isRevoked);

        // Attempting to refresh with Token 1 or Token 2 fails with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token2Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject expired refresh token with appropriate error")
    void shouldRejectExpiredRefreshToken() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("expired@example.com")
                .username("expired_user")
                .password("Password123!")
                .firstName("Expired")
                .lastName("Token")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie refreshCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // Manually expire token in database
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        for (RefreshToken t : tokens) {
            t.setExpiresAt(Instant.now().minusSeconds(60));
            refreshTokenRepository.save(t);
        }

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token has expired."));
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

    @Test
    @DisplayName("Should fail closed with 403 when global login token accesses tenant business API")
    void shouldFailClosedWhenGlobalTokenAccessesTenantProtectedBranchEndpoint() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("globalonly@example.com")
                .username("globaluser")
                .password("Password123!")
                .firstName("Global")
                .lastName("User")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String globalToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Attempt to access tenant-protected /api/v1/branches with global token
        mockMvc.perform(get("/api/v1/branches")
                        .header("Authorization", "Bearer " + globalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Tenant Context Missing"));
    }

    @Test
    @DisplayName("Should reject global token even when malicious X-Tenant-Id header is provided")
    void shouldRejectGlobalTokenEvenWithXTenantIdHeader() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("spoofed.header@example.com")
                .username("spoofuser")
                .password("Password123!")
                .firstName("Spoof")
                .lastName("Header")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String globalToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Pass spoofed X-Tenant-Id header with global token
        mockMvc.perform(get("/api/v1/branches")
                        .header("Authorization", "Bearer " + globalToken)
                        .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Tenant Context Missing"));
    }

    @Test
    @DisplayName("Should allow tenant-scoped token to access tenant branch business API")
    void shouldAllowTenantScopedTokenToAccessBranchEndpoint() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("tenantscoped@example.com")
                .username("tenantuser")
                .password("Password123!")
                .firstName("Tenant")
                .lastName("Scoped")
                .storeName("Tenant Scoped Store")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String tenantToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Access tenant-protected /api/v1/branches with tenant-scoped token
        mockMvc.perform(get("/api/v1/branches")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Should reject tenant-scoped token when membershipId claim does not match active membership")
    void shouldRejectTenantTokenWithMismatchingMembershipIdClaim() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("mismatch.membership@example.com")
                .username("mismatchuser")
                .password("Password123!")
                .firstName("Mismatch")
                .lastName("User")
                .storeName("Store A")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = regResult.getResponse().getContentAsString();
        UUID tenantId = UUID.fromString(objectMapper.readTree(body).get("data").get("activeTenant").get("tenantId").asText());
        User user = userRepository.findByEmailIgnoreCase("mismatch.membership@example.com").orElseThrow();

        // Forge a token with valid userId and tenantId, but bogus membershipId
        UUID bogusMembershipId = UUID.randomUUID();
        String forgedToken = jwtTokenProvider.generateTenantAccessToken(
                user.getId(), user.getEmail(), tenantId, bogusMembershipId);

        mockMvc.perform(get("/api/v1/branches")
                        .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tenant membership claim missing or mismatch."));
    }

    @Test
    @DisplayName("Should reject cookie-authenticated refresh request from unauthorized origin (CSRF protection)")
    void shouldRejectCookieRefreshWithDisallowedOriginDueToCsrfProtection() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("csrf@example.com")
                .username("csrfuser")
                .password("Password123!")
                .firstName("Csrf")
                .lastName("Protection")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie refreshCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // Attack scenario: malicious website attempts cross-site POST with cookie
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", "https://malicious-attacker-site.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject cookie-authenticated refresh when Origin and Referer are both missing (CSRF protection)")
    void shouldRejectCookieRefreshWithMissingOriginAndMissingReferer() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("missingorigin@example.com")
                .username("missingorig_user")
                .password("Password123!")
                .firstName("Missing")
                .lastName("Origin")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie refreshCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // Request with cookie but WITHOUT Origin and WITHOUT Referer must be blocked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("CSRF protection: Missing Origin and Referer headers on cookie-authenticated request."));
    }

    @Test
    @DisplayName("Should reject cookie-authenticated refresh when Origin is null (sandboxed iframe CSRF attack)")
    void shouldRejectCookieRefreshWithNullOrigin() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("nullorigin@example.com")
                .username("nullorig_user")
                .password("Password123!")
                .firstName("Null")
                .lastName("Origin")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie refreshCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", "null"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject cookie-authenticated refresh when Referer is unauthorized")
    void shouldRejectCookieRefreshWithUnauthorizedReferer() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("badreferer@example.com")
                .username("badref_user")
                .password("Password123!")
                .firstName("Bad")
                .lastName("Referer")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie refreshCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // Origin absent, but unauthorized Referer present
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Referer", "https://attacker.evil.com/phishing/login"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow cookie-authenticated refresh when Origin is absent but Referer is authorized (fallback)")
    void shouldAllowCookieRefreshWithAuthorizedRefererFallback() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("goodreferer@example.com")
                .username("goodref_user")
                .password("Password123!")
                .firstName("Good")
                .lastName("Referer")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie refreshCookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // Origin absent, but permitted Referer present
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Referer", "http://localhost:5173/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should verify Liquibase migration dropped encrypted column and preserved successor_token_hash for audit")
    void shouldVerifyLiquibaseMigrationDroppedEncryptedColumnAndPreservedSuccessorHash() {
        Integer encryptedColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = 'successor_token_encrypted'",
                Integer.class
        );
        assertThat(encryptedColCount).isNotNull().isEqualTo(0);

        Integer hashColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = 'successor_token_hash'",
                Integer.class
        );
        assertThat(hashColCount).isNotNull().isEqualTo(1);
    }

    @Test
    @DisplayName("Should safely clean up expired revoked tokens older than retention cutoff while preserving active and unexpired tokens")
    void shouldSafelyCleanUpExpiredRevokedTokensOlderThanRetentionCutoff() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("cleanup.retention@example.com")
                .username("cleanup_retention")
                .password("Password123!")
                .firstName("Clean")
                .lastName("Retention")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie token1Cookie = regResult.getResponse().getCookie("savdocrm_refresh_token");

        // Rotate Token 1 -> Token 2
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());

        // Both Token 1 and Token 2 exist in DB
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(2);

        RefreshToken rotatedToken = tokens.stream().filter(RefreshToken::isRevoked).findFirst().orElseThrow();
        RefreshToken activeToken = tokens.stream().filter(t -> !t.isRevoked()).findFirst().orElseThrow();

        // Simulate Token 1 being expired beyond the 90-day retention cutoff
        rotatedToken.setExpiresAt(Instant.now().minusSeconds(86400 * 95));
        refreshTokenRepository.save(rotatedToken);

        // Run retention cleanup with 90-day cutoff
        Instant retentionCutoff = Instant.now().minusSeconds(86400 * 90);
        int purged = authService.cleanupExpiredRevokedTokens(retentionCutoff);

        assertThat(purged).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(rotatedToken.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(activeToken.getId())).isPresent();
    }
}
