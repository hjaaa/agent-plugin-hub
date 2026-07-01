package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@TableName("submission")
public class Submission {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Setter
    @TableField("package_name")
    private String packageName;

    @Setter
    private String version;

    @Setter
    @TableField("plugin_name")
    private String pluginName;

    @Setter
    private String description;

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
    private SubmissionState state;

    @Setter
    private String submitter;

    @Setter
    private String reviewer;

    @Setter
    @TableField("review_notes")
    private String reviewNotes;

    @Setter
    @Version
    @TableField("lock_version")
    private long lockVersion;

    @Setter
    @TableField("created_at")
    private Instant createdAt;

    @Setter
    @TableField("updated_at")
    private Instant updatedAt;
}
