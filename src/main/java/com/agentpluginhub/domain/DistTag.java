package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("dist_tag")
public class DistTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plugin_id")
    private Long pluginId;

    private String tag;

    private String version;

    // 移动指针的 admin subject 与移动时刻;M1 既有 latest 行为 NULL(不回填)
    @TableField("updated_by")
    private String updatedBy;

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
