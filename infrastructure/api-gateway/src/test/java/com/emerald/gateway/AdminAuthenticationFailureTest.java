package com.emerald.gateway;

import static org.junit.jupiter.api.Assertions.*;
import com.emerald.gateway.security.AdminAuthenticationManager;
import com.emerald.gateway.security.AuthServiceUnavailableException;
import java.net.InetSocketAddress;
import java.time.Duration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AdminAuthenticationFailureTest {
    @Test void credentialServiceOutageIsReportedAsUnavailable() throws Exception {
        assertCredentialFailure(503, AuthServiceUnavailableException.class);
    }

    @Test void rejectedCredentialsRemainBadCredentials() throws Exception {
        assertCredentialFailure(401, BadCredentialsException.class);
    }

    @Test void emptyCredentialResponseIsReportedAsUnavailable() throws Exception {
        assertCredentialFailure(200, AuthServiceUnavailableException.class);
    }

    private void assertCredentialFailure(int status, Class<? extends Throwable> failure) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/credentials", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            var manager = new AdminAuthenticationManager("http://127.0.0.1:" + server.getAddress().getPort() + "/credentials", "x".repeat(32), Duration.ofSeconds(2), Duration.ofSeconds(2));
            assertThrows(failure, () -> manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", "password")).block(Duration.ofSeconds(5)));
        } finally {
            server.stop(0);
        }
    }
}
