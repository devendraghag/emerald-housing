package com.emerald.member.controller;

import com.emerald.member.dto.MembershipResponse;
import com.emerald.member.dto.PageResponse;
import com.emerald.member.service.MembershipService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/flats")
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service=service; }
    @GetMapping("/{flatId}/memberships")
    public List<MembershipResponse> getMemberships(@PathVariable UUID flatId) { return service.byFlat(flatId); }
    @GetMapping("/{flatId}/memberships/page")
    public PageResponse<MembershipResponse> getMembershipsPage(@PathVariable UUID flatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.pageByFlat(flatId, page, size, sortBy, direction);
    }
}
