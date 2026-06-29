package com.agentpluginhub.common;

// 目录中找不到该 npm 包
public class PackageNotFoundException extends RuntimeException {

    public PackageNotFoundException(String packageName) {
        super("package not found: " + packageName);
    }
}
