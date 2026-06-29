package com.agentpluginhub.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

public record Packument(
        @JsonProperty("_id") String id,
        String name,
        @JsonProperty("dist-tags") Map<String, String> distTags,
        Map<String, ObjectNode> versions) {
}
