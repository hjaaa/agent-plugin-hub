package com.agentpluginhub.catalog;

import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// M1:从 DB 构建只读目录视图。仅暴露含 PUBLISHED 版本的插件;dist-tags 取自 dist_tag 表。
// 保持 M0 的 all()/find()/require() 与 PluginEntry/VersionEntry 形状不变,上游读路径无感。
@Component
public class PluginCatalog {

    private static final String PUBLISHED = "PUBLISHED";

    private final PluginMapper plugins;
    private final PluginVersionMapper versions;
    private final DistTagMapper distTags;

    public PluginCatalog(PluginMapper plugins, PluginVersionMapper versions,
            DistTagMapper distTags) {
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
    }

    public List<PluginEntry> all() {
        List<Plugin> allPlugins = plugins.selectList(Wrappers.lambdaQuery());
        if (allPlugins.isEmpty()) {
            return List.of();
        }
        List<Long> ids = allPlugins.stream().map(Plugin::getId).toList();
        // 三次查询替代 1+2N:全量插件 + 批量 versions + 批量 dist-tags,按 pluginId 分组
        Map<Long, List<PluginVersion>> versionsByPlugin = versions
                .selectList(Wrappers.<PluginVersion>lambdaQuery()
                        .in(PluginVersion::getPluginId, ids)
                        .eq(PluginVersion::getStatus, PUBLISHED)).stream()
                .collect(Collectors.groupingBy(PluginVersion::getPluginId));
        Map<Long, List<DistTag>> tagsByPlugin = distTags.selectList(Wrappers.<DistTag>lambdaQuery()
                        .in(DistTag::getPluginId, ids)).stream()
                .collect(Collectors.groupingBy(DistTag::getPluginId));
        List<PluginEntry> result = new ArrayList<>();
        for (Plugin p : allPlugins) {
            buildEntry(p,
                    versionsByPlugin.getOrDefault(p.getId(), List.of()),
                    tagsByPlugin.getOrDefault(p.getId(), List.of()))
                    .ifPresent(result::add);
        }
        return result;
    }

    public Optional<PluginEntry> find(String packageName) {
        return MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, packageName)).flatMap(this::toEntry);
    }

    public PluginEntry require(String packageName) {
        return find(packageName).orElseThrow(() -> new PackageNotFoundException(packageName));
    }

    // 单插件(find/require)走单查;聚合逻辑与 all() 共用 buildEntry
    private Optional<PluginEntry> toEntry(Plugin p) {
        return buildEntry(p,
                versions.selectList(Wrappers.<PluginVersion>lambdaQuery()
                        .eq(PluginVersion::getPluginId, p.getId())
                        .eq(PluginVersion::getStatus, PUBLISHED)),
                distTags.selectList(Wrappers.<DistTag>lambdaQuery()
                        .eq(DistTag::getPluginId, p.getId())));
    }

    // 无 PUBLISHED 版本则视为不存在(Optional.empty);否则渲成 PluginEntry
    private Optional<PluginEntry> buildEntry(Plugin p, List<PluginVersion> published, List<DistTag> tags) {
        if (published.isEmpty()) {
            return Optional.empty();
        }
        List<VersionEntry> vs = new ArrayList<>();
        for (PluginVersion pv : published) {
            vs.add(new VersionEntry(pv.getVersion(), pv.getTarballRef()));
        }
        Map<String, String> tagMap = new LinkedHashMap<>();
        for (DistTag t : tags) {
            tagMap.put(t.getTag(), t.getVersion());
        }
        return Optional.of(new PluginEntry(p.getPackageName(), p.getPluginName(),
                p.getDescription(), tagMap, vs));
    }
}
