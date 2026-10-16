package com.emerald.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @MobileNumber String mobileNumber,
        @Email @Size(max = 254) String email) {

    public UpdateMemberRequest {
        email = email == null ? null : email.trim();
    }
}
