package com.agentpluginhub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "submission")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(nullable = false)
    private String version;

    @Column(name = "plugin_name", nullable = false)
    private String pluginName;

    private String description;

    @Column(name = "tarball_ref", nullable = false)
    private String tarballRef;

    @Column(nullable = false)
    private String integrity;

    @Column(nullable = false)
    private String shasum;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionState state;

    @Column(nullable = false)
    private String submitter;

    private String reviewer;

    @Column(name = "review_notes")
    private String reviewNotes;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Submission() {
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
