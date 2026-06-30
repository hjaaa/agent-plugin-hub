package com.agentpluginhub.review;

import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.domain.PluginVersionRepository;
import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionRepository;
import com.agentpluginhub.domain.SubmissionState;
import com.agentpluginhub.publish.DuplicatePublishException;
import com.agentpluginhub.storage.ArtifactKeys;
import com.agentpluginhub.storage.ArtifactStore;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// ③审核工作流:状态机 + 审批副作用(落 PUBLISHED、移 blob、设 latest)。
// 并发审批靠 Submission 的 @Version 乐观锁(冲突抛 OptimisticLockingFailureException)。
@Service
public class ReviewService {

    private static final String PUBLISHED = "PUBLISHED";

    private final SubmissionRepository submissions;
    private final PluginRepository plugins;
    private final PluginVersionRepository versions;
    private final DistTagRepository distTags;
    private final ArtifactStore store;

    public ReviewService(SubmissionRepository submissions, PluginRepository plugins,
            PluginVersionRepository versions, DistTagRepository distTags, ArtifactStore store) {
        this.submissions = submissions;
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
        this.store = store;
    }

    public List<Submission> listSubmissions(SubmissionState state) {
        return state == null ? submissions.findAll() : submissions.findByState(state);
    }

    @Transactional
    public void startReview(Long id, String reviewer) {
        Submission s = require(id);
        if (s.getState() != SubmissionState.SUBMITTED) {
            throw new IllegalTransitionException(
                    "只能从 SUBMITTED 进入 UNDER_REVIEW,当前:" + s.getState());
        }
        s.setState(SubmissionState.UNDER_REVIEW);
        s.setReviewer(reviewer);
        s.setUpdatedAt(Instant.now());
        submissions.save(s);
    }

    @Transactional
    public void approve(Long id, String reviewer, String notes) {
        Submission s = require(id);
        assertReviewable(s);

        // 已存在则刷新显示名/描述(新版本可能改了元数据,marketplace 应反映 latest);不存在则新建
        Plugin plugin = plugins.findByPackageName(s.getPackageName())
                .map(existing -> {
                    existing.setPluginName(s.getPluginName());
                    existing.setDescription(s.getDescription());
                    return plugins.save(existing);
                })
                .orElseGet(() -> plugins.save(
                        new Plugin(s.getPackageName(), s.getPluginName(), s.getDescription(), null)));

        // 二次不可变检查(防并发双批)
        if (versions.existsByPluginIdAndVersionAndStatus(plugin.getId(), s.getVersion(), PUBLISHED)) {
            throw new DuplicatePublishException(
                    "版本已发布,不可覆盖:" + s.getPackageName() + "@" + s.getVersion());
        }

        // 内容寻址 canonical key(含 shasum):不同内容 → 不同 key,杜绝跨包/同版本不同内容相互覆盖
        String canonicalKey = ArtifactKeys.canonical(s.getPackageName(), s.getVersion(), s.getShasum());

        // claim-before-copy:先 saveAndFlush 抢占 uk_version_plugin_ver (plugin_id, version) 唯一约束。
        // 并发审批的输家在此触发 DataIntegrityViolationException(→409)而回滚,此时尚未写对象,
        // 因此不会覆盖赢家已落盘的 canonical 字节(防「赢家 DB 行指向输家 tarball」)。
        PluginVersion pv = new PluginVersion();
        pv.setPluginId(plugin.getId());
        pv.setVersion(s.getVersion());
        pv.setTarballRef(canonicalKey);
        pv.setIntegrity(s.getIntegrity());
        pv.setShasum(s.getShasum());
        pv.setSizeBytes(s.getSizeBytes());
        pv.setStatus(PUBLISHED);
        pv.setUploadedBy(s.getSubmitter());
        pv.setPublishedAt(Instant.now());
        versions.saveAndFlush(pv);

        // 抢占成功后再把 pending blob 复制到 canonical key(只有赢家执行到这里)
        byte[] bytes = store.load(s.getTarballRef());
        store.save(canonicalKey, bytes);
        // pending blob 已复制到 canonical,提交后清理 pending(本提交成功才删)
        deletePendingAfterCommit(s.getTarballRef());

        // 设/移 latest 指针(审批自动推进)+ 审计
        Instant ts = Instant.now();
        distTags.findByPluginIdAndTag(plugin.getId(), "latest")
                .ifPresentOrElse(
                        t -> { t.apply(s.getVersion(), reviewer, ts); distTags.save(t); },
                        () -> distTags.save(new DistTag(plugin.getId(), "latest", s.getVersion(), reviewer, ts)));

        // 首发规则:该 plugin 尚无 stable 时,同时把 stable 设为该版本(保证首发即可被装)
        if (distTags.findByPluginIdAndTag(plugin.getId(), "stable").isEmpty()) {
            distTags.save(new DistTag(plugin.getId(), "stable", s.getVersion(), reviewer, ts));
        }

        s.setState(SubmissionState.APPROVED);
        s.setReviewer(reviewer);
        s.setReviewNotes(notes);
        s.setUpdatedAt(Instant.now());
        submissions.save(s);   // @Version 乐观锁
    }

    @Transactional
    public void reject(Long id, String reviewer, String notes) {
        Submission s = require(id);
        assertReviewable(s);
        s.setState(SubmissionState.REJECTED);
        s.setReviewer(reviewer);
        s.setReviewNotes(notes);
        s.setUpdatedAt(Instant.now());
        submissions.save(s);
        deletePendingAfterCommit(s.getTarballRef());
    }

    private Submission require(Long id) {
        return submissions.findById(id).orElseThrow(() -> new SubmissionNotFoundException(id));
    }

    // 仅 SUBMITTED / UNDER_REVIEW 可被审批或驳回;终态拒绝(非法跃迁)
    private void assertReviewable(Submission s) {
        if (s.getState() != SubmissionState.SUBMITTED && s.getState() != SubmissionState.UNDER_REVIEW) {
            throw new IllegalTransitionException("当前状态不可审批:" + s.getState());
        }
    }

    // 事务提交后再删 pending blob:回滚则保留供重试,提交才清理 —— 避免 DB/blob 不一致与孤儿堆积
    private void deletePendingAfterCommit(String pendingKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                store.delete(pendingKey);
            }
        });
    }
}
