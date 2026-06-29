package com.agentpluginhub.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.support.AbstractIntegrationTest;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.storage.type=local")
class PublishApproveEndToEndIT extends AbstractIntegrationTest {

    @LocalServerPort int port;
    @Autowired PublishingService publishing;
    @Autowired ReviewService review;

    private final HttpClient http = HttpClient.newHttpClient();

    static byte[] plugin() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Map<String, String> entries = Map.of(
                "package/package.json", "{\"name\":\"@demo/e2e\",\"version\":\"1.0.0\"}",
                "package/.claude-plugin/plugin.json",
                "{\"name\":\"e2e-plugin\",\"description\":\"d\",\"version\":\"1.0.0\"}");
        try (TarArchiveOutputStream tar =
                new TarArchiveOutputStream(new GzipCompressorOutputStream(bos))) {
            for (Map.Entry<String, String> en : entries.entrySet()) {
                byte[] c = en.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry e = new TarArchiveEntry(en.getKey());
                e.setSize(c.length);
                tar.putArchiveEntry(e);
                tar.write(c);
                tar.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    void approved_plugin_appears_in_marketplace_and_registry() throws Exception {
        Long id = publishing.publish(plugin(), "alice");
        review.approve(id, "admin", "ok");

        HttpResponse<String> mk = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/marketplace.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(mk.statusCode()).isEqualTo(200);
        assertThat(mk.body()).contains("e2e-plugin").contains("@demo/e2e");

        HttpResponse<String> pk = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/registry/@demo%2Fe2e")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(pk.statusCode()).isEqualTo(200);
        assertThat(pk.body()).contains("\"latest\":\"1.0.0\"");

        HttpResponse<byte[]> tb = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/registry/@demo/e2e/-/demo-e2e-1.0.0.tgz")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(tb.statusCode()).isEqualTo(200);
        assertThat(tb.body()).isNotEmpty();
    }
}
