package com.emerald.society;

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
class SocietyFlywayUpgradeTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesVersionTwoDatabaseAndRetainsExistingFlat() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("2")).load().migrate();

        UUID flatId = UUID.randomUUID();
        UUID societyId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID floorId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        JdbcTemplate jdbc = jdbc();
        jdbc.update("insert into flats(id,society_id,floor_id,flat_number) values(?,?,?,?)",
                flatId, societyId, floorId, "LEGACY-101");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertEquals("LEGACY-101", jdbc.queryForObject("select flat_number from flats where id=?", String.class, flatId));
        assertEquals("9", jdbc.queryForObject("select max(version) from flyway_schema_history where success", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events", Integer.class));
        assertEquals(0L, jdbc.queryForObject("select version from flats where id=?", Long.class, flatId));
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }
}
