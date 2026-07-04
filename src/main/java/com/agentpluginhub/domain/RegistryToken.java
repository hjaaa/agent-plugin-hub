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
@TableName("registry_token")
public class RegistryToken {

    /** 令牌主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 令牌哈希值 */
    @TableField("token_hash")
    private String tokenHash;

    /** 令牌标签 */
    private String label;

    /** 创建人主体 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private Instant createdAt;

    /** 是否已吊销 */
    private boolean revoked;
}
