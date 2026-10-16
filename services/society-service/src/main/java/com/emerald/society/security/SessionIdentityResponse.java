package com.emerald.society.security;

import java.util.UUID;

public record SessionIdentityResponse(UUID adminId, String username, String role) { }
