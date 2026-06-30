package com.agentpluginhub.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.springframework.stereotype.Component;

// 判断 package.json 是否含需向 registry 解析的外部依赖(本平台不代理上游 → 这类插件装不上)。
// 可选 peer 不算外部;bundleDependencies(数组/布尔)声明的包还须实际存在于 tarball node_modules
// (presentModules)才算自包含,否则仍判为外部依赖。
@Component
public class DependencyInspector {

    public boolean hasExternalDependencies(ObjectNode manifest, Set<String> presentModules) {
        Set<String> bundled = bundledNames(manifest, presentModules);
        if (hasNonBundled(manifest.get("dependencies"), bundled)) {
            return true;
        }
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

    // 仅当声明的 bundleDependencies 确实存在于 tarball node_modules(presentModules)中,才视为已打包。
    // 声明却未实际打包的依赖会落回 hasNonBundled,被判为外部依赖(本平台不代理上游 npm,装不上)。
    private Set<String> bundledNames(ObjectNode manifest, Set<String> presentModules) {
        JsonNode b = manifest.has("bundleDependencies")
                ? manifest.get("bundleDependencies")
                : manifest.get("bundledDependencies");
        Set<String> names = new HashSet<>();
        if (b == null) {
            return names;
        }
        if (b.isBoolean() && b.asBoolean()) {
            JsonNode deps = manifest.get("dependencies");
            if (deps != null && deps.isObject()) {
                deps.fieldNames().forEachRemaining(n -> {
                    if (presentModules.contains(n)) {
                        names.add(n);
                    }
                });
            }
            return names;
        }
        if (b.isArray()) {
            b.forEach(n -> {
                String nm = n.asText();
                if (presentModules.contains(nm)) {
                    names.add(nm);
                }
            });
        }
        return names;
    }
}
