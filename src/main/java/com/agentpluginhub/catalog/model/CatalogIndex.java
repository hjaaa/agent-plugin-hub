package com.agentpluginhub.catalog.model;

import java.util.List;

// index.json 根结构
public record CatalogIndex(List<PluginEntry> plugins) {
}
