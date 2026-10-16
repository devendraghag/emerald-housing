package com.emerald.member.config;

import com.emerald.member.exception.InvalidPageRequestException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequests {
    private PageRequests() { }

    public static Pageable of(int page, int size, String sortBy, String direction, Set<String> allowedSorts) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPageRequestException("page must be non-negative and size must be between 1 and 100");
        }
        if (!allowedSorts.contains(sortBy)) {
            throw new InvalidPageRequestException("Unsupported sortBy field");
        }
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPageRequestException("direction must be asc or desc");
        }
        return PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
    }
}
