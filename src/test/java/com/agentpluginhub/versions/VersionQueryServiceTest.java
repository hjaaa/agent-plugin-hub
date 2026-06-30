package com.agentpluginhub.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.domain.PluginVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VersionQueryServiceTest {

    private final PluginRepository plugins = mock(PluginRepository.class);
    private final PluginVersionRepository versions = mock(PluginVersionRepository.class);
    private final DistTagRepository distTags = mock(DistTagRepository.class);
    private final VersionQueryService service = new VersionQueryService(plugins, versions, distTags);

    private static PluginVersion pv(String version, Instant publishedAt) {
        PluginVersion p = new PluginVersion();
        p.setVersion(version);
        p.setStatus("PUBLISHED");
        p.setPublishedAt(publishedAt);
        p.setUploadedBy("alice");
        p.setSizeBytes(123L);
        return p;
    }

    @Test
    void lists_versions_newest_first_with_dist_tags() {
        Plugin plugin = new Plugin("@demo/foo", "foo", "d", null);
        when(plugins.findByPackageName("@demo/foo")).thenReturn(Optional.of(plugin));
        when(versions.findByPluginIdAndStatus(any(), eq("PUBLISHED"))).thenReturn(List.of(
                pv("1.0.0", Instant.parse("2026-01-01T00:00:00Z")),
                pv("1.1.0", Instant.parse("2026-02-01T00:00:00Z"))));
        when(distTags.findByPluginId(any())).thenReturn(List.of(
                new DistTag(null, "latest", "1.1.0"), new DistTag(null, "stable", "1.0.0")));

        PluginVersionsView view = service.list("@demo/foo");

        assertThat(view.packageName()).isEqualTo("@demo/foo");
        assertThat(view.versions()).extracting(VersionDetail::version)
                .containsExactly("1.1.0", "1.0.0");   // publishedAt 降序
        assertThat(view.distTags()).containsEntry("latest", "1.1.0").containsEntry("stable", "1.0.0");
    }

    @Test
    void throws_404_when_package_missing() {
        when(plugins.findByPackageName("@x/none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list("@x/none")).isInstanceOf(PackageNotFoundException.class);
    }

    @Test
    void throws_404_when_no_published_versions() {
        Plugin plugin = new Plugin("@demo/foo", "foo", "d", null);
        when(plugins.findByPackageName("@demo/foo")).thenReturn(Optional.of(plugin));
        when(versions.findByPluginIdAndStatus(any(), eq("PUBLISHED"))).thenReturn(List.of());
        assertThatThrownBy(() -> service.list("@demo/foo")).isInstanceOf(PackageNotFoundException.class);
    }
}
