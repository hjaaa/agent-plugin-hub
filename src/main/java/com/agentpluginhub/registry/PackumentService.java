package com.agentpluginhub.registry;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.registry.model.Dist;
import com.agentpluginhub.registry.model.Packument;
import com.agentpluginhub.registry.model.PackumentVersion;
import com.agentpluginhub.storage.ArtifactStore;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PackumentService {

    private final PluginCatalog catalog;
    private final ArtifactStore store;

    public PackumentService(PluginCatalog catalog, ArtifactStore store) {
        this.catalog = catalog;
        this.store = store;
    }

    // 为某 npm 包构建 packument;baseUrl 形如 http://host:port(无尾斜杠)
    public Packument build(String packageName, String baseUrl) {
        PluginEntry entry = catalog.require(packageName); // 未知包 → PackageNotFoundException
        Map<String, PackumentVersion> versions = new LinkedHashMap<>();
        for (VersionEntry v : entry.versions()) {
            byte[] bytes = store.load(v.tarball());
            Dist dist = new Dist(
                    baseUrl + "/registry/" + packageName + "/-/" + v.tarball(),
                    IntegrityUtil.sriSha512(bytes),
                    IntegrityUtil.hexSha1(bytes));
            versions.put(v.version(), new PackumentVersion(packageName, v.version(), dist));
        }
        return new Packument(packageName, packageName, entry.distTags(), versions);
    }
}
