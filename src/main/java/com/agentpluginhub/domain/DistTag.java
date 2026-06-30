package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "dist_tag")
@TableName("dist_tag")
public class DistTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "plugin_id", nullable = false)
    @TableField("plugin_id")
    private Long pluginId;

    @Column(nullable = false)
    private String tag;

    @Column(nullable = false)
    private String version;

    // 移动指针的 admin subject 与移动时刻;M1 既有 latest 行为 NULL(不回填)
    @Column(name = "updated_by")
    @TableField("updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    @TableField("updated_at")
    private Instant updatedAt;

    protected DistTag() {
    }

    public DistTag(Long pluginId, String tag, String version) {
        this.pluginId = pluginId;
        this.tag = tag;
        this.version = version;
    }

    public DistTag(Long pluginId, String tag, String version, String updatedBy, Instant updatedAt) {
        this.pluginId = pluginId;
        this.tag = tag;
        this.version = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    // 移动指针:更新版本并记审计
    public void apply(String version, String updatedBy, Instant updatedAt) {
        this.version = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getPluginId() { return pluginId; }
    public String getTag() { return tag; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
