package com.agentpluginhub.disttag;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.common.VersionNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.agentpluginhub.publish.ValidationException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 渠道指针管理:把 latest|stable 移到某 PUBLISHED 版本(提升 / 回滚)。校验 + upsert + 审计;幂等。
@Service
public class DistTagService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final Set<String> ALLOWED_TAGS = Set.of("latest", "stable");

    private final PluginMapper plugins;
    private final PluginVersionMapper versions;
    private final DistTagMapper distTags;

    public DistTagService(PluginMapper plugins, PluginVersionMapper versions,
            DistTagMapper distTags) {
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
    }

    @Transactional
    public Map<String, String> setDistTag(String packageName, String tag, String version, String subject) {
        if (!ALLOWED_TAGS.contains(tag)) {
            throw new ValidationException("INVALID_DIST_TAG", "dist-tag 只能是 latest 或 stable:" + tag);
        }
        if (version == null || version.isBlank()) {
            throw new ValidationException("VERSION_REQUIRED", "version 不能为空");
        }
        Plugin plugin = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                        .eq(Plugin::getPackageName, packageName))
                .orElseThrow(() -> new PackageNotFoundException(packageName));
        if (!MapperQueries.exists(versions, Wrappers.<PluginVersion>lambdaQuery()
                .eq(PluginVersion::getPluginId, plugin.getId())
                .eq(PluginVersion::getVersion, version)
                .eq(PluginVersion::getStatus, PUBLISHED))) {
            throw new VersionNotFoundException(packageName, version);
        }
        Instant ts = Instant.now();
        MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                        .eq(DistTag::getPluginId, plugin.getId())
                        .eq(DistTag::getTag, tag))
                .ifPresentOrElse(
                        t -> { t.apply(version, subject, ts); distTags.updateById(t); },
                        () -> distTags.insert(new DistTag(plugin.getId(), tag, version, subject, ts)));
        Map<String, String> current = new LinkedHashMap<>();
        for (DistTag t : distTags.selectList(Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, plugin.getId()))) {
            current.put(t.getTag(), t.getVersion());
        }
        return current;
    }
}
