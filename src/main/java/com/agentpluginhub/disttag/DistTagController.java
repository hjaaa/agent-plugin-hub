package com.agentpluginhub.disttag;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 渠道管理:提升 / 回滚 latest|stable 指针。仅 ADMIN。
// scoped 包名含 "/",故 pkg 走末尾 capture-all({*pkg},值带前导 "/");tag 为干净单段。
@RestController
public class DistTagController {

    private final DistTagService service;

    public DistTagController(DistTagService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/dist-tags/{tag}/plugins/{*pkg}")
    public Map<String, String> set(
            @PathVariable String tag,
            @PathVariable("pkg") String pkg,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal OidcUser principal) {
        String packageName = pkg.startsWith("/") ? pkg.substring(1) : pkg;
        String version = body == null ? null : body.get("version");
        String subject = principal != null ? principal.getSubject() : "admin";
        return service.setDistTag(packageName, tag, version, subject);
    }
}
