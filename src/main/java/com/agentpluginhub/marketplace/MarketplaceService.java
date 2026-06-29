package com.agentpluginhub.marketplace;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.marketplace.model.Marketplace;
import com.agentpluginhub.marketplace.model.NpmSource;
import com.agentpluginhub.marketplace.model.PluginRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    private final PluginCatalog catalog;

    public MarketplaceService(PluginCatalog catalog) {
        this.catalog = catalog;
    }

    // 渲染 CC 认的 marketplace.json。铁律(spec §7):坏插件跳过 + 记日志,永远吐合法 JSON。
    // 依赖型插件已在发布期被 Validator 硬拒,故此处无需再判依赖。
    public Marketplace render(String baseUrl) {
        List<PluginRef> refs = new ArrayList<>();
        for (PluginEntry e : catalog.all()) {
            try {
                String latest = e.distTags() == null ? null : e.distTags().get("latest");
                if (latest == null || e.packageName() == null || e.pluginName() == null) {
                    log.warn("skip plugin with incomplete metadata: {}", e.packageName());
                    continue;
                }
                NpmSource src = new NpmSource("npm", e.packageName(), latest, baseUrl + "/registry");
                refs.add(new PluginRef(e.pluginName(), e.description(), src));
            } catch (RuntimeException ex) {
                log.warn("skip bad plugin {}", e.packageName(), ex);
            }
        }
        return new Marketplace("agent-plugin-hub", Map.of("name", "agent-plugin-hub"), refs);
    }
}
