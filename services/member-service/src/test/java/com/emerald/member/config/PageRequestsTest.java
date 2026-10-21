package com.emerald.member.config;

import static org.junit.jupiter.api.Assertions.*;
import com.emerald.member.exception.InvalidPageRequestException;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PageRequestsTest {
    @ParameterizedTest
    @CsvSource({"-1,10", "0,0", "0,-1", "0,101"})
    void rejectsOutOfRangePages(int page, int size) {
        assertThrows(InvalidPageRequestException.class, () -> PageRequests.of(page, size, "createdAt", "asc", Set.of("createdAt")));
    }
    @Test void rejectsUnsupportedSortAndDirection() {
        assertThrows(InvalidPageRequestException.class, () -> PageRequests.of(0, 10, "password", "asc", Set.of("createdAt")));
        assertThrows(InvalidPageRequestException.class, () -> PageRequests.of(0, 10, "createdAt", "sideways", Set.of("createdAt")));
    }
    @ParameterizedTest
    @CsvSource({"1,asc", "100,DESC"})
    void acceptsSizeBoundariesAndCaseInsensitiveDirection(int size, String direction) {
        var page = PageRequests.of(0, size, "createdAt", direction, Set.of("createdAt"));
        assertEquals(size, page.getPageSize());
        assertEquals(direction.toUpperCase(java.util.Locale.ROOT), page.getSort().getOrderFor("createdAt").getDirection().name());
    }
}
