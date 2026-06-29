package com.agentpluginhub.catalog.model;

// 单个已发布版本:版本号 + 对应 tarball 文件名(artifactsDir 下)
public record VersionEntry(String version, String tarball) {
}
