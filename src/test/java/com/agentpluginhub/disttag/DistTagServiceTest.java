package com.agentpluginhub.disttag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.common.VersionNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersionRepository;
import com.agentpluginhub.publish.ValidationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DistTagServiceTest {

    private final PluginRepository plugins = mock(PluginRepository.class);
    private final PluginVersionRepository versions = mock(PluginVersionRepository.class);
    private final DistTagRepository distTags = mock(DistTagRepository.class);
    private final DistTagService service = new DistTagService(plugins, versions, distTags);

    private Plugin plugin() {
        Plugin p = new Plugin("@demo/foo", "foo", "d", null);
        // id 在真实场景由 DB 生成;这里用反射不便,改用 spy 不值得 —— 直接 stub findByPackageName 返回它,
        // 并让 existsByPluginIdAndVersionAndStatus 不依赖具体 id。
        return p;
    }

    @Test
    void rejects_tag_not_in_whitelist() {
        assertThatThrownBy(() -> service.setDistTag("@demo/foo", "beta", "1.0.0", "admin"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("INVALID_DIST_TAG"));
    }

    @Test
    void rejects_blank_version() {
        assertThatThrownBy(() -> service.setDistTag("@demo/foo", "stable", "  ", "admin"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("VERSION_REQUIRED"));
    }

    @Test
    void throws_when_package_missing() {
        when(plugins.findByPackageName("@demo/foo")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setDistTag("@demo/foo", "stable", "1.0.0", "admin"))
                .isInstanceOf(PackageNotFoundException.class);
    }

    @Test
    void throws_when_version_not_published() {
        Plugin p = plugin();
        when(plugins.findByPackageName("@demo/foo")).thenReturn(Optional.of(p));
        when(versions.existsByPluginIdAndVersionAndStatus(any(), eq("9.9.9"), eq("PUBLISHED")))
                .thenReturn(false);
        assertThatThrownBy(() -> service.setDistTag("@demo/foo", "stable", "9.9.9", "admin"))
                .isInstanceOf(VersionNotFoundException.class);
    }

    @Test
    void upserts_existing_tag_and_returns_current_map() {
        Plugin p = plugin();
        DistTag stable = new DistTag(null, "stable", "1.0.0");
        when(plugins.findByPackageName("@demo/foo")).thenReturn(Optional.of(p));
        when(versions.existsByPluginIdAndVersionAndStatus(any(), eq("1.1.0"), eq("PUBLISHED")))
                .thenReturn(true);
        when(distTags.findByPluginIdAndTag(any(), eq("stable"))).thenReturn(Optional.of(stable));
        when(distTags.findByPluginId(any())).thenReturn(List.of(
                new DistTag(null, "latest", "1.1.0"), stable));

        var result = service.setDistTag("@demo/foo", "stable", "1.1.0", "admin");

        assertThat(stable.getVersion()).isEqualTo("1.1.0");          // 指针已移动
        assertThat(stable.getUpdatedBy()).isEqualTo("admin");        // 审计填充
        verify(distTags).save(stable);
        assertThat(result).containsEntry("stable", "1.1.0");
    }
}
