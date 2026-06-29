package com.agentpluginhub.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PluginCatalogTest {

    private PluginCatalog catalogOn(String dir) throws Exception {
        AppProperties props = new AppProperties();
        props.setArtifactsDir(dir);
        PluginCatalog catalog = new PluginCatalog(props, new ObjectMapper());
        catalog.load();
        return catalog;
    }

    @Test
    void should_load_plugins_when_index_present() throws Exception {
        PluginCatalog catalog = catalogOn("src/test/resources/fixtures/artifacts");
        assertThat(catalog.all()).hasSize(1);

        PluginEntry e = catalog.require("@demo/hello-plugin");
        assertThat(e.pluginName()).isEqualTo("hello-plugin");
        assertThat(e.distTags()).containsEntry("latest", "1.0.0");
        assertThat(e.versions()).extracting(VersionEntry::version).containsExactly("1.0.0");
        assertThat(e.versions()).extracting(VersionEntry::tarball)
                .containsExactly("demo-hello-plugin-1.0.0.tgz");
    }

    @Test
    void should_throw_when_package_unknown() throws Exception {
        PluginCatalog catalog = catalogOn("src/test/resources/fixtures/artifacts");
        assertThatThrownBy(() -> catalog.require("@x/none"))
                .isInstanceOf(PackageNotFoundException.class);
    }

    @Test
    void should_be_empty_when_index_missing() throws Exception {
        PluginCatalog catalog = catalogOn("src/test/resources/fixtures/no-such-dir");
        assertThat(catalog.all()).isEmpty();
    }
}
