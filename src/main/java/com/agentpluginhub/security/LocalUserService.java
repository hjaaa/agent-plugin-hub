package com.agentpluginhub.security;

import com.agentpluginhub.domain.AppUser;
import com.agentpluginhub.domain.AppUserRepository;
import com.agentpluginhub.domain.UserRole;
import com.agentpluginhub.domain.UserRoleRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// OIDC 只认证;此服务负责本地用户 upsert 与本地角色读取/引导。
@Service
public class LocalUserService {

    private static final String AUTHOR = "AUTHOR";
    private static final String ADMIN = "ADMIN";

    private final AppUserRepository users;
    private final UserRoleRepository roles;
    private final SecurityProperties props;

    public LocalUserService(AppUserRepository users, UserRoleRepository roles, SecurityProperties props) {
        this.users = users;
        this.roles = roles;
        this.props = props;
    }

    @Transactional
    public List<String> upsertAndLoadRoles(String subject, String email) {
        AppUser user = users.findBySubject(subject)
                .orElseGet(() -> users.save(new AppUser(subject, email, Instant.now())));
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            users.save(user);
        }
        // 新用户默认 AUTHOR
        if (!roles.existsByUserIdAndRole(user.getId(), AUTHOR)) {
            roles.save(new UserRole(user.getId(), AUTHOR));
        }
        // 引导初始 admin
        String bootstrapSubject = props.getBootstrapAdminSubject();
        if (StringUtils.hasText(bootstrapSubject) && bootstrapSubject.equals(subject)
                && !roles.existsByUserIdAndRole(user.getId(), ADMIN)) {
            roles.save(new UserRole(user.getId(), ADMIN));
        }
        return roles.findByUserId(user.getId()).stream().map(UserRole::getRole).toList();
    }
}
