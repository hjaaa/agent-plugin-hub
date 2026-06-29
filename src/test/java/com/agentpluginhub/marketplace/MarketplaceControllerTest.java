package com.agentpluginhub.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.artifacts-dir=src/test/resources/fixtures/artifacts")
class MarketplaceControllerTest {

    @LocalServerPort
    int port;

    @Test
    void should_serve_marketplace_json() throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/marketplace.json");
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body())
                .contains("\"name\":\"agent-plugin-hub\"")
                .contains("\"source\":\"npm\"")
                .contains("\"package\":\"@demo/hello-plugin\"")
                .contains("\"version\":\"1.0.0\"")
                .contains("/registry");
    }
}
