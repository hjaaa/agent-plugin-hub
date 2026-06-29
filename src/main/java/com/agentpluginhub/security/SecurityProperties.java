package com.agentpluginhub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    // 首次登录即授 ADMIN 的 OIDC subject(引导初始管理员);留空则无人自动成为 admin
    private String bootstrapAdminSubject;

    public String getBootstrapAdminSubject() {
        return bootstrapAdminSubject;
    }

    public void setBootstrapAdminSubject(String v) {
        this.bootstrapAdminSubject = v;
    }
}
