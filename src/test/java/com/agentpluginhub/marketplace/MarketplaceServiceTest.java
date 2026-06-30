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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketplaceServiceTest {

    @Test
    void should_render_npm_source_and_skip_incomplete_plugin() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        PluginEntry good = new PluginEntry("@demo/good", "good", "g",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "good-1.0.0.tgz")));
        // 坏插件:没有 latest dist-tag,必须被跳过
        PluginEntry bad = new PluginEntry("@demo/bad", "bad", "b", Map.of(), List.of());
        when(catalog.all()).thenReturn(List.of(good, bad));

        Marketplace m = new MarketplaceService(catalog).render("http://localhost:8080");

        assertThat(m.name()).isEqualTo("agent-plugin-hub");
        assertThat(m.plugins()).extracting(PluginRef::name).containsExactly("good");

        NpmSource src = m.plugins().get(0).source();
        assertThat(src.source()).isEqualTo("npm");
        assertThat(src.packageName()).isEqualTo("@demo/good");
        assertThat(src.version()).isEqualTo("1.0.0");
        assertThat(src.registry()).isEqualTo("http://localhost:8080/registry");
    }
}
