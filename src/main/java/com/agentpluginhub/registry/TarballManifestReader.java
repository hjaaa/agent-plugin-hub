package com.agentpluginhub.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TarballManifestReader {

    private static final Logger log = LoggerFactory.getLogger(TarballManifestReader.class);
    private static final long MAX_MANIFEST_BYTES = 1_048_576L; // 1 MiB:package.json 不可能这么大

    private final ObjectMapper mapper;

    public TarballManifestReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // 从 npm tarball(.tgz)提取 package/package.json 并解析为 ObjectNode;读不到/解析失败返回 empty
    public Optional<ObjectNode> readPackageJson(byte[] tgz) {
        return readEntry(tgz, "package/package.json");
    }

    // 从 npm tarball 提取 package/.claude-plugin/plugin.json;读不到/解析失败返回 empty
    public Optional<ObjectNode> readClaudePluginJson(byte[] tgz) {
        return readEntry(tgz, "package/.claude-plugin/plugin.json");
    }

    // 列出 tarball 内 package/node_modules/ 下实际存在的顶层包名(含 scoped: @scope/name)。
    // 用于校验 bundleDependencies 声明的包是否真的打包进了 tarball(本平台不代理上游 npm)。
    public Set<String> listBundledModuleNames(byte[] tgz) {
        Set<String> names = new HashSet<>();
        String prefix = "package/node_modules/";
        try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(new ByteArrayInputStream(tgz));
                TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String[] parts = name.substring(prefix.length()).split("/");
                if (parts[0].isEmpty()) {
                    continue;
                }
                if (parts[0].startsWith("@")) {
                    if (parts.length >= 2 && !parts[1].isEmpty()) {
                        names.add(parts[0] + "/" + parts[1]);   // scoped 包顶层名
                    }
                } else {
                    names.add(parts[0]);
                }
            }
        } catch (Exception e) {
            log.warn("failed to list node_modules from tarball: {}", e.getMessage());
        }
        return names;
    }

    private Optional<ObjectNode> readEntry(byte[] tgz, String entryName) {
        try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(new ByteArrayInputStream(tgz));
                TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isFile() && entryName.equals(entry.getName())) {
                    if (entry.getSize() > MAX_MANIFEST_BYTES) {
                        log.warn("{} in tarball too large ({} bytes), skipping", entryName, entry.getSize());
                        return Optional.empty();
                    }
                    byte[] content = tar.readAllBytes();
                    return Optional.of((ObjectNode) mapper.readTree(content));
                }
            }
        } catch (Exception e) {
            log.warn("failed to read {} from tarball: {}", entryName, e.getMessage());
        }
        return Optional.empty();
    }
}
