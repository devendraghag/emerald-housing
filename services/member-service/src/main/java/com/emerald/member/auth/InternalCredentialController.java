package com.emerald.member.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@Validated
public class InternalCredentialController {
    public static final String SERVICE_TOKEN_HEADER = "X-Gateway-Authentication-Token";

    private final AuthenticationManager authenticationManager;
    private final AdminAccountRepository accounts;
    private final byte[] expectedToken;

    public InternalCredentialController(AuthenticationManager authenticationManager,
            AdminAccountRepository accounts,
            @Value("${emerald.security.gateway-authentication-token:}") String expectedToken) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/credentials")
    public ResponseEntity<?> authenticate(@RequestHeader(name = SERVICE_TOKEN_HEADER, required = false) String token,
            @jakarta.validation.Valid @RequestBody CredentialRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_CREDENTIALS", "message", "Username or password is invalid"));
        }
        if (expectedToken.length < 32 || token == null
                || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "INTERNAL_CALL_FORBIDDEN", "message", "Internal authentication is not permitted"));
        }
        try {
            var authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            AdminAccountEntity account = accounts.findByUsername(authenticated.getName()).orElseThrow();
            return ResponseEntity.ok(new AdminIdentityResponse(account.getId(), account.getUsername(), account.getRole()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_CREDENTIALS", "message", "Username or password is invalid"));
        }
    }
}
