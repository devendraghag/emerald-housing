package com.emerald.society.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.emerald.society.dto.FlatResponse;
import com.emerald.society.dto.PageResponse;
import com.emerald.society.repository.FlatRepository;
import com.emerald.society.repository.FloorRepository;
import com.emerald.society.repository.SocietyRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FlatServiceReadQueryTest {
    @Mock private FlatRepository flats;
    @Mock private FloorRepository floors;
    @Mock private SocietyRepository societies;

    @Test
    void listUsesSingleFlatAndFloorProjectionInsteadOfLookingUpEachFloor() {
        var expected = List.of(new FlatResponse(UUID.randomUUID(), "A-101", UUID.randomUUID(), 1, "ACTIVE"));
        when(flats.findAllResponsesByFlatNumberAsc()).thenReturn(expected);
        var service = new FlatService(flats, floors, societies);

        assertEquals(expected, service.list());
        verify(flats).findAllResponsesByFlatNumberAsc();
        verifyNoInteractions(floors, societies);
    }

    @Test
    void pagedListUsesSingleFlatAndFloorProjectionInsteadOfLookingUpEachFloor() {
        var expected = new FlatResponse(UUID.randomUUID(), "A-101", UUID.randomUUID(), 1, "ACTIVE");
        var pageable = PageRequest.of(0, 20);
        when(flats.findAllResponses(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expected), pageable, 1));
        var service = new FlatService(flats, floors, societies);

        PageResponse<FlatResponse> result = service.page(0, 20, "flatNumber", "asc");

        assertEquals(List.of(expected), result.content());
        verify(flats).findAllResponses(any(Pageable.class));
        verifyNoInteractions(floors, societies);
    }
}
