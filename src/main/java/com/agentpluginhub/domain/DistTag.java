package com.agentpluginhub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dist_tag")
public class DistTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plugin_id", nullable = false)
    private Long pluginId;

    @Column(nullable = false)
    private String tag;

    @Column(nullable = false)
    private String version;

    protected DistTag() {
    }

    public DistTag(Long pluginId, String tag, String version) {
        this.pluginId = pluginId;
        this.tag = tag;
        this.version = version;
    }

    public Long getId() { return id; }
    public Long getPluginId() { return pluginId; }
    public String getTag() { return tag; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
}
