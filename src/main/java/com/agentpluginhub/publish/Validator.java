package com.agentpluginhub.publish;

import com.agentpluginhub.registry.TarballManifestReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

// 发布期结构/元数据校验:package.json、.claude-plugin/plugin.json、保留名、外部依赖硬拒。
@Component
public class Validator {

    // 保留名(精确)与保留前缀;含仿冒变体的从简:前缀匹配覆盖 anthropic-* / claude-plugins-*
    private static final List<String> RESERVED_EXACT = List.of("claude-code-marketplace");
    private static final List<String> RESERVED_PREFIX = List.of("anthropic-", "claude-plugins-", "claude-code-");

    private final TarballManifestReader reader;
    private final DependencyInspector inspector;

    public Validator(TarballManifestReader reader, DependencyInspector inspector) {
        this.reader = reader;
        this.inspector = inspector;
    }

    public ValidationResult validate(byte[] tarball) {
        ObjectNode pkg = reader.readPackageJson(tarball)
                .orElseThrow(() -> new ValidationException("PACKAGE_JSON_MISSING",
                        "tarball 内缺少可读的 package/package.json"));
        String packageName = text(pkg, "name");
        String version = text(pkg, "version");
        if (packageName == null || version == null) {
            throw new ValidationException("PACKAGE_JSON_INVALID",
                    "package.json 缺少 name 或 version");
        }

        ObjectNode plugin = reader.readClaudePluginJson(tarball)
                .orElseThrow(() -> new ValidationException("PLUGIN_JSON_MISSING",
                        "tarball 内缺少可读的 package/.claude-plugin/plugin.json"));
        String pluginName = text(plugin, "name");
        if (pluginName == null) {
            throw new ValidationException("PLUGIN_JSON_INVALID",
                    ".claude-plugin/plugin.json 缺少 name");
        }
        if (isReserved(pluginName)) {
            throw new ValidationException("RESERVED_NAME",
                    "插件名命中保留名/保留前缀,不可用:" + pluginName);
        }

        // 收集 tarball 内实际打包的 node_modules 顶层包名,校验 bundleDependencies 是否真打包
        Set<String> presentModules = reader.listBundledModuleNames(tarball);
        if (inspector.hasExternalDependencies(pkg, presentModules)) {
            throw new ValidationException("EXTERNAL_DEPENDENCY",
                    "插件声明了未打包的外部依赖;M1 仅支持自包含插件,请将依赖 bundle 进 tarball 或移除");
        }

        String description = text(plugin, "description");
        if (description == null) {
            description = text(pkg, "description");
        }
        return new ValidationResult(packageName, version, pluginName, description);
    }

    private static String text(ObjectNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && v.isTextual() && !v.asText().isBlank()) ? v.asText() : null;
    }

    private static boolean isReserved(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (RESERVED_EXACT.contains(n)) {
            return true;
        }
        return RESERVED_PREFIX.stream().anyMatch(n::startsWith);
    }
}
