package com.emerald.member.security;

import java.util.UUID;

public record SessionIdentityResponse(UUID adminId, String username, String role) { }
