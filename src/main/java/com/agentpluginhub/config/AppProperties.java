package com.agentpluginhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // 本地制品目录;存放 index.json 与各版本 *.tgz
    private String artifactsDir = "./artifacts";

    public String getArtifactsDir() {
        return artifactsDir;
    }

    public void setArtifactsDir(String artifactsDir) {
        this.artifactsDir = artifactsDir;
    }
}
