package com.agentpluginhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.agentpluginhub.mapper.SubmissionMapper;
import com.agentpluginhub.support.AbstractIntegrationTest;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class DomainMapperTest extends AbstractIntegrationTest {

    @Autowired PluginMapper plugins;
    @Autowired PluginVersionMapper versions;
    @Autowired DistTagMapper distTags;
    @Autowired SubmissionMapper submissions;

    @Test
    void dist_tag_persists_audit_columns() {
        Plugin p = new Plugin("@demo/audit", "audit", null, null);
        plugins.insert(p);
        Instant ts = Instant.now();
        DistTag saved = new DistTag(p.getId(), "stable", "1.0.0", "admin-sub", ts);
        distTags.insert(saved);

        DistTag reloaded = distTags.selectById(saved.getId());
        assertThat(reloaded.getVersion()).isEqualTo("1.0.0");
        assertThat(reloaded.getUpdatedBy()).isEqualTo("admin-sub");
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_persist_and_find_plugin_by_package() {
        Plugin p = new Plugin("@demo/x", "x", "desc", "team-a");
        plugins.insert(p);
        assertThat(plugins.selectOne(Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, "@demo/x"))).isNotNull();
    }

    @Test
    void should_reject_duplicate_package_name() {
        plugins.insert(new Plugin("@demo/dup", "dup", null, null));
        assertThatThrownBy(() -> plugins.insert(new Plugin("@demo/dup", "dup2", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_reject_duplicate_plugin_version() {
        Plugin p = new Plugin("@demo/v", "v", null, null);
        plugins.insert(p);
        versions.insert(newVersion(p.getId(), "1.0.0"));
        assertThatThrownBy(() -> versions.insert(newVersion(p.getId(), "1.0.0")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PluginVersion newVersion(Long pluginId, String v) {
        PluginVersion pv = new PluginVersion();
        pv.setPluginId(pluginId);
        pv.setVersion(v);
        pv.setTarballRef("demo-v-" + v + ".tgz");
        pv.setIntegrity("sha512-x");
        pv.setShasum("abc");
        pv.setSizeBytes(10L);
        pv.setStatus("PUBLISHED");
        pv.setUploadedBy("tester");
        pv.setPublishedAt(Instant.now());
        return pv;
    }

    private Submission newSubmission(String pkg, String version) {
        Submission s = new Submission();
        s.setPackageName(pkg);
        s.setVersion(version);
        s.setPluginName("p");
        s.setTarballRef("pending-x.tgz");
        s.setIntegrity("sha512-x");
        s.setShasum("abc");
        s.setSizeBytes(1L);
        s.setState(SubmissionState.SUBMITTED);
        s.setSubmitter("alice");
        Instant now = Instant.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        return s;
    }

    @Test
    void stale_submission_update_triggers_optimistic_lock() {
        Submission submission = newSubmission("@demo/lock", "1.0.0");
        submissions.insert(submission);
        Long id = submission.getId();

        Submission saved = submissions.selectById(id);
        assertThat(saved.getState()).isEqualTo(SubmissionState.SUBMITTED);

        // 两份独立加载(各自独立事务 → 均 lock_version=0,互不共享一级缓存)
        Submission stale = submissions.selectById(id);
        Submission fresh = submissions.selectById(id);

        fresh.setReviewer("admin");
        submissions.updateById(fresh);     // DB lock_version 0→1

        stale.setReviewer("other");        // stale 仍持 lock_version=0
        assertThatThrownBy(() -> {
            int updated = submissions.updateById(stale);
            if (updated == 0) {
                throw new OptimisticLockingFailureException("stale submission update:" + id);
            }
        })
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
