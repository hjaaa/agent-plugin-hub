package com.agentpluginhub.common;

// 指定版本不是该 plugin 的 PUBLISHED 版本
public class VersionNotFoundException extends RuntimeException {

    public VersionNotFoundException(String packageName, String version) {
        super("version not published: " + packageName + "@" + version);
    }
}
