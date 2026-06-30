package com.agentpluginhub.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersionRepository;
import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionRepository;
import com.agentpluginhub.domain.SubmissionState;
import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.publish.DuplicatePublishException;
import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.storage.ArtifactStore;
import com.agentpluginhub.support.AbstractIntegrationTest;
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
class ReviewServiceIT extends AbstractIntegrationTest {

    @Autowired PublishingService publishing;
    @Autowired ReviewService review;
    @Autowired SubmissionRepository submissions;
    @Autowired PluginRepository plugins;
    @Autowired PluginVersionRepository versions;
    @Autowired DistTagRepository distTags;
    @Autowired ArtifactStore store;

    static byte[] plugin(String pkg, String version) throws Exception {
        return plugin(pkg, version, "d");
    }

    // desc 用于在保持 (pkg, version) 不变的前提下制造不同字节(不同 shasum),覆盖并发/二次审批
    static byte[] plugin(String pkg, String version, String desc) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Map<String, String> entries = Map.of(
                "package/package.json", "{\"name\":\"" + pkg + "\",\"version\":\"" + version + "\"}",
                "package/.claude-plugin/plugin.json",
                "{\"name\":\"p7\",\"description\":\"" + desc + "\",\"version\":\"" + version + "\"}");
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
    void approve_should_publish_version_and_set_latest_and_store_canonical_blob() throws Exception {
        byte[] bytes = plugin("@demo/p7a", "1.0.0");
        Long id = publishing.publish(bytes, "alice");
        review.approve(id, "admin", "ok");

        Submission s = submissions.findById(id).orElseThrow();
        assertThat(s.getState()).isEqualTo(SubmissionState.APPROVED);

        Plugin p = plugins.findByPackageName("@demo/p7a").orElseThrow();
        assertThat(versions.existsByPluginIdAndVersionAndStatus(p.getId(), "1.0.0", "PUBLISHED")).isTrue();
        assertThat(distTags.findByPluginIdAndTag(p.getId(), "latest").orElseThrow().getVersion())
                .isEqualTo("1.0.0");
        // canonical key 含内容 shasum 短前缀(内容寻址)
        String canonicalKey = "demo-p7a-1.0.0-" + IntegrityUtil.hexSha1(bytes).substring(0, 12) + ".tgz";
        assertThat(store.exists(canonicalKey)).isTrue();
    }

    @Test
    void second_submission_of_same_version_cannot_overwrite_published_blob() throws Exception {
        // 同 (package, version) 的两份不同内容提交(发布期允许:尚未 PUBLISHED)
        byte[] contentA = plugin("@demo/p7d", "1.0.0", "A");
        byte[] contentB = plugin("@demo/p7d", "1.0.0", "B");
        Long idA = publishing.publish(contentA, "alice");
        Long idB = publishing.publish(contentB, "bob");

        review.approve(idA, "admin", "ok");
        String keyA = "demo-p7d-1.0.0-" + IntegrityUtil.hexSha1(contentA).substring(0, 12) + ".tgz";
        assertThat(store.load(keyA)).isEqualTo(contentA);

        // 二次审批同版本 → 409,且不得污染已发布对象(claim-before-copy:失败前不写对象)
        assertThatThrownBy(() -> review.approve(idB, "admin", "ok"))
                .isInstanceOf(DuplicatePublishException.class);
        assertThat(store.load(keyA)).isEqualTo(contentA);   // 仍是 A 的字节,未被 B 覆盖
    }

    @Test
    void reject_should_not_publish() throws Exception {
        Long id = publishing.publish(plugin("@demo/p7b", "1.0.0"), "alice");
        review.reject(id, "admin", "no");
        assertThat(submissions.findById(id).orElseThrow().getState()).isEqualTo(SubmissionState.REJECTED);
        assertThat(plugins.findByPackageName("@demo/p7b")).isEmpty();
    }

    @Test
    void approving_terminal_submission_is_illegal() throws Exception {
        Long id = publishing.publish(plugin("@demo/p7c", "1.0.0"), "alice");
        review.approve(id, "admin", "ok");
        assertThatThrownBy(() -> review.approve(id, "admin", "again"))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void missing_submission_throws() {
        assertThatThrownBy(() -> review.approve(999999L, "admin", "x"))
                .isInstanceOf(SubmissionNotFoundException.class);
    }
}
