package com.agentpluginhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {

    private String endpoint;     // MinIO endpoint,如 http://localhost:9000
    private String accessKey;
    private String secretKey;
    private String bucket = "agent-plugin-hub";
    private String region = "us-east-1";
    // 仅 dev/MinIO/测试自动建桶;生产(最小权限)设 false,直查已存在的配置 bucket,
    // 避免无 ListBuckets/CreateBucket 权限时启动期失败
    private boolean autoCreateBucket = true;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String v) { this.endpoint = v; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String v) { this.accessKey = v; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String v) { this.secretKey = v; }
    public String getBucket() { return bucket; }
    public void setBucket(String v) { this.bucket = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
    public boolean isAutoCreateBucket() { return autoCreateBucket; }
    public void setAutoCreateBucket(boolean v) { this.autoCreateBucket = v; }
}
