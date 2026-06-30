package com.agentpluginhub.publish;

import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersionRepository;
import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionRepository;
import com.agentpluginhub.domain.SubmissionState;
import com.agentpluginhub.storage.ArtifactStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// ②发布服务:校验 → 不可变检查 → 存 pending blob → 建 SUBMITTED submission。
@Service
public class PublishingService {

    private static final String PUBLISHED = "PUBLISHED";

    private final Validator validator;
    private final ArtifactStore store;
    private final SubmissionRepository submissions;
    private final PluginRepository plugins;
    private final PluginVersionRepository versions;

    public PublishingService(Validator validator, ArtifactStore store,
            SubmissionRepository submissions, PluginRepository plugins,
            PluginVersionRepository versions) {
        this.validator = validator;
        this.store = store;
        this.submissions = submissions;
        this.plugins = plugins;
        this.versions = versions;
    }

    @Transactional
    public Long publish(byte[] tarball, String submitter) {
        ValidationResult vr = validator.validate(tarball);          // 失败 → ValidationException(422)

        // 不可变发布:若该版本已 PUBLISHED,拒绝
        Plugin existing = plugins.findByPackageName(vr.packageName()).orElse(null);
        if (existing != null && versions.existsByPluginIdAndVersionAndStatus(
                existing.getId(), vr.version(), PUBLISHED)) {
            throw new DuplicatePublishException(
                    "版本已发布,不可覆盖:" + vr.packageName() + "@" + vr.version());
        }

        // pending blob 用每提交唯一的 key(非内容寻址),扁平无斜杠,本地/对象存储均可。
        // 内容寻址会让同字节的两次提交共享一个 pending blob;一旦其一被驳回,afterCommit 删除会
        // 连带删掉另一条仍可审批的 submission 引用的 blob,导致后续 approve 的 store.load 失败。
        // 每提交独立 key 杜绝该别名,删自身 pending 永远安全。shasum 仍单独留存用于 integrity/canonical key。
        String shasum = IntegrityUtil.hexSha1(tarball);
        String pendingKey = "pending-" + UUID.randomUUID() + ".tgz";
        store.save(pendingKey, tarball);

        Instant now = Instant.now();
        Submission s = new Submission();
        s.setPackageName(vr.packageName());
        s.setVersion(vr.version());
        s.setPluginName(vr.pluginName());
        s.setDescription(vr.description());
        s.setTarballRef(pendingKey);
        s.setIntegrity(IntegrityUtil.sriSha512(tarball));
        s.setShasum(shasum);
        s.setSizeBytes(tarball.length);
        s.setState(SubmissionState.SUBMITTED);
        s.setSubmitter(submitter);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        return submissions.save(s).getId();
    }
}
