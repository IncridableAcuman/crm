package com.savdocrm.modules.tenancy;

import com.savdocrm.modules.tenancy.application.BranchService;
import com.savdocrm.modules.tenancy.application.TenantService;
import com.savdocrm.modules.tenancy.application.dto.BranchDto;
import com.savdocrm.modules.tenancy.application.dto.CreateBranchCommand;
import com.savdocrm.modules.tenancy.application.dto.CreateTenantCommand;
import com.savdocrm.modules.tenancy.application.dto.TenantDto;
import com.savdocrm.modules.tenancy.domain.Branch;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.multitenancy.TenantContextMissingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TenantIsolationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_multitenancy_test")
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
    }

    @Autowired
    private TenantService tenantService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private TenantDto tenantA;
    private TenantDto tenantB;

    @BeforeEach
    void setupTenants() {
        TenantContextHolder.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantA = tenantService.createTenant(new CreateTenantCommand("Grocery Chain A " + suffix, "grocery-a-" + suffix, "USD", "UTC"));
        tenantB = tenantService.createTenant(new CreateTenantCommand("Boutique B " + suffix, "boutique-b-" + suffix, "EUR", "UTC"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Fail-Closed: Invoking tenant-scoped BranchService without active context throws TenantContextMissingException")
    void shouldFailClosedWhenContextIsMissing() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> branchService.getActiveBranches())
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("Security Violation");
    }

    @Test
    @DisplayName("Cross-Tenant Data Isolation: Tenant A cannot read Tenant B's branch data")
    void shouldIsolateBranchDataBetweenTenants() {
        // Tenant A creates Branch A
        TenantContextHolder.setContext(TenantContext.of(tenantA.id()));
        BranchDto branchA = branchService.createBranch(new CreateBranchCommand("Downtown Store", "DT-01", "123 Main St", "555-0100", true));

        // Tenant B creates Branch B
        TenantContextHolder.setContext(TenantContext.of(tenantB.id()));
        BranchDto branchB = branchService.createBranch(new CreateBranchCommand("Airport Outlet", "AIR-01", "Airport Terminal", "555-0200", true));

        // Switch back to Tenant A
        TenantContextHolder.setContext(TenantContext.of(tenantA.id()));

        // Tenant A can see Branch A
        Optional<BranchDto> foundA = branchService.getBranchById(branchA.id());
        assertThat(foundA).isPresent();
        assertThat(foundA.get().name()).isEqualTo("Downtown Store");

        // Tenant A CANNOT see Branch B (must return empty Optional)
        Optional<BranchDto> foundB = branchService.getBranchById(branchB.id());
        assertThat(foundB).isEmpty();

        // Tenant A active branches list contains only Branch A
        List<BranchDto> tenantABranches = branchService.getActiveBranches();
        assertThat(tenantABranches)
                .extracting(BranchDto::id)
                .containsExactly(branchA.id())
                .doesNotContain(branchB.id());
    }

    @Test
    @DisplayName("Cross-Tenant Mutation Defense: Tenant A cannot delete or modify Tenant B's branch")
    void shouldPreventCrossTenantDeletion() {
        // Create branch under Tenant B
        TenantContextHolder.setContext(TenantContext.of(tenantB.id()));
        BranchDto branchB = branchService.createBranch(new CreateBranchCommand("Airport Terminal", "AIR-02", "Concourse B", "555-0300", false));

        // Switch to Tenant A and attempt to delete Branch B
        TenantContextHolder.setContext(TenantContext.of(tenantA.id()));
        branchRepository.deleteByIdInCurrentTenant(branchB.id());

        // Verify Branch B still exists intact under Tenant B
        TenantContextHolder.setContext(TenantContext.of(tenantB.id()));
        Optional<BranchDto> branchBAfter = branchService.getBranchById(branchB.id());
        assertThat(branchBAfter).isPresent();
    }

    @Test
    @DisplayName("Database Constraint: Duplicate branch codes within the same tenant are rejected by unique constraint")
    void shouldRejectDuplicateBranchCodeWithinSameTenant() {
        TenantContextHolder.setContext(TenantContext.of(tenantA.id()));
        branchService.createBranch(new CreateBranchCommand("Flagship", "MAIN", "Central Ave", null, true));

        // Second branch with identical code within Tenant A must be rejected
        assertThatThrownBy(() -> branchService.createBranch(new CreateBranchCommand("Second Store", "MAIN", "Side St", null, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists for this store");
    }

    @Test
    @DisplayName("Database Isolation: Different tenants CAN use the same branch code simultaneously")
    void shouldAllowSameBranchCodeAcrossDifferentTenants() {
        // Tenant A uses code 'MAIN'
        TenantContextHolder.setContext(TenantContext.of(tenantA.id()));
        BranchDto branchA = branchService.createBranch(new CreateBranchCommand("Tenant A Flagship", "MAIN", "Central Ave", null, true));
        assertThat(branchA.code()).isEqualTo("MAIN");

        // Tenant B also uses code 'MAIN' (must succeed due to composite unique constraint uq_branches_tenant_code)
        TenantContextHolder.setContext(TenantContext.of(tenantB.id()));
        BranchDto branchB = branchService.createBranch(new CreateBranchCommand("Tenant B Flagship", "MAIN", "Broadway", null, true));
        assertThat(branchB.code()).isEqualTo("MAIN");
        assertThat(branchB.tenantId()).isEqualTo(tenantB.id());
        assertThat(branchA.tenantId()).isEqualTo(tenantA.id());
    }

    @Test
    @DisplayName("Database-Level FK Protection: Inserting branch with non-existent tenant ID fails with foreign key violation")
    void shouldEnforceDatabaseForeignKeyOnTenantId() {
        UUID nonExistentTenantId = UUID.randomUUID();

        Branch rogueBranch = Branch.builder()
                .name("Rogue Branch")
                .code("ROGUE")
                .isActive(true)
                .isMain(false)
                .build();
        rogueBranch.setTenantId(nonExistentTenantId);

        assertThatThrownBy(() -> branchRepository.saveAndFlush(rogueBranch))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
