package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.domain.exception.TenantBootstrapNotAllowedException;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class TenantBootstrapSecurityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_bootstrap_security_test")
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
    private TenantBootstrapService tenantBootstrapService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantMembershipRepository tenantMembershipRepository;

    private Tenant testTenant;
    private User creatorUser;
    private TenantMembership creatorMembership;
    private User ordinaryUser;
    private TenantMembership ordinaryMembership;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        TenantContextHolder.clear();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        creatorUser = userRepository.save(User.builder()
                .email("creator." + suffix + "@example.com")
                .username("creator_" + suffix)
                .passwordHash("hashed")
                .firstName("Creator")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build());

        // Unbootstrapped tenant created with createdBy = creatorUser.getId()
        testTenant = Tenant.builder()
                .name("Unbootstrapped Store " + suffix)
                .code("unbootstrapped-" + suffix)
                .currencyCode("USD")
                .timezone("UTC")
                .status(TenantStatus.ACTIVE)
                .build();
        testTenant.setCreatedBy(creatorUser.getId());
        testTenant = tenantRepository.save(testTenant);

        creatorMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(creatorUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());

        ordinaryUser = userRepository.save(User.builder()
                .email("ordinary." + suffix + "@example.com")
                .username("ordinary_" + suffix)
                .passwordHash("hashed")
                .firstName("Ordinary")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build());

        ordinaryMembership = tenantMembershipRepository.save(TenantMembership.builder()
                .tenantId(testTenant.getId())
                .userId(ordinaryUser.getId())
                .status(MembershipStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Security: No public HTTP endpoint allows unauthenticated bootstrap execution")
    void shouldEnsureNoPublicBootstrapEndpointExists() throws Exception {
        // Attempting to invoke hypothetical bootstrap endpoints should be 401 Unauthorized or 404 Not Found
        mockMvc.perform(post("/api/v1/system/tenants/" + testTenant.getId() + "/bootstrap-owner"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/tenants/" + testTenant.getId() + "/bootstrap"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Ordinary user cannot bootstrap themselves to OWNER (TenantBootstrapNotAllowedException)")
    void shouldRejectOrdinaryUserFromBootstrappingOwner() {
        assertThatThrownBy(() -> tenantBootstrapService.bootstrapExistingTenantOwner(
                testTenant.getId(), ordinaryMembership.getId(), ordinaryUser.getId()))
                .isInstanceOf(TenantBootstrapNotAllowedException.class)
                .hasMessageContaining("Ordinary tenant users/administrators cannot bootstrap or self-elevate to OWNER.");
    }

    @Test
    @DisplayName("Security: Non-creator user cannot bootstrap creator to OWNER")
    void shouldRejectNonCreatorActorFromBootstrappingCreator() {
        assertThatThrownBy(() -> tenantBootstrapService.bootstrapExistingTenantOwner(
                testTenant.getId(), creatorMembership.getId(), ordinaryUser.getId()))
                .isInstanceOf(TenantBootstrapNotAllowedException.class)
                .hasMessageContaining("Ordinary tenant users/administrators cannot bootstrap or self-elevate to OWNER.");
    }

    @Test
    @DisplayName("Security: Verified tenant creator can bootstrap their own membership to OWNER")
    void shouldAllowTenantCreatorToBootstrapOwner() {
        TenantBootstrapResult result = tenantBootstrapService.bootstrapExistingTenantOwner(
                testTenant.getId(), creatorMembership.getId(), creatorUser.getId());

        assertThat(result).isEqualTo(TenantBootstrapResult.SUCCESS);

        // Subsequent call is idempotent
        TenantBootstrapResult secondResult = tenantBootstrapService.bootstrapExistingTenantOwner(
                testTenant.getId(), creatorMembership.getId(), creatorUser.getId());
        assertThat(secondResult).isEqualTo(TenantBootstrapResult.ALREADY_BOOTSTRAPPED);
    }
}
