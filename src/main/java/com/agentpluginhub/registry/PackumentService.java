package com.agentpluginhub.registry;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.registry.model.Dist;
import com.agentpluginhub.registry.model.Packument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.agentpluginhub.storage.ArtifactStore;

@Service
public class PackumentService {

    private static final Logger log = LoggerFactory.getLogger(PackumentService.class);

    private final PluginCatalog catalog;
    private final ArtifactStore store;
    private final TarballManifestReader manifestReader;
    private final ObjectMapper mapper;

    public PackumentService(PluginCatalog catalog, ArtifactStore store,
            TarballManifestReader manifestReader, ObjectMapper mapper) {
        this.catalog = catalog;
        this.store = store;
        this.manifestReader = manifestReader;
        this.mapper = mapper;
    }

    public Packument build(String packageName, String baseUrl) {
        PluginEntry entry = catalog.require(packageName);
        Map<String, ObjectNode> versions = new LinkedHashMap<>();
        for (VersionEntry v : entry.versions()) {
            byte[] bytes = store.load(v.tarball());
            Dist dist = new Dist(
                    baseUrl + "/registry/" + packageName + "/-/" + v.tarball(),
                    IntegrityUtil.sriSha512(bytes),
                    IntegrityUtil.hexSha1(bytes));
            // 真实 npm version 对象应内含整个 package.json(含 dependencies),npm 据此解析依赖;
            // tarball 无可读 package.json 时降级为最小对象(name/version/dist)并告警。
            ObjectNode versionNode = manifestReader.readPackageJson(bytes)
                    .orElseGet(() -> {
                        log.warn("tarball {} has no readable package.json; serving minimal version object",
                                v.tarball());
                        return mapper.createObjectNode();
                    });
            versionNode.put("name", packageName);
            versionNode.put("version", v.version());
            versionNode.set("dist", mapper.valueToTree(dist));
            versions.put(v.version(), versionNode);
        }
        return new Packument(packageName, packageName, entry.distTags(), versions);
    }
}
