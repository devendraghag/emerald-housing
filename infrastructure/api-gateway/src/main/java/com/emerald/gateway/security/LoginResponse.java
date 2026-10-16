package com.emerald.gateway.security;

import java.util.UUID;

public record LoginResponse(UUID adminId, String username, String role, boolean authenticated) { }
