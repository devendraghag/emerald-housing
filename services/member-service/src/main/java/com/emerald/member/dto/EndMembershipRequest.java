package com.emerald.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

public record EndMembershipRequest(@NotNull @PastOrPresent LocalDate endDate) { }
