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
import com.agentpluginhub.registry.model.PackumentVersion;
import com.agentpluginhub.storage.ArtifactStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
        return new PackumentService(catalog, store);
    }

    @Test
    void should_build_packument_with_dist_when_package_exists() {
        Packument doc = serviceWithOnePackage().build("@demo/hello-plugin", "http://localhost:8080");

        assertThat(doc.name()).isEqualTo("@demo/hello-plugin");
        assertThat(doc.id()).isEqualTo("@demo/hello-plugin");
        assertThat(doc.distTags()).containsEntry("latest", "1.0.0");

        PackumentVersion v = doc.versions().get("1.0.0");
        assertThat(v.name()).isEqualTo("@demo/hello-plugin");
        assertThat(v.version()).isEqualTo("1.0.0");
        assertThat(v.dist().tarball())
                .isEqualTo("http://localhost:8080/registry/@demo/hello-plugin/-/demo-hello-plugin-1.0.0.tgz");
        // 完整性必须与同一份字节自洽(npm 下载后会校验)
        assertThat(v.dist().integrity()).isEqualTo(IntegrityUtil.sriSha512(TARBALL));
        assertThat(v.dist().shasum()).isEqualTo(IntegrityUtil.hexSha1(TARBALL));
    }

    @Test
    void should_throw_when_package_unknown() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        when(catalog.require("@x/none")).thenThrow(new PackageNotFoundException("@x/none"));
        PackumentService svc = new PackumentService(catalog, store);

        assertThatThrownBy(() -> svc.build("@x/none", "http://localhost:8080"))
                .isInstanceOf(PackageNotFoundException.class);
    }
}
