package com.emerald.member.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageRequestsTest {
    private final Set<String> allowed = Set.of("name", "createdAt");

    @Test void acceptsPageBoundsAndBothSortDirections() {
        assertEquals(0, PageRequests.of(0, 1, "name", "asc", allowed).getPageNumber());
        assertEquals(100, PageRequests.of(2, 100, "createdAt", "DESC", allowed).getPageSize());
        assertEquals(Sort.Direction.DESC, PageRequests.of(2, 100, "createdAt", "DESC", allowed).getSort().getOrderFor("createdAt").getDirection());
    }

    @Test void rejectsNegativePageAndInvalidSizeBounds() {
        assertThrows(com.emerald.member.exception.InvalidPageRequestException.class, () -> PageRequests.of(-1, 10, "name", "asc", allowed));
        assertThrows(com.emerald.member.exception.InvalidPageRequestException.class, () -> PageRequests.of(0, 0, "name", "asc", allowed));
        assertThrows(com.emerald.member.exception.InvalidPageRequestException.class, () -> PageRequests.of(0, 101, "name", "asc", allowed));
    }

    @Test void rejectsUnknownSortFieldAndDirection() {
        assertThrows(com.emerald.member.exception.InvalidPageRequestException.class, () -> PageRequests.of(0, 10, "password", "asc", allowed));
        assertThrows(com.emerald.member.exception.InvalidPageRequestException.class, () -> PageRequests.of(0, 10, "name", "sideways", allowed));
    }
}
