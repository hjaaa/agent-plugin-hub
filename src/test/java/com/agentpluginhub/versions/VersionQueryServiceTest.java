package com.agentpluginhub.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VersionQueryServiceTest {

    private final PluginMapper plugins = mock(PluginMapper.class);
    private final PluginVersionMapper versions = mock(PluginVersionMapper.class);
    private final DistTagMapper distTags = mock(DistTagMapper.class);
    private final VersionQueryService service = new VersionQueryService(plugins, versions, distTags);

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Plugin.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PluginVersion.class);
    }

    private static PluginVersion pv(String version, Instant publishedAt) {
        PluginVersion p = new PluginVersion();
        p.setVersion(version);
        p.setStatus("PUBLISHED");
        p.setPublishedAt(publishedAt);
        p.setUploadedBy("alice");
        p.setSizeBytes(123L);
        return p;
    }

    private static Plugin plugin() {
        Plugin p = new Plugin("@demo/foo", "foo", "d", null);
        setId(p, 42L);
        return p;
    }

    private static void setId(Plugin plugin, Long id) {
        try {
            Field idField = Plugin.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(plugin, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to set plugin id", e);
        }
    }

    private static void assertWrapperContains(LambdaQueryWrapper<?> wrapper,
            String expectedSql, Object expectedValue) {
        assertThat(wrapper.getSqlSegment()).contains(expectedSql);
        assertThat(wrapper.getParamNameValuePairs()).containsValue(expectedValue);
    }

    @Test
    void lists_versions_using_mapper_order_with_dist_tags() {
        Plugin plugin = plugin();
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plugin);
        when(versions.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                pv("1.1.0", Instant.parse("2026-02-01T00:00:00Z")),
                pv("1.0.0", Instant.parse("2026-01-01T00:00:00Z"))));
        when(distTags.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                new DistTag(null, "latest", "1.1.0"), new DistTag(null, "stable", "1.0.0")));

        PluginVersionsView view = service.list("@demo/foo");

        assertThat(view.packageName()).isEqualTo("@demo/foo");
        assertThat(view.versions()).extracting(VersionDetail::version)
                .containsExactly("1.1.0", "1.0.0");   // publishedAt 降序
        assertThat(view.distTags()).containsEntry("latest", "1.1.0").containsEntry("stable", "1.0.0");
    }

    @Test
    void queries_package_and_published_versions_with_required_conditions() {
        Plugin plugin = plugin();
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plugin);
        when(versions.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                pv("1.1.0", Instant.parse("2026-02-01T00:00:00Z"))));
        when(distTags.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.list("@demo/foo");

        ArgumentCaptor<LambdaQueryWrapper<Plugin>> packageQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(plugins).selectOne(packageQuery.capture());
        assertWrapperContains(packageQuery.getValue(), "package_name", "@demo/foo");

        ArgumentCaptor<LambdaQueryWrapper<PluginVersion>> publishedQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(versions).selectList(publishedQuery.capture());
        LambdaQueryWrapper<PluginVersion> wrapper = publishedQuery.getValue();
        assertThat(wrapper.getSqlSegment()).contains("plugin_id", "status", "ORDER BY", "published_at");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(plugin.getId())
                .containsValue("PUBLISHED");
    }

    @Test
    void throws_404_when_package_missing() {
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.list("@x/none")).isInstanceOf(PackageNotFoundException.class);
    }

    @Test
    void throws_404_when_no_published_versions() {
        Plugin plugin = plugin();
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plugin);
        when(versions.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.list("@demo/foo")).isInstanceOf(PackageNotFoundException.class);
    }
}
