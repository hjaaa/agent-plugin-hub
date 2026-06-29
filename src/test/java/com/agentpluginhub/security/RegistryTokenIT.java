package com.agentpluginhub.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.support.AbstractIntegrationTest;
import com.agentpluginhub.support.TestDataSeeder;
import com.agentpluginhub.registry.TarballTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"app.storage.type=local", "app.registry.auth.enabled=true"})
class RegistryTokenIT extends AbstractIntegrationTest {

    @LocalServerPort int port;
    @Autowired RegistryTokenService tokens;
    @Autowired TestDataSeeder seeder;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seed() throws Exception {
        seeder.publish("@demo/tok", "tok", "1.0.0", "demo-tok-1.0.0.tgz",
                TarballTestSupport.tgzWithPackageJson("{\"name\":\"@demo/tok\",\"version\":\"1.0.0\"}"));
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void valid_token_allows_packument() throws Exception {
        String token = tokens.issue("ci", "admin");
        assertThat(get("/registry/@demo%2Ftok", token).statusCode()).isEqualTo(200);
    }

    @Test
    void missing_or_invalid_token_is_401_when_enabled() throws Exception {
        assertThat(get("/registry/@demo%2Ftok", null).statusCode()).isEqualTo(401);
        assertThat(get("/registry/@demo%2Ftok", "garbage").statusCode()).isEqualTo(401);
    }

    @Test
    void revoked_token_is_401() throws Exception {
        String token = tokens.issue("temp", "admin");
        // 先确认可用,再吊销
        assertThat(get("/registry/@demo%2Ftok", token).statusCode()).isEqualTo(200);
        Long id = tokens.findIdByToken(token);
        tokens.revoke(id);
        assertThat(get("/registry/@demo%2Ftok", token).statusCode()).isEqualTo(401);
    }
}
