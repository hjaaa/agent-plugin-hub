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
    void should_follow_stable_channel_and_skip_plugin_without_stable() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        // latest 与 stable 故意不同:必须广告 stable(1.0.0)而非 latest(1.1.0)
        PluginEntry good = new PluginEntry("@demo/good", "good", "g",
                Map.of("latest", "1.1.0", "stable", "1.0.0"),
                List.of(new VersionEntry("1.1.0", "good-1.1.0.tgz"),
                        new VersionEntry("1.0.0", "good-1.0.0.tgz")));
        // 仅有 latest、无 stable(灰度中)→ 必须跳过
        PluginEntry latestOnly = new PluginEntry("@demo/grey", "grey", "x",
                Map.of("latest", "2.0.0"), List.of(new VersionEntry("2.0.0", "grey-2.0.0.tgz")));
        when(catalog.all()).thenReturn(List.of(good, latestOnly));

        Marketplace m = new MarketplaceService(catalog).render("http://localhost:8080");

        assertThat(m.plugins()).extracting(PluginRef::name).containsExactly("good");
        NpmSource src = m.plugins().get(0).source();
        assertThat(src.version()).isEqualTo("1.0.0");   // 跟 stable,不跟 latest
        assertThat(src.registry()).isEqualTo("http://localhost:8080/registry");
    }
}
