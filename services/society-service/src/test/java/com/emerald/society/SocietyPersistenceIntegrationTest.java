package com.emerald.society;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.emerald.society.repository.FloorRepository;
import com.emerald.society.repository.SocietyRepository;
import com.emerald.society.repository.FlatRepository;
import com.emerald.society.service.FlatService;
import com.emerald.society.dto.CreateFlatRequest;
import com.emerald.society.dto.UpdateFlatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SocietyPersistenceIntegrationTest {
    @org.junit.jupiter.api.BeforeEach void authenticate() {
        var actor=new com.emerald.society.security.SessionIdentityResponse(UUID.randomUUID(),"integration-admin","ADMIN");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(actor,null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));

    }
    @org.junit.jupiter.api.AfterEach void clearAuthentication() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("app.datasource.writer.jdbc-url", POSTGRES::getJdbcUrl);
        properties.add("app.datasource.writer.username", POSTGRES::getUsername);
        properties.add("app.datasource.writer.password", POSTGRES::getPassword);
    }
    @Autowired FloorRepository floors;
    @Autowired SocietyRepository societies;
    @Autowired FlatRepository flats;
    @Autowired FlatService flatService;

    @Test void flywaySeedsEmeraldAndExactlyEighteenFloors() {
        assertTrue(societies.findBySocietyCode("EMERALD").isPresent());
        assertEquals(18, floors.count());
        assertEquals(18, floors.findAllByOrderByFloorNumberAsc().getLast().getFloorNumber());
    }

    @Test void flatCanBeCreatedUpdatedListedByFloorAndDeactivated() {
        var created = flatService.create(new CreateFlatRequest(1, "A-101"));
        assertEquals("ACTIVE", created.status());
        assertEquals(1, flatService.listByFloorNumber(1).size());
        var updated = flatService.update(created.flatId(), new UpdateFlatRequest(2, "B-201"));
        assertEquals("B-201", updated.flatNumber());
        assertEquals(2, updated.floorNumber());
        var inactive = flatService.deactivate(created.flatId());
        assertEquals("INACTIVE", inactive.status());
        assertEquals("INACTIVE", flatService.get(created.flatId()).status());
    }

    @Test void flatListingCanBePagedAndSorted() {
        flatService.create(new CreateFlatRequest(1, "A-102"));
        flatService.create(new CreateFlatRequest(1, "A-103"));
        var page = flatService.page(0, 1, "flatNumber", "asc");
        assertEquals(1, page.content().size());
        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals("A-102", page.content().getFirst().flatNumber());
    }

    @Test void databaseRejectsDuplicateFlatNumberInSociety() {
        flatService.create(new CreateFlatRequest(1, "UNIQUE-101"));
        assertThrows(DataIntegrityViolationException.class,
                () -> flatService.create(new CreateFlatRequest(2, "UNIQUE-101")));
    }

    @Test void societyAndFlatAuditTimestampsAreMapped() {
        var society = societies.findBySocietyCode("EMERALD").orElseThrow();
        var floor = floors.findByFloorNumber(1).orElseThrow();
        assertNotNull(society.getCreatedAt());
        assertNotNull(society.getUpdatedAt());
        assertNotNull(floor.getCreatedAt());
        assertNotNull(floor.getUpdatedAt());

        var created = flatService.create(new CreateFlatRequest(1, "AUDIT-101"));
        var flat = flats.findById(created.flatId()).orElseThrow();
        assertNotNull(flat.getCreatedAt());
        assertNotNull(flat.getUpdatedAt());
    }
}
