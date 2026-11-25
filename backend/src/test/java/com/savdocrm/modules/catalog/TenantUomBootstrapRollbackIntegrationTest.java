package com.savdocrm.modules.catalog;

import com.savdocrm.modules.catalog.application.UnitOfMeasureService;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.modules.identity.application.AuthService;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class TenantUomBootstrapRollbackIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_uom_rollback_test")
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
    private AuthService authService;

    @MockitoSpyBean
    private UnitOfMeasureService unitOfMeasureService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private TenantMembershipRepository membershipRepository;
    @Autowired
    private UnitOfMeasureRepository unitRepository;

    @Test
    @DisplayName("Failed UOM initialization rolls back the entire tenant registration transaction")
    void failedUomBootstrap_rollsBackTenantRegistration() {
        long usersBefore = userRepository.count();
        long tenantsBefore = tenantRepository.count();
        long branchesBefore = branchRepository.count();
        long membershipsBefore = membershipRepository.count();
        long unitsBefore = unitRepository.count();

        doThrow(new IllegalStateException("Simulated UOM bootstrap failure"))
                .when(unitOfMeasureService)
                .initializeStandardUnitsForCurrentTenant();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = RegisterRequest.builder()
                .email("rollback_" + suffix + "@example.com")
                .username("rollback_" + suffix)
                .password("StrongPassword123!")
                .firstName("Rollback")
                .lastName("Tester")
                .storeName("Rollback Store " + suffix)
                .build();

        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", "JUnit"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated UOM bootstrap failure");

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore);
        assertThat(branchRepository.count()).isEqualTo(branchesBefore);
        assertThat(membershipRepository.count()).isEqualTo(membershipsBefore);
        assertThat(unitRepository.count()).isEqualTo(unitsBefore);
    }
}
