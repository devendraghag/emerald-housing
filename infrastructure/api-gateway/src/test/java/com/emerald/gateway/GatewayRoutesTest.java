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
}
