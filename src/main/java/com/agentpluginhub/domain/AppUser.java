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
@TableName("app_user")
public class AppUser {

    /** 用户主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 外部身份主体 */
    private String subject;

    /** 用户邮箱 */
    private String email;

    /** 创建时间 */
    @TableField("created_at")
    private Instant createdAt;
}
