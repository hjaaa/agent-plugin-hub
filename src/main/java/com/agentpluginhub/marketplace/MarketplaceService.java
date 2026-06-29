package com.agentpluginhub.marketplace;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.marketplace.model.Marketplace;
import com.agentpluginhub.marketplace.model.NpmSource;
import com.agentpluginhub.marketplace.model.PluginRef;
import com.agentpluginhub.registry.TarballManifestReader;
import com.agentpluginhub.storage.ArtifactStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    private final PluginCatalog catalog;
    private final ArtifactStore store;
    private final TarballManifestReader manifestReader;

    public MarketplaceService(PluginCatalog catalog, ArtifactStore store,
            TarballManifestReader manifestReader) {
        this.catalog = catalog;
        this.store = store;
        this.manifestReader = manifestReader;
    }

    // 把目录里的插件渲染成 CC 认的 marketplace.json。
    // 铁律(spec §7):坏插件跳过 + 记日志,永远吐合法 JSON。
    // M0:仅支持自包含插件——带未打包外部依赖的插件无法从本 registry 装上,跳过不广告(上游代理留 M1)。
    public Marketplace render(String baseUrl) {
        List<PluginRef> refs = new ArrayList<>();
        for (PluginEntry e : catalog.all()) {
            try {
                String latest = e.distTags() == null ? null : e.distTags().get("latest");
                if (latest == null || e.packageName() == null || e.pluginName() == null) {
                    log.warn("skip plugin with incomplete metadata: {}", e.packageName());
                    continue;
                }
                if (hasUnservableDependencies(e, latest)) {
                    log.warn("skip plugin {} with external npm dependencies (M0 supports self-contained plugins only)",
                            e.packageName());
                    continue;
                }
                NpmSource src = new NpmSource("npm", e.packageName(), latest, baseUrl + "/registry");
                refs.add(new PluginRef(e.pluginName(), e.description(), src));
            } catch (RuntimeException ex) {
                log.warn("skip bad plugin {}", e.packageName(), ex);
            }
        }
        return new Marketplace("agent-plugin-hub", Map.of("name", "agent-plugin-hub"), refs);
    }

    // 读取 latest 版本 tarball 的 package.json,判断是否存在需向 registry 解析的外部依赖。
    // 产物读不到 / manifest 不可读时无法判定,M0 从宽广告(交由安装时暴露)。
    private boolean hasUnservableDependencies(PluginEntry e, String latest) {
        String tarball = e.versions() == null ? null : e.versions().stream()
                .filter(v -> latest.equals(v.version()))
                .map(VersionEntry::tarball)
                .findFirst().orElse(null);
        if (tarball == null) {
            return false;
        }
        byte[] bytes;
        try {
            bytes = store.load(tarball);
        } catch (RuntimeException ex) {
            return false;
        }
        Optional<ObjectNode> manifest = manifestReader.readPackageJson(bytes);
        return manifest.map(this::manifestHasExternalDependencies).orElse(false);
    }

    private boolean manifestHasExternalDependencies(ObjectNode manifest) {
        Set<String> bundled = bundledNames(manifest);
        // 常规 dependencies(非 bundle)需向 registry 解析
        if (hasNonBundled(manifest.get("dependencies"), bundled)) {
            return true;
        }
        // 非可选 peerDependencies:npm v7+ 默认安装,也会向 registry 解析
        JsonNode peers = manifest.get("peerDependencies");
        if (peers != null && peers.isObject() && !peers.isEmpty()) {
            Set<String> optionalPeers = optionalPeerNames(manifest);
            for (Iterator<String> it = peers.fieldNames(); it.hasNext();) {
                String name = it.next();
                if (!bundled.contains(name) && !optionalPeers.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNonBundled(JsonNode deps, Set<String> bundled) {
        if (deps == null || !deps.isObject() || deps.isEmpty()) {
            return false;
        }
        for (Iterator<String> it = deps.fieldNames(); it.hasNext();) {
            if (!bundled.contains(it.next())) {
                return true;
            }
        }
        return false;
    }

    // peerDependenciesMeta.<name>.optional == true 的 peer 视为可选,不阻断安装
    private Set<String> optionalPeerNames(ObjectNode manifest) {
        Set<String> names = new HashSet<>();
        JsonNode meta = manifest.get("peerDependenciesMeta");
        if (meta != null && meta.isObject()) {
            meta.fields().forEachRemaining(en -> {
                JsonNode opt = en.getValue() == null ? null : en.getValue().get("optional");
                if (opt != null && opt.asBoolean(false)) {
                    names.add(en.getKey());
                }
            });
        }
        return names;
    }

    private Set<String> bundledNames(ObjectNode manifest) {
        JsonNode b = manifest.has("bundleDependencies")
                ? manifest.get("bundleDependencies")
                : manifest.get("bundledDependencies");
        Set<String> names = new HashSet<>();
        if (b == null) {
            return names;
        }
        // bundleDependencies: true 表示 dependencies 全部打包进 tarball
        if (b.isBoolean() && b.asBoolean()) {
            JsonNode deps = manifest.get("dependencies");
            if (deps != null && deps.isObject()) {
                deps.fieldNames().forEachRemaining(names::add);
            }
            return names;
        }
        if (b.isArray()) {
            b.forEach(n -> names.add(n.asText()));
        }
        return names;
    }
}
