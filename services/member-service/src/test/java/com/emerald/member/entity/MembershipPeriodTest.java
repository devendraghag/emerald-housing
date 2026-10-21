package com.emerald.member.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPeriodTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void openMembershipGrantsAccessStartingOnItsStartDate() {
        assertTrue(membership(TODAY.minusDays(1), null).isCurrentOn(TODAY));
        assertTrue(membership(TODAY, null).isCurrentOn(TODAY));
        assertFalse(membership(TODAY.plusDays(1), null).isCurrentOn(TODAY));
    }

    @Test
    void anyRecordedEndDateRemovesCurrentAccess() {
        for (LocalDate end : List.of(TODAY.minusDays(1), TODAY, TODAY.plusDays(1))) {
            assertFalse(membership(TODAY.minusDays(2), end).isCurrentOn(TODAY));
        }
    }

    @Test
    void staticPredicateFiltersWithTheDefaultAccessPolicy() {
        var current = membership(TODAY, null);
        var future = membership(TODAY.plusDays(1), null);
        var ended = membership(TODAY.minusDays(2), TODAY.minusDays(1));

        var result = List.of(future, current, ended).stream()
                .filter(MembershipPeriod.currentOn(TODAY))
                .map(FlatMembershipEntity::getFlatId)
                .toList();

        assertEquals(List.of(current.getFlatId()), result);
    }

    @Test
    void missingReferenceDateIsRejectedBeforeEvaluatingMemberships() {
        assertThrows(NullPointerException.class, () -> MembershipPeriod.currentOn(null));
        assertThrows(NullPointerException.class, () -> membership(TODAY, null).isCurrentOn(null));
    }

    private static FlatMembershipEntity membership(LocalDate start, LocalDate end) {
        var membership = new FlatMembershipEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "RESIDENT", start, Instant.parse("2026-10-20T00:00:00Z"));
        if (end != null) membership.end(end, Instant.parse("2026-10-20T00:00:00Z"));
        return membership;
    }
}
