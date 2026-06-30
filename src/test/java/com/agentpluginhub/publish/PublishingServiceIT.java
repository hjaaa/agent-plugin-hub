package com.agentpluginhub.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionRepository;
import com.agentpluginhub.domain.SubmissionState;
import com.agentpluginhub.registry.TarballManifestReader;
import com.agentpluginhub.support.AbstractIntegrationTest;
import com.agentpluginhub.support.TestDataSeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.storage.type=local")
class PublishingServiceIT extends AbstractIntegrationTest {

    @Autowired PublishingService publishing;
    @Autowired SubmissionRepository submissions;
    @Autowired TestDataSeeder seeder;

    private static byte[] tgz(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
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

    private static byte[] validPlugin(String pkg, String version) throws Exception {
        return tgz(Map.of(
                "package/package.json", "{\"name\":\"" + pkg + "\",\"version\":\"" + version + "\"}",
                "package/.claude-plugin/plugin.json",
                "{\"name\":\"hello-plugin\",\"description\":\"hi\",\"version\":\"" + version + "\"}"));
    }

    @Test
    void should_create_submitted_submission_for_valid_upload() throws Exception {
        Long id = publishing.publish(validPlugin("@demo/p6a", "1.0.0"), "alice");
        Submission s = submissions.findById(id).orElseThrow();
        assertThat(s.getState()).isEqualTo(SubmissionState.SUBMITTED);
        assertThat(s.getPackageName()).isEqualTo("@demo/p6a");
        assertThat(s.getVersion()).isEqualTo("1.0.0");
        assertThat(s.getPluginName()).isEqualTo("hello-plugin");
        assertThat(s.getSubmitter()).isEqualTo("alice");
        assertThat(s.getIntegrity()).startsWith("sha512-");
    }

    @Test
    void should_reject_upload_with_external_dependency() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json",
                "{\"name\":\"@demo/p6b\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"^1\"}}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"p6b\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> publishing.publish(t, "alice"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_reject_already_published_version() throws Exception {
        seeder.publish("@demo/p6c", "p6c", "1.0.0", "demo-p6c-1.0.0.tgz",
                validPlugin("@demo/p6c", "1.0.0"));
        assertThatThrownBy(() -> publishing.publish(validPlugin("@demo/p6c", "1.0.0"), "alice"))
                .isInstanceOf(DuplicatePublishException.class);
    }
}
