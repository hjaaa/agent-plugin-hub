package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("submission")
public class Submission {

    /** 提交单主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** NPM 包名 */
    @TableField("package_name")
    private String packageName;

    /** 提交版本号 */
    private String version;

    /** 插件名称 */
    @TableField("plugin_name")
    private String pluginName;

    /** 插件描述 */
    private String description;

    /** 待审核产物存储引用 */
    @TableField("tarball_ref")
    private String tarballRef;

    /** NPM integrity 校验值 */
    private String integrity;

    /** 产物 SHA1 摘要 */
    private String shasum;

    /** 产物字节大小 */
    @TableField("size_bytes")
    private long sizeBytes;

    /** 审核状态 */
    private SubmissionState state;

    /** 提交人主体 */
    private String submitter;

    /** 审核人主体 */
    private String reviewer;

    /** 审核备注 */
    @TableField("review_notes")
    private String reviewNotes;

    /** 乐观锁版本 */
    @Version
    @TableField("lock_version")
    private long lockVersion;

    /** 创建时间 */
    @TableField("created_at")
    private Instant createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Instant updatedAt;
}
