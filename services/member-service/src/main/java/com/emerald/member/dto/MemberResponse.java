package com.emerald.member.dto;

import com.emerald.member.service.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(UUID memberId, String fullName, String mobileNumber, String email,
                             MemberStatus status, Instant createdAt, Instant updatedAt) {
}
