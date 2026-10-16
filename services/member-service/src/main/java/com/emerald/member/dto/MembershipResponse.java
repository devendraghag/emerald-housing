package com.emerald.member.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MembershipResponse(UUID membershipId, UUID memberId, UUID flatId, RelationshipType relationshipType,
                                 LocalDate startDate, LocalDate endDate) { }
