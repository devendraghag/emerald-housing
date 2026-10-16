package com.emerald.gateway.security;

public record CsrfResponse(String token, String headerName, String parameterName) { }
