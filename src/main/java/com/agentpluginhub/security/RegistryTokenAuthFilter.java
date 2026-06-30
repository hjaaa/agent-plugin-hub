package com.agentpluginhub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

// 校验 npm 携带的 Authorization: Bearer <token>;无效/缺失 → 401。仅在 registry 链(启用时)生效。
public class RegistryTokenAuthFilter extends OncePerRequestFilter {

    private final RegistryTokenService tokenService;

    public RegistryTokenAuthFilter(RegistryTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        RegistryPrincipal principal = tokenService.validate(token);
        if (principal == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_REGISTRY")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
