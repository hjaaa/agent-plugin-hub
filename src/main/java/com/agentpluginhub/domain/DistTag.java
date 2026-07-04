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
@TableName("dist_tag")
public class DistTag {

    /** 渠道标签主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 插件 ID */
    @TableField("plugin_id")
    private Long pluginId;

    /** 渠道标签名称 */
    private String tag;

    /** 渠道标签指向版本 */
    private String version;

    /** 更新人主体 */
    @TableField("updated_by")
    private String updatedBy;

    /** 更新时间 */
    @TableField("updated_at")
    private Instant updatedAt;
}
