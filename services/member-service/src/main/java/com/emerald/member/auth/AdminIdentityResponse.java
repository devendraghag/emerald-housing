package com.emerald.member.auth;

import java.util.UUID;

public record AdminIdentityResponse(UUID adminId, String username, String role) { }
