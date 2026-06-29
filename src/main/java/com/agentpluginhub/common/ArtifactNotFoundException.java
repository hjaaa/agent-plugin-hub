package com.agentpluginhub.common;

// 产物(tarball)不存在或文件名非法
public class ArtifactNotFoundException extends RuntimeException {

    public ArtifactNotFoundException(String filename) {
        super("artifact not found: " + filename);
    }
}
