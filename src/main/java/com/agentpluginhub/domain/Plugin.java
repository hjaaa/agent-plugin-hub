package com.agentpluginhub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plugin")
public class Plugin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_name", nullable = false, unique = true)
    private String packageName;

    @Column(name = "plugin_name", nullable = false)
    private String pluginName;

    private String description;

    @Column(name = "owner_team")
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
