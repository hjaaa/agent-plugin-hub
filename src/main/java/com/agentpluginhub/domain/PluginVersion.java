package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("plugin_version")
public class PluginVersion {

    /** 插件版本主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 插件 ID */
    @TableField("plugin_id")
    private Long pluginId;

    /** 版本号 */
    private String version;

    /** 产物存储引用 */
    @TableField("tarball_ref")
    private String tarballRef;

    /** NPM integrity 校验值 */
    private String integrity;

    /** 产物 SHA1 摘要 */
    private String shasum;

    /** 产物字节大小 */
    @TableField("size_bytes")
    private long sizeBytes;

    /** 版本状态 */
    private String status;

    /** 上传人主体 */
    @TableField("uploaded_by")
    private String uploadedBy;

    /** 发布时间 */
    @TableField("published_at")
    private Instant publishedAt;
}
