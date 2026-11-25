package com.savdocrm.modules.catalog;

import com.savdocrm.modules.catalog.application.CatalogTenantBootstrapListener;
import com.savdocrm.modules.catalog.application.UnitOfMeasureService;
import com.savdocrm.modules.catalog.infrastructure.persistence.UnitOfMeasureRepository;
import com.savdocrm.modules.identity.application.AuthService;
import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.tenancy.application.TenantService;
import com.savdocrm.modules.tenancy.application.dto.CreateTenantCommand;
import com.savdocrm.modules.tenancy.application.dto.TenantDto;
import com.savdocrm.modules.tenancy.domain.event.TenantCreatedEvent;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

    @Autowired
    private TenantService tenantService;

    @Autowired
    private CatalogTenantBootstrapListener bootstrapListener;

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

    @Test
    @DisplayName("Failed UOM initialization rolls back TenantService.createTenant transaction")
    void failedUomBootstrap_rollsBackTenantCreation() {
        long tenantsBefore = tenantRepository.count();
        long unitsBefore = unitRepository.count();

        doThrow(new IllegalStateException("Simulated TenantService UOM failure"))
                .when(unitOfMeasureService)
                .initializeStandardUnitsForCurrentTenant();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        assertThatThrownBy(() -> tenantService.createTenant(
                new CreateTenantCommand("Rollback Tenant " + suffix, "rb-" + suffix, "USD", "UTC")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated TenantService UOM failure");

        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore);
        assertThat(unitRepository.count()).isEqualTo(unitsBefore);
    }

    @Test
    @DisplayName("Tenant registration invokes UOM bootstrap exactly once and repeated event delivery is idempotent")
    void singleInitializationPerTenantAndRepeatedEventIdempotent() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse response = authService.register(RegisterRequest.builder()
                .email("once_" + suffix + "@example.com")
                .username("once_" + suffix)
                .password("StrongPassword123!")
                .firstName("Once")
                .lastName("Owner")
                .storeName("Once Store " + suffix)
                .build(), "127.0.0.1", "JUnit");

        UUID tenantId = response.getActiveTenant().getTenantId();
        verify(unitOfMeasureService, times(1)).initializeStandardUnitsForCurrentTenant();
        assertThat(unitRepository.findAllByTenantIdOrderByIsSystemDescCodeAsc(tenantId)).hasSize(8);

        // Repeated TenantCreatedEvent delivery does not duplicate units
        bootstrapListener.onTenantCreated(new TenantCreatedEvent(tenantId));
        assertThat(unitRepository.findAllByTenantIdOrderByIsSystemDescCodeAsc(tenantId)).hasSize(8);

        // Secondary tenant creation via TenantService also runs once and creates independent units
        TenantDto second = tenantService.createTenant(
                new CreateTenantCommand("Second Store " + suffix, "sec-" + suffix, "USD", "UTC")
        );
        verify(unitOfMeasureService, times(3)).initializeStandardUnitsForCurrentTenant();
        var tenant1UnitIds = unitRepository.findAllByTenantIdOrderByIsSystemDescCodeAsc(tenantId).stream()
                .map(com.savdocrm.modules.catalog.domain.UnitOfMeasure::getId)
                .collect(java.util.stream.Collectors.toSet());
        var tenant2UnitIds = unitRepository.findAllByTenantIdOrderByIsSystemDescCodeAsc(second.id()).stream()
                .map(com.savdocrm.modules.catalog.domain.UnitOfMeasure::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(tenant2UnitIds).hasSize(8);
        assertThat(tenant2UnitIds).doesNotContainAnyElementsOf(tenant1UnitIds);

        // User registration without storeName does not trigger UOM bootstrap
        authService.register(RegisterRequest.builder()
                .email("nostore_" + suffix + "@example.com")
                .username("nostore_" + suffix)
                .password("StrongPassword123!")
                .firstName("No")
                .lastName("Store")
                .build(), "127.0.0.1", "JUnit");
        verify(unitOfMeasureService, times(3)).initializeStandardUnitsForCurrentTenant();
    }
}
