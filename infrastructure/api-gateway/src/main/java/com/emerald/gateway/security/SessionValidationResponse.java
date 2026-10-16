package com.emerald.gateway.security;

import java.util.UUID;

public record SessionValidationResponse(UUID adminId, String username, String role) { }
