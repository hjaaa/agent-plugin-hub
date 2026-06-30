package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("registry_token")
public class RegistryToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("token_hash")
    private String tokenHash;

    private String label;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private Instant createdAt;

    private boolean revoked;

    protected RegistryToken() {
    }

    public RegistryToken(String tokenHash, String label, String createdBy, Instant createdAt) {
        this.tokenHash = tokenHash;
        this.label = label;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.revoked = false;
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public String getLabel() { return label; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean v) { this.revoked = v; }
}
