package com.agentpluginhub.registry.model;

// packument.versions 里的单版本对象;M0 只放 npm 解析必需字段
public record PackumentVersion(String name, String version, Dist dist) {
}
