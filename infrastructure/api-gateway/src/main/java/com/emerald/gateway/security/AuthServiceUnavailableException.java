package com.emerald.gateway.security;

import org.springframework.security.authentication.AuthenticationServiceException;

public class AuthServiceUnavailableException extends AuthenticationServiceException {
    public AuthServiceUnavailableException() { super("Credential validation is temporarily unavailable"); }
}
