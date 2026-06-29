package com.agentpluginhub.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TarballManifestReader {

    private static final Logger log = LoggerFactory.getLogger(TarballManifestReader.class);

    private final ObjectMapper mapper;

    public TarballManifestReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // 从 npm tarball(.tgz)提取 package/package.json 并解析为 ObjectNode;读不到/解析失败返回 empty
    public Optional<ObjectNode> readPackageJson(byte[] tgz) {
        try (TarArchiveInputStream tar =
                new TarArchiveInputStream(new GZIPInputStream(new ByteArrayInputStream(tgz)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isFile() && "package/package.json".equals(entry.getName())) {
                    byte[] content = tar.readAllBytes();
                    return Optional.of((ObjectNode) mapper.readTree(content));
                }
            }
        } catch (Exception e) {
            log.warn("failed to read package.json from tarball: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
