package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Setter
    private boolean revoked;

    public RegistryToken(String tokenHash, String label, String createdBy, Instant createdAt) {
        this.tokenHash = tokenHash;
        this.label = label;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.revoked = false;
    }
}
