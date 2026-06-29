package com.agentpluginhub.registry;

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
class RegistryControllerTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> getString(String rawPath) throws Exception {
        URI uri = URI.create("http://localhost:" + port + rawPath);
        return http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void should_return_packument_when_literal_scoped_path() throws Exception {
        HttpResponse<String> res = getString("/registry/@demo/hello-plugin");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"dist-tags\"").contains("\"latest\":\"1.0.0\"");
    }

    // 关键:npm 拉 packument 时把 scope 斜杠编码成 %2F,服务端必须放行
    @Test
    void should_return_packument_when_percent_encoded_scope() throws Exception {
        HttpResponse<String> res = getString("/registry/@demo%2Fhello-plugin");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"latest\":\"1.0.0\"");
    }

    @Test
    void should_return_tarball_bytes_when_tarball_path() throws Exception {
        URI uri = URI.create("http://localhost:" + port
                + "/registry/@demo/hello-plugin/-/demo-hello-plugin-1.0.0.tgz");
        HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isNotEmpty();
    }

    @Test
    void should_return_404_when_package_unknown() throws Exception {
        HttpResponse<String> res = getString("/registry/@demo/missing");
        assertThat(res.statusCode()).isEqualTo(404);
    }
}
