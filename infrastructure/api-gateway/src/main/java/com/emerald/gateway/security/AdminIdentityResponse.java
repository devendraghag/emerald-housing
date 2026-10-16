package com.emerald.gateway.security;

import java.util.UUID;

public record AdminIdentityResponse(UUID adminId, String username, String role) { }
