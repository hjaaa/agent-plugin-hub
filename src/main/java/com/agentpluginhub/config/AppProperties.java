package com.agentpluginhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // 本地制品目录;存放 index.json 与各版本 *.tgz
    private String artifactsDir = "./artifacts";

    // 制品存储后端:s3(默认,连 MinIO/S3)或 local(本地文件)
    private String storageType = "s3";

    public String getArtifactsDir() {
        return artifactsDir;
    }

    public void setArtifactsDir(String artifactsDir) {
        this.artifactsDir = artifactsDir;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }
}
