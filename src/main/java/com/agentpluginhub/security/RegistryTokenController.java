package com.agentpluginhub.security;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registry-tokens")
@PreAuthorize("hasRole('ADMIN')")
public class RegistryTokenController {

    private final RegistryTokenService tokens;

    public RegistryTokenController(RegistryTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    public ResponseEntity<RegistryTokenResponse> issue(
            @RequestBody Map<String, String> body, @AuthenticationPrincipal OidcUser principal) {
        String label = body.getOrDefault("label", "unnamed");
        String createdBy = principal != null ? principal.getSubject() : "system";
        String token = tokens.issue(label, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistryTokenResponse(token, label));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        tokens.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
