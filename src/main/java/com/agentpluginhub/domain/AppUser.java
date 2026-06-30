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
@Table(name = "app_user")
@TableName("app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private String subject;

    private String email;

    @Column(name = "created_at", nullable = false)
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
