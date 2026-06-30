package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("plugin_version")
public class PluginVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plugin_id")
    private Long pluginId;

    private String version;

    @TableField("tarball_ref")
    private String tarballRef;

    private String integrity;

    private String shasum;

    @TableField("size_bytes")
    private long sizeBytes;

    private String status;

    @TableField("uploaded_by")
    private String uploadedBy;

    @TableField("published_at")
    private Instant publishedAt;

    public Long getId() { return id; }
    public Long getPluginId() { return pluginId; }
    public void setPluginId(Long v) { this.pluginId = v; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public String getTarballRef() { return tarballRef; }
    public void setTarballRef(String v) { this.tarballRef = v; }
    public String getIntegrity() { return integrity; }
    public void setIntegrity(String v) { this.integrity = v; }
    public String getShasum() { return shasum; }
    public void setShasum(String v) { this.shasum = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String v) { this.uploadedBy = v; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant v) { this.publishedAt = v; }
}
