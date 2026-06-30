package com.agentpluginhub.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.registry.TarballTestSupport;
import com.agentpluginhub.support.AbstractIntegrationTest;
import com.agentpluginhub.support.TestDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PluginCatalogTest extends AbstractIntegrationTest {

    @Autowired PluginCatalog catalog;
    @Autowired TestDataSeeder seeder;

    @Test
    void should_expose_published_plugin_from_db() throws Exception {
        byte[] tgz = TarballTestSupport.tgzWithPackageJson(
                "{\"name\":\"@demo/hello-plugin\",\"version\":\"1.0.0\"}");
        seeder.publish("@demo/hello-plugin", "hello-plugin", "1.0.0",
                "demo-hello-plugin-1.0.0.tgz", tgz);

        PluginEntry e = catalog.require("@demo/hello-plugin");
        assertThat(e.pluginName()).isEqualTo("hello-plugin");
        assertThat(e.distTags()).containsEntry("latest", "1.0.0");
        assertThat(e.versions()).extracting(VersionEntry::version).containsExactly("1.0.0");
        assertThat(e.versions()).extracting(VersionEntry::tarball)
                .containsExactly("demo-hello-plugin-1.0.0.tgz");
    }

    @Test
    void should_throw_when_package_unknown() {
        assertThatThrownBy(() -> catalog.require("@x/none"))
                .isInstanceOf(PackageNotFoundException.class);
    }
}
