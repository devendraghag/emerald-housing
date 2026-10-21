package com.emerald.society.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.emerald.society.dto.*;
import com.emerald.society.exception.*;
import com.emerald.society.repository.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlatServiceFailureTest {
    @Test void staleVersionCannotMutateOrSaveFlat() {
        var id = UUID.randomUUID();
        var flat = new com.emerald.society.entity.FlatEntity(id, UUID.randomUUID(), UUID.randomUUID(), "101", "ACTIVE", java.time.Instant.EPOCH);
        when(flats.findById(id)).thenReturn(java.util.Optional.of(flat));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "versions", new com.emerald.society.api.VersionPreconditions(true));
        var stale = com.emerald.society.api.VersionPreconditions.tag("flat", id, 9);
        var failure = assertThrows(com.emerald.society.api.ApiFailure.class, () -> service.update(id, new UpdateFlatRequest(2, "201"), stale));
        assertEquals(412, failure.status());
        assertEquals("101", flat.getFlatNumber());
        verify(flats, never()).saveAndFlush(any());
        verifyNoInteractions(floors);
    }

    @Test void flatRequestsRejectInvalidFloorsAndBlankNumbers() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new CreateFlatRequest(0, "101")).isEmpty());
            assertFalse(validator.validate(new CreateFlatRequest(19, "101")).isEmpty());
            assertFalse(validator.validate(new CreateFlatRequest(1, " ")).isEmpty());
            assertFalse(validator.validate(new UpdateFlatRequest(1, "a".repeat(41))).isEmpty());
            assertTrue(validator.validate(new CreateFlatRequest(1, "101")).isEmpty());
            assertTrue(validator.validate(new UpdateFlatRequest(18, "1801")).isEmpty());
        }
    }

    private final FlatRepository flats = mock(FlatRepository.class);
    private final FloorRepository floors = mock(FloorRepository.class);
    private final SocietyRepository societies = mock(SocietyRepository.class);
    private final FlatService service = new FlatService(flats, floors, societies);

    @Test void missingFloorPreventsFlatCreation() {
        assertThrows(FloorEntityNotFoundException.class, () -> service.create(new CreateFlatRequest(1, "101")));
        verifyNoInteractions(flats, societies);
    }

    @Test void missingFlatCannotBeUpdated() {
        assertThrows(FlatNotFoundException.class, () -> service.update(UUID.randomUUID(), new UpdateFlatRequest(1, "101")));
        verify(flats, never()).saveAndFlush(any());
        verifyNoInteractions(floors, societies);
    }

    @Test void missingFlatCannotBeDeactivated() {
        assertThrows(FlatNotFoundException.class, () -> service.deactivate(UUID.randomUUID()));
        verify(flats, never()).saveAndFlush(any());
    }

    @Test void missingFloorCannotReturnAFlatList() {
        assertThrows(FloorEntityNotFoundException.class, () -> service.listByFloorNumber(18));
        verifyNoInteractions(flats);
    }
}
