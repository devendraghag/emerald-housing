package com.emerald.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest(properties = {
        "MEMBER_SERVICE_URL=http://member-service:8080",
        "SOCIETY_SERVICE_URL=http://society-service:8080"
})
class GatewayRoutesTest {
    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesUseStableKubernetesServiceAddresses() {
        var routes = routeLocator.getRoutes().collectList().block(Duration.ofSeconds(5));

        assertThat(routes).isNotNull();
        assertThat(routes).extracting("id")
                .contains("member-api", "society-api");
        assertThat(routes).filteredOn(route -> route.getId().equals("member-api"))
                .extracting(route -> route.getUri().toString())
                .containsExactly("http://member-service:8080");
        assertThat(routes).filteredOn(route -> route.getId().equals("society-api"))
                .extracting(route -> route.getUri().toString())
                .containsExactly("http://society-service:8080");
    }

    @Test
    void accessFamilyAndOwnerRequestsRouteToMemberService() {
        var routes=routeLocator.getRoutes().collectList().block(Duration.ofSeconds(5));
        var member=routes.stream().filter(route->route.getId().equals("member-api")).findFirst().orElseThrow();
        for(String path:java.util.List.of("/api/v1/access/roles","/api/v1/families","/api/v1/owners","/api/v1/accounts/me")) {
            var exchange=org.springframework.mock.web.server.MockServerWebExchange.from(org.springframework.mock.http.server.reactive.MockServerHttpRequest.get(path));
            assertThat(reactor.core.publisher.Mono.from(member.getPredicate().apply(exchange)).block()).as(path).isTrue();
        }
    }
    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @Test
    void portalAssetsAreAvailableWithoutSigningIn() {
        var client=org.springframework.test.web.reactive.server.WebTestClient.bindToApplicationContext(applicationContext).build();
        client.get().uri("/portal.html").exchange().expectStatus().isOk().expectBody(String.class).value(body->assertThat(body).contains("Society portal","portal.js"));
        client.get().uri("/portal.js").exchange().expectStatus().isOk();
        client.get().uri("/portal.css").exchange().expectStatus().isOk();
    }
}
