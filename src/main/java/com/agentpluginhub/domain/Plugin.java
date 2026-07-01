package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@TableName("plugin")
public class Plugin {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Setter
    @TableField("package_name")
    private String packageName;

    @Setter
    @TableField("plugin_name")
    private String pluginName;

    @Setter
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    @Setter
    @TableField("owner_team")
    private String ownerTeam;

    public Plugin(String packageName, String pluginName, String description, String ownerTeam) {
        this.packageName = packageName;
        this.pluginName = pluginName;
        this.description = description;
        this.ownerTeam = ownerTeam;
    }
}
