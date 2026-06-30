package com.agentpluginhub.catalog;

import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.domain.PluginVersionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// M1:从 DB 构建只读目录视图。仅暴露含 PUBLISHED 版本的插件;dist-tags 取自 dist_tag 表。
// 保持 M0 的 all()/find()/require() 与 PluginEntry/VersionEntry 形状不变,上游读路径无感。
@Component
public class PluginCatalog {

    private static final String PUBLISHED = "PUBLISHED";

    private final PluginRepository plugins;
    private final PluginVersionRepository versions;
    private final DistTagRepository distTags;

    public PluginCatalog(PluginRepository plugins, PluginVersionRepository versions,
            DistTagRepository distTags) {
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
    }

    public List<PluginEntry> all() {
        List<PluginEntry> result = new ArrayList<>();
        for (Plugin p : plugins.findAll()) {
            toEntry(p).ifPresent(result::add);
        }
        return result;
    }

    public Optional<PluginEntry> find(String packageName) {
        return plugins.findByPackageName(packageName).flatMap(this::toEntry);
    }

    public PluginEntry require(String packageName) {
        return find(packageName).orElseThrow(() -> new PackageNotFoundException(packageName));
    }

    // 把一个 Plugin 聚合成 PluginEntry;无 PUBLISHED 版本则视为不存在(Optional.empty)
    private Optional<PluginEntry> toEntry(Plugin p) {
        List<PluginVersion> published = versions.findByPluginIdAndStatus(p.getId(), PUBLISHED);
        if (published.isEmpty()) {
            return Optional.empty();
        }
        List<VersionEntry> vs = new ArrayList<>();
        for (PluginVersion pv : published) {
            vs.add(new VersionEntry(pv.getVersion(), pv.getTarballRef()));
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (DistTag t : distTags.findByPluginId(p.getId())) {
            tags.put(t.getTag(), t.getVersion());
        }
        return Optional.of(new PluginEntry(p.getPackageName(), p.getPluginName(),
                p.getDescription(), tags, vs));
    }
}
