package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

@TableName("submission")
public class Submission {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("package_name")
    private String packageName;

    private String version;

    @TableField("plugin_name")
    private String pluginName;

    private String description;

    @TableField("tarball_ref")
    private String tarballRef;

    private String integrity;

    private String shasum;

    @TableField("size_bytes")
    private long sizeBytes;

    private SubmissionState state;

    private String submitter;

    private String reviewer;

    @TableField("review_notes")
    private String reviewNotes;

    @Version
    @TableField("lock_version")
    private long lockVersion;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public Submission() {
    }

    public Long getId() { return id; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String v) { this.packageName = v; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public String getPluginName() { return pluginName; }
    public void setPluginName(String v) { this.pluginName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getTarballRef() { return tarballRef; }
    public void setTarballRef(String v) { this.tarballRef = v; }
    public String getIntegrity() { return integrity; }
    public void setIntegrity(String v) { this.integrity = v; }
    public String getShasum() { return shasum; }
    public void setShasum(String v) { this.shasum = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public SubmissionState getState() { return state; }
    public void setState(SubmissionState v) { this.state = v; }
    public String getSubmitter() { return submitter; }
    public void setSubmitter(String v) { this.submitter = v; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String v) { this.reviewer = v; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String v) { this.reviewNotes = v; }
    public long getLockVersion() { return lockVersion; }
    public void setLockVersion(long v) { this.lockVersion = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
