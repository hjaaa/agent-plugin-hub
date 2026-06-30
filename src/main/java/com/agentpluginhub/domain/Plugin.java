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

@Entity
@Table(name = "plugin")
@TableName("plugin")
public class Plugin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "package_name", nullable = false, unique = true)
    @TableField("package_name")
    private String packageName;

    @Column(name = "plugin_name", nullable = false)
    @TableField("plugin_name")
    private String pluginName;

    private String description;

    @Column(name = "owner_team")
    @TableField("owner_team")
    private String ownerTeam;

    protected Plugin() {
    }

    public Plugin(String packageName, String pluginName, String description, String ownerTeam) {
        this.packageName = packageName;
        this.pluginName = pluginName;
        this.description = description;
        this.ownerTeam = ownerTeam;
    }

    public Long getId() { return id; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String v) { this.packageName = v; }
    public String getPluginName() { return pluginName; }
    public void setPluginName(String v) { this.pluginName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getOwnerTeam() { return ownerTeam; }
    public void setOwnerTeam(String v) { this.ownerTeam = v; }
}
