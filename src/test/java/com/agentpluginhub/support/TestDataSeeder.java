package com.agentpluginhub.support;

import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.mapper.DistTagMapper;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.agentpluginhub.storage.ArtifactStore;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import org.springframework.stereotype.Component;

// 集成测试辅助:直接落一个 PUBLISHED 插件(plugin + version + dist_tag latest)并把产物放进 store。
@Component
public class TestDataSeeder {

    private final PluginMapper plugins;
    private final PluginVersionMapper versions;
    private final DistTagMapper distTags;
    private final ArtifactStore store;

    public TestDataSeeder(PluginMapper plugins, PluginVersionMapper versions,
            DistTagMapper distTags, ArtifactStore store) {
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
        this.store = store;
    }

    public void publish(String packageName, String pluginName, String version,
            String tarballRef, byte[] tarball) {
        Plugin plugin = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                .eq(Plugin::getPackageName, packageName))
                .orElseGet(() -> {
                    Plugin created = new Plugin(packageName, pluginName, "demo", "team");
                    plugins.insert(created);
                    return created;
                });
        // 幂等:同一版本已存在则跳过插入,避免跨测试类共享容器时违反唯一约束
        if (!MapperQueries.exists(versions, Wrappers.<PluginVersion>lambdaQuery()
                .eq(PluginVersion::getPluginId, plugin.getId())
                .eq(PluginVersion::getVersion, version))) {
            store.save(tarballRef, tarball);
            PluginVersion pv = new PluginVersion();
            pv.setPluginId(plugin.getId());
            pv.setVersion(version);
            pv.setTarballRef(tarballRef);
            pv.setIntegrity(com.agentpluginhub.common.IntegrityUtil.sriSha512(tarball));
            pv.setShasum(com.agentpluginhub.common.IntegrityUtil.hexSha1(tarball));
            pv.setSizeBytes(tarball.length);
            pv.setStatus("PUBLISHED");
            pv.setUploadedBy("seeder");
            pv.setPublishedAt(Instant.now());
            versions.insert(pv);
        }
        MapperQueries.one(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, plugin.getId())
                .eq(DistTag::getTag, "latest"))
                .ifPresentOrElse(t -> { t.setVersion(version); distTags.updateById(t); },
                        () -> distTags.insert(new DistTag(plugin.getId(), "latest", version)));
        // M2:种子插件也设 stable(镜像"首发即设 stable"),使其出现在 marketplace.json
        if (!MapperQueries.exists(distTags, Wrappers.<DistTag>lambdaQuery()
                .eq(DistTag::getPluginId, plugin.getId())
                .eq(DistTag::getTag, "stable"))) {
            distTags.insert(new DistTag(plugin.getId(), "stable", version));
        }
    }
}
