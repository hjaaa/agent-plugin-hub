package com.agentpluginhub.marketplace.model;

// marketplace.json 的 plugins[] 单条
public record PluginRef(String name, String description, NpmSource source) {
}
