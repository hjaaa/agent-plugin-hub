package com.agentpluginhub.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionState;
import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.agentpluginhub.mapper.SubmissionMapper;
import com.agentpluginhub.publish.DuplicatePublishException;
import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.storage.ArtifactStore;
import com.agentpluginhub.support.AbstractIntegrationTest;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    @Autowired SubmissionMapper submissions;
    @Autowired PluginMapper plugins;
    @Autowired PluginVersionMapper versions;
    @Autowired DistTagMapper distTags;
    @Autowired ArtifactStore store;

    static byte[] plugin(String pkg, String version) throws Exception {
        return plugin(pkg, version, "p7", "d");
    }

    // desc 用于在保持 (pkg, version) 不变的前提下制造不同字节(不同 shasum),覆盖并发/二次审批
    static byte[] plugin(String pkg, String version, String desc) throws Exception {
        return plugin(pkg, version, "p7", desc);
    }

    // pluginName/desc 可定制,覆盖审批新版本刷新 plugin 元数据
    static byte[] plugin(String pkg, String version, String pluginName, String desc) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Map<String, String> entries = Map.of(
                "package/package.json", "{\"name\":\"" + pkg + "\",\"version\":\"" + version + "\"}",
                "package/.claude-plugin/plugin.json",
                "{\"name\":\"" + pluginName + "\",\"description\":\"" + desc + "\",\"version\":\"" + version + "\"}");
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

        Submission s = submissions.selectById(id);
        assertThat(s).isNotNull();
        assertThat(s.getState()).isEqualTo(SubmissionState.APPROVED);

        Plugin p = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/p7a")).orElseThrow();
        assertThat(MapperQueries.exists(versions, Wrappers.<PluginVersion>lambdaQuery()
                .eq(PluginVersion::getPluginId, p.getId())
                .eq(PluginVersion::getVersion, "1.0.0")
                .eq(PluginVersion::getStatus, "PUBLISHED"))).isTrue();
        assertThat(MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, p.getId())
                .eq(DistTag::getTag, "latest")).orElseThrow().getVersion())
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
    void approving_new_version_refreshes_plugin_metadata() throws Exception {
        Long id1 = publishing.publish(plugin("@demo/p7f", "1.0.0", "old-name", "old desc"), "alice");
        review.approve(id1, "admin", "ok");
        Long id2 = publishing.publish(plugin("@demo/p7f", "2.0.0", "new-name", "new desc"), "alice");
        review.approve(id2, "admin", "ok");

        Plugin p = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/p7f")).orElseThrow();
        assertThat(p.getPluginName()).isEqualTo("new-name");
        assertThat(p.getDescription()).isEqualTo("new desc");
        assertThat(MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, p.getId())
                .eq(DistTag::getTag, "latest")).orElseThrow().getVersion())
                .isEqualTo("2.0.0");
    }

    @Test
    void reject_should_not_publish() throws Exception {
        Long id = publishing.publish(plugin("@demo/p7b", "1.0.0"), "alice");
        review.reject(id, "admin", "no");
        Submission s = submissions.selectById(id);
        assertThat(s).isNotNull();
        assertThat(s.getState()).isEqualTo(SubmissionState.REJECTED);
        assertThat(MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/p7b"))).isEmpty();
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

    @Test
    void first_approve_sets_both_latest_and_stable() throws Exception {
        Long id = publishing.publish(plugin("@demo/p7i", "1.0.0"), "alice");
        review.approve(id, "admin-sub", "ok");

        Plugin p = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/p7i")).orElseThrow();
        assertThat(MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, p.getId())
                .eq(DistTag::getTag, "latest")).orElseThrow().getVersion())
                .isEqualTo("1.0.0");
        var stable = MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, p.getId())
                .eq(DistTag::getTag, "stable")).orElseThrow();
        assertThat(stable.getVersion()).isEqualTo("1.0.0");
        assertThat(stable.getUpdatedBy()).isEqualTo("admin-sub");   // 审计填充
    }

    @Test
    void second_approve_advances_latest_but_keeps_stable() throws Exception {
        Long id1 = publishing.publish(plugin("@demo/p7j", "1.0.0"), "alice");
        review.approve(id1, "admin", "ok");
        Long id2 = publishing.publish(plugin("@demo/p7j", "1.1.0"), "alice");
        review.approve(id2, "admin", "ok");

        Plugin p = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/p7j")).orElseThrow();
        assertThat(MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, p.getId())
                .eq(DistTag::getTag, "latest")).orElseThrow().getVersion())
                .isEqualTo("1.1.0");
        assertThat(MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, p.getId())
                .eq(DistTag::getTag, "stable")).orElseThrow().getVersion())
                .isEqualTo("1.0.0");   // stable 不随审批推进
    }

    @Test
    void approve_deletes_pending_blob_and_keeps_canonical() throws Exception {
        byte[] bytes = plugin("@demo/p7g", "1.0.0");
        Long id = publishing.publish(bytes, "alice");
        Submission s = submissions.selectById(id);
        assertThat(s).isNotNull();
        String pendingKey = s.getTarballRef();
        assertThat(store.exists(pendingKey)).isTrue();

        review.approve(id, "admin", "ok");

        String canonicalKey = "demo-p7g-1.0.0-" + IntegrityUtil.hexSha1(bytes).substring(0, 12) + ".tgz";
        assertThat(store.exists(canonicalKey)).isTrue();    // canonical 仍在
        assertThat(store.exists(pendingKey)).isFalse();     // 提交后 pending 已清理
    }

    @Test
    void reject_deletes_pending_blob() throws Exception {
        byte[] bytes = plugin("@demo/p7h", "1.0.0");
        Long id = publishing.publish(bytes, "alice");
        Submission s = submissions.selectById(id);
        assertThat(s).isNotNull();
        String pendingKey = s.getTarballRef();
        assertThat(store.exists(pendingKey)).isTrue();

        review.reject(id, "admin", "no");

        assertThat(store.exists(pendingKey)).isFalse();
    }

    // 回归(Codex P2):同字节的两次提交各持有独立 pending blob,驳回其一不得删掉另一条仍可审批的 blob。
    // 旧的内容寻址 pending key 下两条 submission 共享同一 blob,reject(a) 会让 approve(b) 的 store.load 失败。
    @Test
    void reject_one_of_two_identical_pending_submissions_keeps_other_approvable() throws Exception {
        byte[] bytes = plugin("@demo/p7dup", "1.0.0");
        Long a = publishing.publish(bytes, "alice");
        Long b = publishing.publish(bytes, "bob");   // 同字节的第二次提交(a、b 均 SUBMITTED)

        Submission submissionA = submissions.selectById(a);
        assertThat(submissionA).isNotNull();
        Submission submissionB = submissions.selectById(b);
        assertThat(submissionB).isNotNull();
        String keyA = submissionA.getTarballRef();
        String keyB = submissionB.getTarballRef();
        assertThat(keyA).isNotEqualTo(keyB);         // 每提交唯一,不再共享 blob

        review.reject(a, "admin", "dup");            // 驳回 a:afterCommit 仅删 a 自己的 pending blob
        assertThat(store.exists(keyB)).isTrue();     // b 的 blob 不受影响

        review.approve(b, "admin", "ok");            // b 仍可正常审批上架
        Plugin p = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/p7dup")).orElseThrow();
        assertThat(MapperQueries.exists(versions, Wrappers.<PluginVersion>lambdaQuery()
                .eq(PluginVersion::getPluginId, p.getId())
                .eq(PluginVersion::getVersion, "1.0.0")
                .eq(PluginVersion::getStatus, "PUBLISHED"))).isTrue();
    }

    // 回归(Codex 二次 P2):升级前遗留的内容寻址 pending key 会让同字节多条提交共享同一 blob
    // (UUID 改动只覆盖新提交)。afterCommit 删 blob 前须确认无其它非终态提交仍引用,
    // 否则驳回其一会删掉兄弟提交仍需的 blob,导致其后续 approve 在 store.load 处失败。
    @Test
    void reject_legacy_shared_pending_key_keeps_sibling_approvable() throws Exception {
        byte[] bytes = plugin("@demo/p7legacy", "1.0.0");
        String legacyKey = "pending-" + IntegrityUtil.hexSha1(bytes) + ".tgz";   // 模拟旧的内容寻址 key
        store.save(legacyKey, bytes);
        Long a = legacySubmission("@demo/p7legacy", "1.0.0", legacyKey, bytes);
        Long b = legacySubmission("@demo/p7legacy", "1.0.0", legacyKey, bytes);  // 共享同一 legacyKey

        review.reject(a, "admin", "dup");            // 驳回 a:b 仍以非终态引用 legacyKey,不得删
        assertThat(store.exists(legacyKey)).isTrue();

        review.approve(b, "admin", "ok");            // b 仍可正常审批上架
        Plugin p = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/p7legacy")).orElseThrow();
        assertThat(MapperQueries.exists(versions, Wrappers.<PluginVersion>lambdaQuery()
                .eq(PluginVersion::getPluginId, p.getId())
                .eq(PluginVersion::getVersion, "1.0.0")
                .eq(PluginVersion::getStatus, "PUBLISHED"))).isTrue();
    }

    // 直接构造一条引用指定 pending key 的 SUBMITTED 提交,模拟升级前遗留的共享 blob 场景
    private Long legacySubmission(String pkg, String version, String key, byte[] bytes) {
        Submission s = new Submission();
        s.setPackageName(pkg);
        s.setVersion(version);
        s.setPluginName("p7");
        s.setDescription("d");
        s.setTarballRef(key);
        s.setIntegrity(IntegrityUtil.sriSha512(bytes));
        s.setShasum(IntegrityUtil.hexSha1(bytes));
        s.setSizeBytes(bytes.length);
        s.setState(SubmissionState.SUBMITTED);
        s.setSubmitter("alice");
        Instant now = Instant.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        submissions.insert(s);
        return s.getId();
    }
}
