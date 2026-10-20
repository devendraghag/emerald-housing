package com.emerald.member.dto;
import com.emerald.member.service.MemberStatus;import java.time.Instant;import java.util.UUID;
public record MemberResponse(UUID memberId,String fullName,String mobileNumber,String email,MemberStatus status,Instant createdAt,Instant updatedAt,Long version) {
 public MemberResponse { version=version==null?0L:version; }
 public MemberResponse(UUID id,String name,String mobile,String email,MemberStatus status,Instant created,Instant updated) { this(id,name,mobile,email,status,created,updated,0L); }
}
