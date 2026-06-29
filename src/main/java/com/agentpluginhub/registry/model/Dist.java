package com.agentpluginhub.registry.model;

// npm packument 的 dist 块:下载地址 + 完整性校验
public record Dist(String tarball, String integrity, String shasum) {
}
