package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("plugin")
public class Plugin {

    /** 插件主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** NPM 包名 */
    @TableField("package_name")
    private String packageName;

    /** 插件名称 */
    @TableField("plugin_name")
    private String pluginName;

    /** 插件描述 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    /** 所属团队 */
    @TableField("owner_team")
    private String ownerTeam;
}
