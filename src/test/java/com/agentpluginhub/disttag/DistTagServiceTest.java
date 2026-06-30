package com.agentpluginhub.disttag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.common.VersionNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.agentpluginhub.publish.ValidationException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.lang.reflect.Field;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DistTagServiceTest {

    private final PluginMapper plugins = mock(PluginMapper.class);
    private final PluginVersionMapper versions = mock(PluginVersionMapper.class);
    private final DistTagMapper distTags = mock(DistTagMapper.class);
    private final DistTagService service = new DistTagService(plugins, versions, distTags);

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Plugin.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PluginVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), DistTag.class);
    }

    private Plugin plugin() {
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
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.setDistTag("@demo/foo", "stable", "1.0.0", "admin"))
                .isInstanceOf(PackageNotFoundException.class);
    }

    @Test
    void throws_when_version_not_published() {
        Plugin p = plugin();
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
        when(versions.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertThatThrownBy(() -> service.setDistTag("@demo/foo", "stable", "9.9.9", "admin"))
                .isInstanceOf(VersionNotFoundException.class);
    }

    @Test
    void upserts_existing_tag_and_returns_current_map() {
        Plugin p = plugin();
        DistTag stable = new DistTag(null, "stable", "1.0.0");
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
        when(versions.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(distTags.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stable);
        when(distTags.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                new DistTag(null, "latest", "1.1.0"), stable));

        var result = service.setDistTag("@demo/foo", "stable", "1.1.0", "admin");

        assertThat(stable.getVersion()).isEqualTo("1.1.0");          // 指针已移动
        assertThat(stable.getUpdatedBy()).isEqualTo("admin");        // 审计填充
        verify(distTags).updateById(stable);
        assertThat(result).containsEntry("stable", "1.1.0");
    }

    @Test
    void queries_package_and_published_version_with_required_conditions() {
        Plugin p = plugin();
        when(plugins.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
        when(versions.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(distTags.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(new DistTag(p.getId(), "stable", "1.0.0"));
        when(distTags.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.setDistTag("@demo/foo", "stable", "1.1.0", "admin");

        ArgumentCaptor<LambdaQueryWrapper<Plugin>> packageQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(plugins).selectOne(packageQuery.capture());
        assertWrapperContains(packageQuery.getValue(), "package_name", "@demo/foo");

        ArgumentCaptor<LambdaQueryWrapper<PluginVersion>> versionExistsQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(versions).selectCount(versionExistsQuery.capture());
        LambdaQueryWrapper<PluginVersion> wrapper = versionExistsQuery.getValue();
        assertThat(wrapper.getSqlSegment()).contains("plugin_id", "version", "status");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(p.getId())
                .containsValue("1.1.0")
                .containsValue("PUBLISHED");

        ArgumentCaptor<LambdaQueryWrapper<DistTag>> existingTagQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(distTags).selectOne(existingTagQuery.capture());
        LambdaQueryWrapper<DistTag> existingTagWrapper = existingTagQuery.getValue();
        assertThat(existingTagWrapper.getSqlSegment()).contains("plugin_id", "tag");
        assertThat(existingTagWrapper.getParamNameValuePairs()).containsValue(p.getId())
                .containsValue("stable");
    }
}
