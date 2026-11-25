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

        // Verify changeset 07 removed successor_token_encrypted while preserving successor_token_hash
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

        // Verify changeset 08 created composite index on (revoked, expires_at)
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'refresh_tokens' AND indexname = 'idx_refresh_tokens_cleanup'",
                Integer.class
        );
        assertThat(indexCount).isNotNull().isEqualTo(1);

        // Verify M04.2 changesets 09-15 and M05.2 changesets 16-21 were executed
        for (int i = 9; i <= 21; i++) {
            String prefix = String.format("v1.0.0-%02d-", i);
            Integer migrationChangesetCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE ?",
                    Integer.class,
                    prefix + "%"
            );
            assertThat(migrationChangesetCount)
                    .as("Changeset %s must be executed", prefix)
                    .isNotNull()
                    .isEqualTo(1);
        }

        // Verify permissions table has 33 seeded permissions
        Integer permCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM permissions",
                Integer.class
        );
        assertThat(permCount).isNotNull().isEqualTo(33);
    }
}
