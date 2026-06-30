package com.agentpluginhub.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

// 合法的自包含插件 tarball(含 package.json + .claude-plugin/plugin.json),供 IT 走真实 publish。
public final class TestTarballs {

    private TestTarballs() {
    }

    public static byte[] plugin(String pkg, String version) throws Exception {
        String pluginName = pkg.contains("/") ? pkg.substring(pkg.indexOf('/') + 1) : pkg;
        return plugin(pkg, version, pluginName, "d");
    }

    public static byte[] plugin(String pkg, String version, String pluginName, String desc) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Map<String, String> entries = Map.of(
                "package/package.json", "{\"name\":\"" + pkg + "\",\"version\":\"" + version + "\"}",
                "package/.claude-plugin/plugin.json",
                "{\"name\":\"" + pluginName + "\",\"description\":\"" + desc + "\",\"version\":\"" + version + "\"}");
        try (TarArchiveOutputStream tar =
                new TarArchiveOutputStream(new GzipCompressorOutputStream(bos))) {
            for (Map.Entry<String, String> en : entries.entrySet()) {
                byte[] c = en.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry e = new TarArchiveEntry(en.getKey());
                e.setSize(c.length);
                tar.putArchiveEntry(e);
                tar.write(c);
                tar.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }
}
