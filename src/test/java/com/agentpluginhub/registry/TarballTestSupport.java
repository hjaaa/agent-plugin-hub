package com.agentpluginhub.registry;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

// 测试工具:构造一个内含 package/package.json 的真实 .tgz 字节
public final class TarballTestSupport {

    private TarballTestSupport() {
    }

    public static byte[] tgzWithPackageJson(String json) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar =
                new TarArchiveOutputStream(new GzipCompressorOutputStream(bos))) {
            byte[] content = json.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry e = new TarArchiveEntry("package/package.json");
            e.setSize(content.length);
            tar.putArchiveEntry(e);
            tar.write(content);
            tar.closeArchiveEntry();
        }
        return bos.toByteArray();
    }
}
