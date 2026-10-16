package com.emerald.gateway.security;

import java.io.Serializable;
import java.util.UUID;

public record AdminIdentity(UUID adminId, String username) implements Serializable { }
