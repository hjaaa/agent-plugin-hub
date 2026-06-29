package com.agentpluginhub.security;

import com.agentpluginhub.domain.RegistryToken;
import com.agentpluginhub.domain.RegistryTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 只读 registry token:签发返明文(仅一次)、库存 sha256 hash、校验、吊销。
@Service
public class RegistryTokenService {

    private final RegistryTokenRepository repo;
    private final SecureRandom random = new SecureRandom();

    public RegistryTokenService(RegistryTokenRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public String issue(String label, String createdBy) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        repo.save(new RegistryToken(sha256(token), label, createdBy, Instant.now()));
        return token;
    }

    public RegistryPrincipal validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return repo.findByTokenHashAndRevokedFalse(sha256(token))
                .map(t -> new RegistryPrincipal(t.getLabel()))
                .orElse(null);
    }

    @Transactional
    public void revoke(Long id) {
        repo.findById(id).ifPresent(t -> {
            t.setRevoked(true);
            repo.save(t);
        });
    }

    // 测试/管理辅助:按明文 token 反查 id
    public Long findIdByToken(String token) {
        return repo.findByTokenHashAndRevokedFalse(sha256(token))
                .map(RegistryToken::getId).orElse(null);
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("missing SHA-256", e);
        }
    }
}
