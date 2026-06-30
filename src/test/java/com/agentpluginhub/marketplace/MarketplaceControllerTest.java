package com.agentpluginhub.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import com.agentpluginhub.support.AbstractIntegrationTest;
import com.agentpluginhub.support.TestDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.artifacts-dir=src/test/resources/fixtures/artifacts")
class MarketplaceControllerTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    // 自给种子:注入 seeder,避免依赖其他测试类写入共享 DB
    @Autowired
    TestDataSeeder seeder;

    @BeforeEach
    void seedPlugin() throws Exception {
        // 读取 fixture 字节,幂等地种入 @demo/hello-plugin@1.0.0(含 stable 渠道)
        byte[] bytes = Files.readAllBytes(
                Path.of("src/test/resources/fixtures/artifacts/demo-hello-plugin-1.0.0.tgz"));
        seeder.publish("@demo/hello-plugin", "hello-plugin", "1.0.0",
                "demo-hello-plugin-1.0.0.tgz", bytes);
    }

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
