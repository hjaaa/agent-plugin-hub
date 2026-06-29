package com.agentpluginhub.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

// npm 包文档(packument):GET <registry>/<package> 的响应体
public record Packument(
        @JsonProperty("_id") String id,
        String name,
        @JsonProperty("dist-tags") Map<String, String> distTags,
        Map<String, PackumentVersion> versions) {
}
