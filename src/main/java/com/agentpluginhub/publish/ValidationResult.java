package com.agentpluginhub.publish;

// 校验通过后提炼出的发布元信息
public record ValidationResult(String packageName, String version, String pluginName, String description) {
}
