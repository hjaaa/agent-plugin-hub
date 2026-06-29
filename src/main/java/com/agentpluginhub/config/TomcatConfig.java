package com.agentpluginhub.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    // npm 把 scoped 包名里的 "/" 编码为 "%2F";Tomcat 默认拒绝路径中的编码斜杠。
    // 设为 decode,使 %2F 被解码为 "/",请求能进入 RegistryController。
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashCustomizer() {
        return factory -> factory.addConnectorCustomizers(
                connector -> connector.setEncodedSolidusHandling("decode"));
    }
}
