package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("user_role")
public class UserRole {

    /** 用户角色主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 角色名称 */
    private String role;
}
