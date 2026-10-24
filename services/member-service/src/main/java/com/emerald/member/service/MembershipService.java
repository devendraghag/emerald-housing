package com.emerald.member.service;

import com.emerald.member.client.SocietyClient;
import com.emerald.member.lock.RedisMembershipLock;
import com.emerald.member.exception.FlatNotEligibleException;
import com.emerald.member.dto.CreateMembershipRequest;
import com.emerald.member.dto.MembershipResponse;
import com.emerald.member.dto.PageResponse;
import com.emerald.member.dto.EndMembershipRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class MembershipService {
    @org.springframework.beans.factory.annotation.Autowired private com.emerald.member.auth.AccessRegistry registry;
    private final MemberService members;
    private final SocietyClient societyClient;
    private final MembershipWriter writer;
    private final RedisMembershipLock membershipLock;
    public MembershipService(MemberService members, SocietyClient societyClient, MembershipWriter writer,
            RedisMembershipLock membershipLock) {
        this.members=members; this.societyClient=societyClient; this.writer=writer;
        this.membershipLock=membershipLock;
    }
    @PreAuthorize("@memberAccess.has('OWNER_REGISTER','SOCIETY')")
    @org.springframework.transaction.annotation.Transactional
    public MembershipResponse assign(UUID memberId, CreateMembershipRequest request) {
        if(registry!=null)registry.lock();
        members.requireActive(memberId);
        var flat = societyClient.getFlat(request.flatId());
        if (!"ACTIVE".equals(flat.status())) throw new FlatNotEligibleException();
        return membershipLock.withMemberLock(memberId, () -> writer.persist(memberId, request));
    }
    @PreAuthorize("@memberAccess.member(#p0)")
    public List<MembershipResponse> byMember(UUID id) { return writer.byMember(id); }
    @PreAuthorize("@memberAccess.flat(#p0)")
    public List<MembershipResponse> byFlat(UUID id) { return writer.byFlat(id); }
    @PreAuthorize("@memberAccess.member(#p0)")
    public PageResponse<MembershipResponse> pageByMember(UUID id, int page, int size, String sortBy, String direction) {
        return writer.pageByMember(id, page, size, sortBy, direction);
    }
    @PreAuthorize("@memberAccess.flat(#p0)")
    public PageResponse<MembershipResponse> pageByFlat(UUID id, int page, int size, String sortBy, String direction) {
        return writer.pageByFlat(id, page, size, sortBy, direction);
    }
    @PreAuthorize("@memberAccess.has('OWNER_END_OWNERSHIP','SOCIETY')")
    @org.springframework.transaction.annotation.Transactional
    public MembershipResponse end(UUID memberId, UUID membershipId, EndMembershipRequest request) {
        if(registry!=null)registry.lock();
        return membershipLock.withMemberLock(memberId, () -> writer.end(memberId, membershipId, request));
    }
}
