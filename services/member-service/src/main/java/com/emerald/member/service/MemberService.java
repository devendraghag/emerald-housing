package com.emerald.member.service;

import com.emerald.member.dto.CreateMemberRequest;
import com.emerald.member.dto.MemberResponse;
import com.emerald.member.dto.UpdateMemberRequest;
import com.emerald.member.dto.PageResponse;
import com.emerald.member.config.PageRequests;
import com.emerald.member.entity.MemberEntity;
import com.emerald.member.exception.DuplicateMobileNumberException;
import com.emerald.member.exception.MemberNotFoundException;
import com.emerald.member.exception.InactiveMemberException;
import com.emerald.member.repository.MemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class MemberService {
    private final MemberRepository members;
    public MemberService(MemberRepository members) { this.members = members; }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse create(CreateMemberRequest request) {
        String mobile = normalizeMobile(request.mobileNumber());
        if (members.existsByMobileNumberAndStatus(mobile, MemberStatus.ACTIVE)) throw new DuplicateMobileNumberException();
        Instant now = Instant.now();
        try {
            return toResponse(members.saveAndFlush(new MemberEntity(UUID.randomUUID(), normalizeName(request.fullName()), mobile,
                    normalizeEmail(request.email()), MemberStatus.ACTIVE, now, now)));
        } catch (DataIntegrityViolationException ex) { throw new DuplicateMobileNumberException(); }
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse get(UUID id) { return toResponse(find(id)); }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<MemberResponse> getAll() { return members.findAllByOrderByCreatedAtDesc().stream().map(MemberService::toResponse).toList(); }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<MemberResponse> getPage(int page, int size, String sortBy, String direction) {
        Pageable pageable = PageRequests.of(page, size, sortBy, direction,
                Set.of("createdAt", "updatedAt", "fullName", "mobileNumber", "email", "status"));
        Page<MemberEntity> result = members.findAll(pageable);
        return PageResponse.from(result, MemberService::toResponse, sortBy, direction);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse update(UUID id, UpdateMemberRequest request) {
        MemberEntity member = find(id);
        String mobile = normalizeMobile(request.mobileNumber());
        if (member.getStatus() == MemberStatus.ACTIVE && !member.getMobileNumber().equals(mobile)
                && members.existsByMobileNumberAndStatus(mobile, MemberStatus.ACTIVE)) throw new DuplicateMobileNumberException();
        member.update(normalizeName(request.fullName()), mobile, normalizeEmail(request.email()), Instant.now());
        try { return toResponse(members.saveAndFlush(member)); }
        catch (DataIntegrityViolationException ex) { throw new DuplicateMobileNumberException(); }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse deactivate(UUID id) {
        MemberEntity member = find(id);
        member.deactivate(Instant.now());
        return toResponse(members.save(member));
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public void requireActive(UUID id) {
        MemberEntity member = find(id);
        if (member.getStatus() != MemberStatus.ACTIVE) throw new InactiveMemberException();
    }
    private MemberEntity find(UUID id) { return members.findById(id).orElseThrow(() -> new MemberNotFoundException(id)); }
    private static String normalizeName(String s) { return s.trim().replaceAll("\\s+", " "); }
    private static String normalizeMobile(String s) { return s.trim(); }
    private static String normalizeEmail(String s) { return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT); }
    private static MemberResponse toResponse(MemberEntity m) { return new MemberResponse(m.getId(), m.getFullName(), m.getMobileNumber(), m.getEmail(), m.getStatus(), m.getCreatedAt(), m.getUpdatedAt()); }
}
