package com.emerald.member.controller;

import com.emerald.member.dto.CreateMemberRequest;
import com.emerald.member.dto.MemberResponse;
import com.emerald.member.dto.UpdateMemberRequest;
import com.emerald.member.service.MemberService;
import com.emerald.member.service.MembershipService;
import com.emerald.member.dto.CreateMembershipRequest;
import com.emerald.member.dto.MembershipResponse;
import com.emerald.member.dto.PageResponse;
import com.emerald.member.dto.EndMembershipRequest;
import com.emerald.member.dto.MemberCreationResult;
import com.emerald.member.security.SessionIdentityResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;
    private final MembershipService membershipService;

    public MemberController(MemberService memberService, MembershipService membershipService) {
        this.memberService = memberService;
        this.membershipService = membershipService;
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody CreateMemberRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal SessionIdentityResponse actor) {
        MemberCreationResult result = idempotencyKey == null
                ? new MemberCreationResult(memberService.create(request), false)
                : memberService.createIdempotent(request, idempotencyKey, actor == null ? null : actor.adminId());
        MemberResponse member = result.member();
        return ResponseEntity.created(URI.create("/api/v1/members/" + member.memberId()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(member);
    }

    @GetMapping("/{memberId}")
    public MemberResponse get(@PathVariable UUID memberId) {
        return memberService.get(memberId);
    }

    @GetMapping
    public List<MemberResponse> getAll() {
        return memberService.getAll();
    }

    @GetMapping("/page")
    public PageResponse<MemberResponse> getPage(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return memberService.getPage(page, size, sortBy, direction);
    }

    @PutMapping("/{memberId}")
    public MemberResponse update(@PathVariable UUID memberId,
                                 @Valid @RequestBody UpdateMemberRequest request) {
        return memberService.update(memberId, request);
    }

    @PatchMapping("/{memberId}/deactivate")
    public MemberResponse deactivate(@PathVariable UUID memberId) {
        return memberService.deactivate(memberId);
    }

    @PostMapping("/{memberId}/flats")
    public ResponseEntity<MembershipResponse> assignFlat(@PathVariable UUID memberId,
            @Valid @RequestBody CreateMembershipRequest request) {
        return ResponseEntity.status(201).body(membershipService.assign(memberId, request));
    }

    @GetMapping("/{memberId}/flats")
    public List<MembershipResponse> getMemberships(@PathVariable UUID memberId) {
        return membershipService.byMember(memberId);
    }

    @GetMapping("/{memberId}/flats/page")
    public PageResponse<MembershipResponse> getMembershipsPage(@PathVariable UUID memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return membershipService.pageByMember(memberId, page, size, sortBy, direction);
    }

    @PatchMapping("/{memberId}/flats/{membershipId}/end")
    public MembershipResponse endMembership(@PathVariable UUID memberId, @PathVariable UUID membershipId,
            @Valid @RequestBody EndMembershipRequest request) {
        return membershipService.end(memberId, membershipId, request);
    }
}
