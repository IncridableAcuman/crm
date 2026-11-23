package com.savdocrm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PostgresLiquibaseMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_test")
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
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify Liquibase migrations execute successfully on PostgreSQL 16 container")
    void shouldExecuteLiquibaseMigrationsAgainstPostgres16() {
        // Verify system_metadata table created by baseline changeset exists
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_metadata WHERE id = 'SAVDOCRM_CORE'",
                Integer.class
        );
        assertThat(count).isNotNull().isEqualTo(1);

        // Verify Liquibase internal changelog table records the execution
        Integer changesetCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id = 'v1.0.0-00-system-init'",
                Integer.class
        );
        assertThat(changesetCount).isNotNull().isEqualTo(1);
    }
}
