package com.agentpluginhub.marketplace.model;

import java.util.List;
import java.util.Map;

// CC 远程市场清单:GET /marketplace.json 的响应体
public record Marketplace(String name, Map<String, Object> owner, List<PluginRef> plugins) {
}
