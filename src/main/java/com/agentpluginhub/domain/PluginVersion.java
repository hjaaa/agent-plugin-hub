package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@TableName("plugin_version")
public class PluginVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Setter
    @TableField("plugin_id")
    private Long pluginId;

    @Setter
    private String version;

    @Setter
    @TableField("tarball_ref")
    private String tarballRef;

    @Setter
    private String integrity;

    @Setter
    private String shasum;

    @Setter
    @TableField("size_bytes")
    private long sizeBytes;

    @Setter
    private String status;

    @Setter
    @TableField("uploaded_by")
    private String uploadedBy;

    @Setter
    @TableField("published_at")
    private Instant publishedAt;
}
