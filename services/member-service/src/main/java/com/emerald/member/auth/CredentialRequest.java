package com.emerald.member.auth;

import jakarta.validation.constraints.NotBlank;

public record CredentialRequest(@NotBlank String username, @NotBlank String password) { }
