package com.agentpluginhub.marketplace.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// CC marketplace 条目的 source 对象(npm 类型,指向自建 registry)
public record NpmSource(
        String source,
        @JsonProperty("package") String packageName,
        String version,
        String registry) {
}
