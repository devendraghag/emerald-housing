package com.emerald.member.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.emerald.member.dto.PageResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseCollectionsTest {
    @Test
    void responseRecordsSnapshotListsInsteadOfExposingTheirMutableSource() {
        var source = new ArrayList<>(List.of("first"));
        var page = new PageResponse<>(source, 0, 20, 1, 1, "createdAt", "asc");
        var cursor = new CursorPage<>(source, null, false);
        source.add("later");

        assertEquals(List.of("first"), page.content());
        assertEquals(List.of("first"), cursor.items());
        assertThrows(UnsupportedOperationException.class, () -> page.content().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> cursor.items().add("other"));
    }
}
