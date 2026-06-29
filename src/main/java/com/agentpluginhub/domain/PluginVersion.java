package com.agentpluginhub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "plugin_version")
public class PluginVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plugin_id", nullable = false)
    private Long pluginId;

    @Column(nullable = false)
    private String version;

    @Column(name = "tarball_ref", nullable = false)
    private String tarballRef;

    @Column(nullable = false)
    private String integrity;

    @Column(nullable = false)
    private String shasum;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String status;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "published_at", nullable = false)
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
