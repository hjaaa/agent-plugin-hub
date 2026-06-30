package com.agentpluginhub.security;

import com.agentpluginhub.domain.RegistryToken;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.RegistryTokenMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    private final RegistryTokenMapper repo;
    private final SecureRandom random = new SecureRandom();

    public RegistryTokenService(RegistryTokenMapper repo) {
        this.repo = repo;
    }

    @Transactional
    public String issue(String label, String createdBy) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        repo.insert(new RegistryToken(sha256(token), label, createdBy, Instant.now()));
        return token;
    }

    public RegistryPrincipal validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return MapperQueries.one(repo, Wrappers.<RegistryToken>lambdaQuery()
                        .eq(RegistryToken::getTokenHash, sha256(token))
                        .eq(RegistryToken::isRevoked, false))
                .map(t -> new RegistryPrincipal(t.getLabel()))
                .orElse(null);
    }

    @Transactional
    public void revoke(Long id) {
        RegistryToken token = repo.selectById(id);
        if (token != null) {
            token.setRevoked(true);
            repo.updateById(token);
        }
    }

    // 测试/管理辅助:按明文 token 反查 id
    public Long findIdByToken(String token) {
        return MapperQueries.one(repo, Wrappers.<RegistryToken>lambdaQuery()
                        .eq(RegistryToken::getTokenHash, sha256(token))
                        .eq(RegistryToken::isRevoked, false))
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
