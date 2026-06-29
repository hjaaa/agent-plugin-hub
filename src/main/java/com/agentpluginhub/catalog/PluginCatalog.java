package com.agentpluginhub.catalog;

import com.agentpluginhub.catalog.model.CatalogIndex;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PluginCatalog {

    private static final Logger log = LoggerFactory.getLogger(PluginCatalog.class);

    private final Map<String, PluginEntry> byPackage = new LinkedHashMap<>();
    private final AppProperties props;
    private final ObjectMapper mapper;

    public PluginCatalog(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    // 启动时一次性加载 index.json;M0 手工维护,缺失则目录为空(允许空仓启动)
    @PostConstruct
    public void load() throws IOException {
        byPackage.clear();
        Path index = Path.of(props.getArtifactsDir(), "index.json");
        if (!Files.exists(index)) {
            log.warn("artifacts index.json not found at {}, catalog is empty", index.toAbsolutePath());
            return;
        }
        CatalogIndex idx = mapper.readValue(Files.readAllBytes(index), CatalogIndex.class);
        if (idx.plugins() != null) {
            for (PluginEntry e : idx.plugins()) {
                byPackage.put(e.packageName(), e);
            }
        }
        log.info("loaded {} plugin(s) from {}", byPackage.size(), index.toAbsolutePath());
    }

    public List<PluginEntry> all() {
        return new ArrayList<>(byPackage.values());
    }

    public Optional<PluginEntry> find(String packageName) {
        return Optional.ofNullable(byPackage.get(packageName));
    }

    public PluginEntry require(String packageName) {
        PluginEntry e = byPackage.get(packageName);
        if (e == null) {
            throw new PackageNotFoundException(packageName);
        }
        return e;
    }
}
