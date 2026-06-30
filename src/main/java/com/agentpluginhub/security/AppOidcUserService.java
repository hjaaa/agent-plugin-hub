package com.agentpluginhub.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// 委托默认 OidcUserService 取标准身份,再用本地角色重建 OidcUser(权限带 ROLE_ 前缀)。
@Service
public class AppOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final LocalUserService localUsers;

    public AppOidcUserService(LocalUserService localUsers) {
        this.localUsers = localUsers;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        List<String> roles = localUsers.upsertAndLoadRoles(oidcUser.getSubject(), oidcUser.getEmail());
        Collection<GrantedAuthority> authorities = roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        String nameAttrKey = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
        return StringUtils.hasText(nameAttrKey)
                ? new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), nameAttrKey)
                : new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
