package com.agentpluginhub.versions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// 版本治理读端点。登录可见(无角色要求,由 webFilterChain 的 anyRequest().authenticated() 把守)。
// scoped 包名含 "/",pkg 走末尾 capture-all。
@RestController
public class VersionController {

    private final VersionQueryService service;

    public VersionController(VersionQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/plugin-versions/{*pkg}")
    public PluginVersionsView versions(@PathVariable("pkg") String pkg) {
        String packageName = pkg.startsWith("/") ? pkg.substring(1) : pkg;
        return service.list(packageName);
    }
}
