package com.emerald.member;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MemberFlywayUpgradeTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesVersionTwoDatabaseWithoutLosingExistingMemberData() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("2")).load().migrate();

        UUID memberId = UUID.randomUUID();
        JdbcTemplate jdbc = jdbc();
        jdbc.update("insert into members(id,full_name,mobile_number,email,status,created_at,updated_at) values(?,?,?,?,?,?,?)",
                memberId, "Upgrade Resident", "9876500001", null, "ACTIVE",
                java.sql.Timestamp.from(java.time.Instant.now()), java.sql.Timestamp.from(java.time.Instant.now()));

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertEquals("Upgrade Resident", jdbc.queryForObject("select full_name from members where id=?", String.class, memberId));
        assertEquals("16", jdbc.queryForObject("select max(version) from flyway_schema_history where success", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from idempotency_records", Integer.class));
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }
}
