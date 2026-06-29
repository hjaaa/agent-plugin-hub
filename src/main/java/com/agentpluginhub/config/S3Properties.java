package com.agentpluginhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {

    private String endpoint;     // MinIO endpoint,如 http://localhost:9000
    private String accessKey;
    private String secretKey;
    private String bucket = "agent-plugin-hub";
    private String region = "us-east-1";

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
}
