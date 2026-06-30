package com.agentpluginhub.versions;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

// 某 plugin 全部 PUBLISHED 版本 + 各 dist-tag 现状;package 是 Java 关键字,故字段名用 packageName
public record PluginVersionsView(
        @JsonProperty("package") String packageName,
        Map<String, String> distTags,
        List<VersionDetail> versions) {
}
