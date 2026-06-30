package com.agentpluginhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String subject;

    private String email;

    @TableField("created_at")
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String subject, String email, Instant createdAt) {
        this.subject = subject;
        this.email = email;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public Instant getCreatedAt() { return createdAt; }
}
