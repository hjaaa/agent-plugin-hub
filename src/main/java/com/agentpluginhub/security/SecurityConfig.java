package com.agentpluginhub.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, RegistryAuthProperties.class})
public class SecurityConfig {

    // npm 客户端在 URL 路径中使用 %2F 编码作用域斜杠(如 @demo%2Fpkg);
    // Spring Security 默认的 StrictHttpFirewall 会拒绝此类 URL,需显式放行。
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return web -> web.httpFirewall(firewall);
    }

    // 机器平面:仅当 app.registry.auth.enabled=true 时启用;stateless + bearer token。
    // 关闭时本 bean 不存在,/registry/** 落到 webFilterChain 的 permitAll。
    @Bean
    @org.springframework.core.annotation.Order(1)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "app.registry.auth.enabled", havingValue = "true")
    public SecurityFilterChain registryFilterChain(
            HttpSecurity http, RegistryTokenService tokenService) throws Exception {
        http
                .securityMatcher("/registry/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new RegistryTokenAuthFilter(tokenService),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Web 平面:对 CC 与运维放行的公共端点 + OIDC 登录保护的 /api/**
    // /registry/** 在 auth.enabled=false 时由此链 permitAll 放行;
    // 启用后由 @Order(1) 的 registryFilterChain 优先匹配,本链不再处理 /registry/**。
    @Bean
    public SecurityFilterChain webFilterChain(
            HttpSecurity http,
            OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/marketplace.json", "/registry/**",
                                "/actuator/**", "/login/**", "/oauth2/**", "/error",
                                "/healthz")
                        .permitAll()
                        .anyRequest().authenticated())
                // /api/** 是 API 端点(CLI/脚本调用,非浏览器表单),豁免 CSRF——否则 OIDC 会话登录的
                // AUTHOR/ADMIN 因拿不到 CSRF token 而对所有状态变更调用 403。授权仍由方法级 @PreAuthorize
                // (ROLE)把守;session cookie 默认 SameSite=Lax,跨站风险有限。
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)));
        return http.build();
    }
}
