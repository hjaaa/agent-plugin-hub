package com.agentpluginhub.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

// 一个插件的跨版本元信息;package 是 npm 包名(Java 关键字,故字段名用 packageName)
public record PluginEntry(
        @JsonProperty("package") String packageName,
        String pluginName,
        String description,
        Map<String, String> distTags,
        List<VersionEntry> versions) {
}
