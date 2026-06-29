package com.agentpluginhub.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.marketplace.model.Marketplace;
import com.agentpluginhub.marketplace.model.NpmSource;
import com.agentpluginhub.marketplace.model.PluginRef;
import com.agentpluginhub.registry.TarballManifestReader;
import com.agentpluginhub.registry.TarballTestSupport;
import com.agentpluginhub.storage.ArtifactStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketplaceServiceTest {

    @Test
    void should_render_npm_source_and_skip_incomplete_plugin() throws Exception {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        PluginEntry good = new PluginEntry("@demo/good", "good", "g",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "good-1.0.0.tgz")));
        // 坏插件:没有 latest dist-tag,必须被跳过
        PluginEntry bad = new PluginEntry("@demo/bad", "bad", "b", Map.of(), List.of());
        when(catalog.all()).thenReturn(List.of(good, bad));
        when(store.load("good-1.0.0.tgz")).thenReturn(TarballTestSupport.tgzWithPackageJson(
                "{\"name\":\"@demo/good\",\"version\":\"1.0.0\"}"));

        Marketplace m = new MarketplaceService(catalog, store, new TarballManifestReader(new ObjectMapper()))
                .render("http://localhost:8080");

        assertThat(m.name()).isEqualTo("agent-plugin-hub");
        assertThat(m.plugins()).extracting(PluginRef::name).containsExactly("good");

        NpmSource src = m.plugins().get(0).source();
        assertThat(src.source()).isEqualTo("npm");
        assertThat(src.packageName()).isEqualTo("@demo/good");
        assertThat(src.version()).isEqualTo("1.0.0");
        assertThat(src.registry()).isEqualTo("http://localhost:8080/registry");
    }

    @Test
    void should_skip_plugin_with_external_dependencies() throws Exception {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        byte[] tgz = TarballTestSupport.tgzWithPackageJson(
                "{\"name\":\"@demo/dep\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"^1.3.0\"}}");
        PluginEntry dep = new PluginEntry("@demo/dep", "dep", "d",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "demo-dep-1.0.0.tgz")));
        when(catalog.all()).thenReturn(List.of(dep));
        when(store.load("demo-dep-1.0.0.tgz")).thenReturn(tgz);

        Marketplace m = new MarketplaceService(catalog, store, new TarballManifestReader(new ObjectMapper()))
                .render("http://h");

        assertThat(m.plugins()).isEmpty(); // 带外部依赖 → 不广告
    }

    @Test
    void should_advertise_plugin_whose_dependencies_are_all_bundled() throws Exception {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        byte[] tgz = TarballTestSupport.tgzWithPackageJson(
                "{\"name\":\"@demo/bundled\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"^1.3.0\"},\"bundleDependencies\":[\"left-pad\"]}");
        PluginEntry bundled = new PluginEntry("@demo/bundled", "bundled", "b",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "demo-bundled-1.0.0.tgz")));
        when(catalog.all()).thenReturn(List.of(bundled));
        when(store.load("demo-bundled-1.0.0.tgz")).thenReturn(tgz);

        Marketplace m = new MarketplaceService(catalog, store, new TarballManifestReader(new ObjectMapper()))
                .render("http://h");

        assertThat(m.plugins()).extracting(PluginRef::name).containsExactly("bundled"); // 依赖已 bundle → 广告
    }

    @Test
    void should_advertise_plugin_with_bundle_dependencies_true() throws Exception {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        byte[] tgz = TarballTestSupport.tgzWithPackageJson(
                "{\"name\":\"@demo/boolbundle\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"^1.3.0\"},\"bundleDependencies\":true}");
        PluginEntry p = new PluginEntry("@demo/boolbundle", "boolbundle", "b",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "demo-boolbundle-1.0.0.tgz")));
        when(catalog.all()).thenReturn(List.of(p));
        when(store.load("demo-boolbundle-1.0.0.tgz")).thenReturn(tgz);

        Marketplace m = new MarketplaceService(catalog, store, new TarballManifestReader(new ObjectMapper()))
                .render("http://h");

        assertThat(m.plugins()).extracting(PluginRef::name).containsExactly("boolbundle");
    }

    @Test
    void should_skip_plugin_with_non_optional_peer_dependency() throws Exception {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        byte[] tgz = TarballTestSupport.tgzWithPackageJson(
                "{\"name\":\"@demo/peer\",\"version\":\"1.0.0\",\"peerDependencies\":{\"react\":\"^18\"}}");
        PluginEntry p = new PluginEntry("@demo/peer", "peer", "p",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "demo-peer-1.0.0.tgz")));
        when(catalog.all()).thenReturn(List.of(p));
        when(store.load("demo-peer-1.0.0.tgz")).thenReturn(tgz);

        Marketplace m = new MarketplaceService(catalog, store, new TarballManifestReader(new ObjectMapper()))
                .render("http://h");

        assertThat(m.plugins()).isEmpty(); // 非可选 peer → 不广告
    }

    @Test
    void should_advertise_plugin_with_optional_peer_dependency() throws Exception {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        byte[] tgz = TarballTestSupport.tgzWithPackageJson(
                "{\"name\":\"@demo/optpeer\",\"version\":\"1.0.0\",\"peerDependencies\":{\"react\":\"^18\"},\"peerDependenciesMeta\":{\"react\":{\"optional\":true}}}");
        PluginEntry p = new PluginEntry("@demo/optpeer", "optpeer", "o",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "demo-optpeer-1.0.0.tgz")));
        when(catalog.all()).thenReturn(List.of(p));
        when(store.load("demo-optpeer-1.0.0.tgz")).thenReturn(tgz);

        Marketplace m = new MarketplaceService(catalog, store, new TarballManifestReader(new ObjectMapper()))
                .render("http://h");

        assertThat(m.plugins()).extracting(PluginRef::name).containsExactly("optpeer"); // 可选 peer → 广告
    }
}
