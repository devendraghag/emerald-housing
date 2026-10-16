package com.emerald.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMembershipRequest(@NotNull UUID flatId, @NotNull RelationshipType relationshipType,
                                     @NotNull @PastOrPresent LocalDate startDate) { }
