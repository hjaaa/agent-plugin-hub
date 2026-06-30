package com.agentpluginhub.disttag;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.common.VersionNotFoundException;
import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersionRepository;
import com.agentpluginhub.publish.ValidationException;
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

    private final PluginRepository plugins;
    private final PluginVersionRepository versions;
    private final DistTagRepository distTags;

    public DistTagService(PluginRepository plugins, PluginVersionRepository versions,
            DistTagRepository distTags) {
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
        Plugin plugin = plugins.findByPackageName(packageName)
                .orElseThrow(() -> new PackageNotFoundException(packageName));
        if (!versions.existsByPluginIdAndVersionAndStatus(plugin.getId(), version, PUBLISHED)) {
            throw new VersionNotFoundException(packageName, version);
        }
        Instant ts = Instant.now();
        distTags.findByPluginIdAndTag(plugin.getId(), tag)
                .ifPresentOrElse(
                        t -> { t.apply(version, subject, ts); distTags.save(t); },
                        () -> distTags.save(new DistTag(plugin.getId(), tag, version, subject, ts)));
        Map<String, String> current = new LinkedHashMap<>();
        for (DistTag t : distTags.findByPluginId(plugin.getId())) {
            current.put(t.getTag(), t.getVersion());
        }
        return current;
    }
}
