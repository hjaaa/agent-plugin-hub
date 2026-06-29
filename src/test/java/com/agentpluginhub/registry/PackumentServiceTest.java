package com.agentpluginhub.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.registry.model.Packument;
import com.agentpluginhub.storage.ArtifactStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

class PackumentServiceTest {

    private static final byte[] TARBALL = "hello-tarball".getBytes(StandardCharsets.UTF_8);

    private PackumentService serviceWithOnePackage() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        PluginEntry entry = new PluginEntry(
                "@demo/hello-plugin", "hello-plugin", "desc",
                Map.of("latest", "1.0.0"),
                List.of(new VersionEntry("1.0.0", "demo-hello-plugin-1.0.0.tgz")));
        when(catalog.require("@demo/hello-plugin")).thenReturn(entry);
        when(store.load("demo-hello-plugin-1.0.0.tgz")).thenReturn(TARBALL);
        ObjectMapper mapper = new ObjectMapper();
        return new PackumentService(catalog, store, new TarballManifestReader(mapper), mapper);
    }

    @Test
    void should_build_packument_with_dist_when_package_exists() {
        Packument doc = serviceWithOnePackage().build("@demo/hello-plugin", "http://localhost:8080");

        assertThat(doc.name()).isEqualTo("@demo/hello-plugin");
        assertThat(doc.id()).isEqualTo("@demo/hello-plugin");
        assertThat(doc.distTags()).containsEntry("latest", "1.0.0");

        ObjectNode v = doc.versions().get("1.0.0");
        assertThat(v.get("name").asText()).isEqualTo("@demo/hello-plugin");
        assertThat(v.get("version").asText()).isEqualTo("1.0.0");
        assertThat(v.get("dist").get("tarball").asText())
                .isEqualTo("http://localhost:8080/registry/@demo/hello-plugin/-/demo-hello-plugin-1.0.0.tgz");
        // 完整性必须与同一份字节自洽(npm 下载后会校验)
        assertThat(v.get("dist").get("integrity").asText()).isEqualTo(IntegrityUtil.sriSha512(TARBALL));
        assertThat(v.get("dist").get("shasum").asText()).isEqualTo(IntegrityUtil.hexSha1(TARBALL));
    }

    @Test
    void should_throw_when_package_unknown() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        when(catalog.require("@x/none")).thenThrow(new PackageNotFoundException("@x/none"));
        ObjectMapper mapper = new ObjectMapper();
        PackumentService svc = new PackumentService(catalog, store, new TarballManifestReader(mapper), mapper);

        assertThatThrownBy(() -> svc.build("@x/none", "http://localhost:8080"))
                .isInstanceOf(PackageNotFoundException.class);
    }

    @Test
    void should_include_dependencies_from_tarball_manifest() throws Exception {
        byte[] tgz = tgzWithPackageJson(
                "{\"name\":\"@demo/dep\",\"version\":\"2.0.0\",\"dependencies\":{\"left-pad\":\"^1.3.0\"}}");
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        PluginEntry entry = new PluginEntry("@demo/dep", "dep", "d",
                Map.of("latest", "2.0.0"),
                List.of(new VersionEntry("2.0.0", "demo-dep-2.0.0.tgz")));
        when(catalog.require("@demo/dep")).thenReturn(entry);
        when(store.load("demo-dep-2.0.0.tgz")).thenReturn(tgz);
        ObjectMapper mapper = new ObjectMapper();
        PackumentService svc =
                new PackumentService(catalog, store, new TarballManifestReader(mapper), mapper);

        Packument doc = svc.build("@demo/dep", "http://h");

        ObjectNode v = doc.versions().get("2.0.0");
        assertThat(v.get("dependencies").get("left-pad").asText()).isEqualTo("^1.3.0");
        assertThat(v.get("name").asText()).isEqualTo("@demo/dep");
        assertThat(v.get("version").asText()).isEqualTo("2.0.0");
        assertThat(v.get("dist").get("tarball").asText())
                .isEqualTo("http://h/registry/@demo/dep/-/demo-dep-2.0.0.tgz");
        assertThat(v.get("dist").get("integrity").asText()).isEqualTo(IntegrityUtil.sriSha512(tgz));
        assertThat(v.get("dist").get("shasum").asText()).isEqualTo(IntegrityUtil.hexSha1(tgz));
    }

    private static byte[] tgzWithPackageJson(String json) throws Exception {
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
