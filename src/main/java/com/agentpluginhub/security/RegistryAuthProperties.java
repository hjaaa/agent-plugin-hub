package com.agentpluginhub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.registry.auth")
public class RegistryAuthProperties {

    // 默认关闭:localhost/测试不校验 registry token;非 localhost 部署须置 true
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
