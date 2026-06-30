package com.agentpluginhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionRepository;
import com.agentpluginhub.domain.SubmissionState;
import com.agentpluginhub.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class DomainRepositoryTest extends AbstractIntegrationTest {

    @Autowired PluginRepository plugins;
    @Autowired PluginVersionRepository versions;
    @Autowired DistTagRepository distTags;
    @Autowired SubmissionRepository submissions;

    @Test
    void dist_tag_persists_audit_columns() {
        Plugin p = plugins.save(new Plugin("@demo/audit", "audit", null, null));
        Instant ts = Instant.now();
        DistTag saved = distTags.save(new DistTag(p.getId(), "stable", "1.0.0", "admin-sub", ts));

        DistTag reloaded = distTags.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo("1.0.0");
        assertThat(reloaded.getUpdatedBy()).isEqualTo("admin-sub");
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_persist_and_find_plugin_by_package() {
        Plugin p = new Plugin("@demo/x", "x", "desc", "team-a");
        plugins.save(p);
        assertThat(plugins.findByPackageName("@demo/x")).isPresent();
    }

    @Test
    void should_reject_duplicate_package_name() {
        plugins.save(new Plugin("@demo/dup", "dup", null, null));
        assertThatThrownBy(() -> plugins.saveAndFlush(new Plugin("@demo/dup", "dup2", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_reject_duplicate_plugin_version() {
        Plugin p = plugins.save(new Plugin("@demo/v", "v", null, null));
        versions.save(newVersion(p.getId(), "1.0.0"));
        assertThatThrownBy(() -> versions.saveAndFlush(newVersion(p.getId(), "1.0.0")))
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
        Long id = submissions.save(newSubmission("@demo/lock", "1.0.0")).getId();

        // 两份独立加载(各自独立事务 → 均 lock_version=0,互不共享一级缓存)
        Submission stale = submissions.findById(id).orElseThrow();
        Submission fresh = submissions.findById(id).orElseThrow();

        fresh.setReviewer("admin");
        submissions.saveAndFlush(fresh);   // DB lock_version 0→1

        stale.setReviewer("other");        // stale 仍持 lock_version=0
        assertThatThrownBy(() -> submissions.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
