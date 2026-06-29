package com.agentpluginhub.support;

import com.agentpluginhub.domain.DistTag;
import com.agentpluginhub.domain.DistTagRepository;
import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.domain.PluginVersionRepository;
import com.agentpluginhub.storage.ArtifactStore;
import java.time.Instant;
import org.springframework.stereotype.Component;

// 集成测试辅助:直接落一个 PUBLISHED 插件(plugin + version + dist_tag latest)并把产物放进 store。
@Component
public class TestDataSeeder {

    private final PluginRepository plugins;
    private final PluginVersionRepository versions;
    private final DistTagRepository distTags;
    private final ArtifactStore store;

    public TestDataSeeder(PluginRepository plugins, PluginVersionRepository versions,
            DistTagRepository distTags, ArtifactStore store) {
        this.plugins = plugins;
        this.versions = versions;
        this.distTags = distTags;
        this.store = store;
    }

    public void publish(String packageName, String pluginName, String version,
            String tarballRef, byte[] tarball) {
        Plugin plugin = plugins.findByPackageName(packageName)
                .orElseGet(() -> plugins.save(new Plugin(packageName, pluginName, "demo", "team")));
        // 幂等:同一版本已存在则跳过插入,避免跨测试类共享容器时违反唯一约束
        if (versions.findByPluginIdAndVersion(plugin.getId(), version).isEmpty()) {
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
            versions.save(pv);
        }
        distTags.findByPluginIdAndTag(plugin.getId(), "latest")
                .ifPresentOrElse(t -> { t.setVersion(version); distTags.save(t); },
                        () -> distTags.save(new DistTag(plugin.getId(), "latest", version)));
    }
}
