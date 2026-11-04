package com.emerald.society.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.emerald.society.entity.FloorEntity;
import com.emerald.society.exception.InvalidPageRequestException;
import com.emerald.society.repository.FloorRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class FloorServicePageTest {
    @Mock private FloorRepository floors;

    @Test
    void pageUsesSocietyScopeAndRequestedSort() {
        var floor = org.mockito.Mockito.mock(FloorEntity.class);
        when(floor.getFloorNumber()).thenReturn(4);
        var pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "floorNumber"));
        when(floors.findAllBySocietySocietyCode("EMERALD", pageable))
                .thenReturn(new PageImpl<>(List.of(floor), pageable, 1));

        var result = new FloorService(floors).getFloorsPage(0, 10, "floorNumber", "desc");

        assertEquals(List.of(4), result.content().stream().map(r -> r.floorNumber()).toList());
        assertEquals(1, result.totalElements());
        verify(floors).findAllBySocietySocietyCode("EMERALD", pageable);
    }

    @Test
    void pageRejectsUnsupportedSortFieldsBeforeQuerying() {
        assertThrows(InvalidPageRequestException.class,
                () -> new FloorService(floors).getFloorsPage(0, 10, "society.name", "asc"));
        verifyNoInteractions(floors);
    }
}
