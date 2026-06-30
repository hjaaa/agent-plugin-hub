package com.agentpluginhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class DomainRepositoryTest extends AbstractIntegrationTest {

    @Autowired PluginRepository plugins;
    @Autowired PluginVersionRepository versions;
    @Autowired DistTagRepository distTags;

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
}
