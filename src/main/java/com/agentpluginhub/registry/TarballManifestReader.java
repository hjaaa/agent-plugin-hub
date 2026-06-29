package com.agentpluginhub.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.util.Optional;
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
        try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(new ByteArrayInputStream(tgz));
                TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isFile() && "package/package.json".equals(entry.getName())) {
                    if (entry.getSize() > MAX_MANIFEST_BYTES) {
                        log.warn("package.json in tarball too large ({} bytes), skipping", entry.getSize());
                        return Optional.empty();
                    }
                    byte[] content = tar.readAllBytes(); // 读取量受 tar 声明的 entry 大小限制
                    return Optional.of((ObjectNode) mapper.readTree(content));
                }
            }
        } catch (Exception e) {
            log.warn("failed to read package.json from tarball: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
