package com.agentpluginhub.versions;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 版本治理读:列某 plugin 所有 PUBLISHED 版本(publishedAt 降序)+ 各 dist-tag 现状。
@Service
public class VersionQueryService {

    private static final String PUBLISHED = "PUBLISHED";

    private final PluginMapper plugins;
    private final PluginVersionMapper versions;
    private final DistTagMapper distTags;

    public VersionQueryService(PluginMapper plugins, PluginVersionMapper versions,
            DistTagMapper distTags) {
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
    }

    @Transactional(readOnly = true)
    public PluginVersionsView list(String packageName) {
        Plugin plugin = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                        .eq(Plugin::getPackageName, packageName))
                .orElseThrow(() -> new PackageNotFoundException(packageName));
        List<PluginVersion> published = versions.selectList(Wrappers.<PluginVersion>lambdaQuery()
                .eq(PluginVersion::getPluginId, plugin.getId())
                .eq(PluginVersion::getStatus, PUBLISHED)
                .orderByDesc(PluginVersion::getPublishedAt));
        if (published.isEmpty()) {
            throw new PackageNotFoundException(packageName);   // 无 PUBLISHED 版本视为不存在
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (DistTag t : distTags.selectList(Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, plugin.getId()))) {
            tags.put(t.getTag(), t.getVersion());
        }
        List<VersionDetail> details = published.stream()
                .sorted(Comparator.comparing(PluginVersion::getPublishedAt).reversed())
                .map(pv -> new VersionDetail(pv.getVersion(), pv.getStatus(),
                        pv.getPublishedAt(), pv.getUploadedBy(), pv.getSizeBytes()))
                .toList();
        return new PluginVersionsView(packageName, tags, details);
    }
}
