package com.agentpluginhub.review;

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
import com.agentpluginhub.storage.ArtifactKeys;
import com.agentpluginhub.storage.ArtifactStore;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// ③审核工作流:状态机 + 审批副作用(落 PUBLISHED、移 blob、设 latest)。
// 并发审批靠 Submission 的 @Version 乐观锁(冲突抛 OptimisticLockingFailureException)。
@Service
public class ReviewService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReviewService.class);
    private static final String PUBLISHED = "PUBLISHED";
    private static final List<SubmissionState> NONTERMINAL =
            List.of(SubmissionState.SUBMITTED, SubmissionState.UNDER_REVIEW);

    private final SubmissionMapper submissions;
    private final PluginMapper plugins;
    private final PluginVersionMapper versions;
    private final DistTagMapper distTags;
    private final ArtifactStore store;

    public ReviewService(SubmissionMapper submissions, PluginMapper plugins,
            PluginVersionMapper versions, DistTagMapper distTags, ArtifactStore store) {
        this.submissions = submissions;
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<Submission> listSubmissions(SubmissionState state) {
        if (state == null) {
            return submissions.selectList(null);
        }
        return submissions.selectList(Wrappers.<Submission>lambdaQuery()
                .eq(Submission::getState, state));
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
        updateSubmission(s);
    }

    @Transactional
    public void approve(Long id, String reviewer, String notes) {
        Submission s = require(id);
        assertReviewable(s);

        // 已存在则刷新显示名/描述(新版本可能改了元数据,marketplace 应反映 latest);不存在则新建
        Plugin plugin = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, s.getPackageName()))
                .map(existing -> {
                    existing.setPluginName(s.getPluginName());
                    existing.setDescription(s.getDescription());
                    plugins.updateById(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    Plugin created = new Plugin(s.getPackageName(), s.getPluginName(), s.getDescription(), null);
                    plugins.insert(created);
                    return created;
                });

        // 二次不可变检查(防并发双批)
        if (MapperQueries.exists(versions, Wrappers.<PluginVersion>lambdaQuery()
                .eq(PluginVersion::getPluginId, plugin.getId())
                .eq(PluginVersion::getVersion, s.getVersion())
                .eq(PluginVersion::getStatus, PUBLISHED))) {
            throw new DuplicatePublishException(
                    "版本已发布,不可覆盖:" + s.getPackageName() + "@" + s.getVersion());
        }

        // 内容寻址 canonical key(含 shasum):不同内容 → 不同 key,杜绝跨包/同版本不同内容相互覆盖
        String canonicalKey = ArtifactKeys.canonical(s.getPackageName(), s.getVersion(), s.getShasum());

        // claim-before-copy:先 insert 抢占 uk_version_plugin_ver (plugin_id, version) 唯一约束。
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
        try {
            versions.insert(pv);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicatePublishException(
                    "版本已发布,不可覆盖:" + s.getPackageName() + "@" + s.getVersion());
        }

        // 抢占成功后再把 pending blob 复制到 canonical key(只有赢家执行到这里)
        byte[] bytes = store.load(s.getTarballRef());
        store.save(canonicalKey, bytes);
        // pending blob 已复制到 canonical,提交后清理 pending(本提交成功才删)
        deletePendingAfterCommit(s.getTarballRef());

        // 设/移 latest 指针(审批自动推进)+ 审计
        Instant ts = Instant.now();
        MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, plugin.getId())
                .eq(DistTag::getTag, "latest"))
                .ifPresentOrElse(
                        t -> {
                            t.apply(s.getVersion(), reviewer, ts);
                            distTags.updateById(t);
                        },
                        () -> distTags.insert(
                                new DistTag(plugin.getId(), "latest", s.getVersion(), reviewer, ts)));

        // 首发规则:该 plugin 尚无 stable 时,同时把 stable 设为该版本(保证首发即可被装)
        if (MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, plugin.getId())
                .eq(DistTag::getTag, "stable")).isEmpty()) {
            distTags.insert(new DistTag(plugin.getId(), "stable", s.getVersion(), reviewer, ts));
        }

        s.setState(SubmissionState.APPROVED);
        s.setReviewer(reviewer);
        s.setReviewNotes(notes);
        s.setUpdatedAt(Instant.now());
        updateSubmission(s);   // @Version 乐观锁
    }

    @Transactional
    public void reject(Long id, String reviewer, String notes) {
        Submission s = require(id);
        assertReviewable(s);
        s.setState(SubmissionState.REJECTED);
        s.setReviewer(reviewer);
        s.setReviewNotes(notes);
        s.setUpdatedAt(Instant.now());
        updateSubmission(s);
        deletePendingAfterCommit(s.getTarballRef());
    }

    private Submission require(Long id) {
        Submission s = submissions.selectById(id);
        if (s == null) {
            throw new SubmissionNotFoundException(id);
        }
        return s;
    }

    private void updateSubmission(Submission s) {
        int updated = submissions.updateById(s);
        if (updated == 0) {
            throw new OptimisticLockingFailureException("stale submission update:" + s.getId());
        }
    }

    // 仅 SUBMITTED / UNDER_REVIEW 可被审批或驳回;终态拒绝(非法跃迁)
    private void assertReviewable(Submission s) {
        if (s.getState() != SubmissionState.SUBMITTED && s.getState() != SubmissionState.UNDER_REVIEW) {
            throw new IllegalTransitionException("当前状态不可审批:" + s.getState());
        }
    }

    // 事务提交后再删 pending blob:回滚则保留供重试,提交才清理 —— 避免 DB/blob 不一致与孤儿堆积。
    // 删前再确认无其它非终态 submission 仍引用同一 key:升级前遗留的内容寻址 pending key
    // (pending-<sha>.tgz)会让同字节的多条提交共享同一 blob,无条件删除会误删兄弟提交仍需的 blob。
    // 新提交已用每提交唯一的 UUID key 不再共享;此检查兜住遗留共享场景,宁可留孤儿也不误删。
    private void deletePendingAfterCommit(String pendingKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // 当前 submission 已落终态(APPROVED/REJECTED),不计入 NONTERMINAL;仍有非终态引用则保留
                    long refs = MapperQueries.count(submissions, Wrappers.<Submission>lambdaQuery()
                            .eq(Submission::getTarballRef, pendingKey)
                            .in(Submission::getState, NONTERMINAL));
                    if (refs == 0) {
                        store.delete(pendingKey);
                    }
                } catch (RuntimeException e) {
                    // pending 清理是 best-effort:事务已提交,清理失败不得影响审批结果,仅告警
                    log.warn("failed to delete pending blob after commit: {}", pendingKey, e);
                }
            }
        });
    }
}
