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
import java.time.Instant;

@Entity
@Table(name = "registry_token")
@TableName("registry_token")
public class RegistryToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true)
    @TableField("token_hash")
    private String tokenHash;

    @Column(nullable = false)
    private String label;

    @Column(name = "created_by", nullable = false)
    @TableField("created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    @TableField("created_at")
    private Instant createdAt;

    @Column(nullable = false)
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
