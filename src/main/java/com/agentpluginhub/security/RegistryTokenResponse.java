package com.agentpluginhub.security;

// 明文 token 仅在签发时返回一次
public record RegistryTokenResponse(String token, String label) {
}
