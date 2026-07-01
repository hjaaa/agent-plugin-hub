package com.agentpluginhub.security;

import com.agentpluginhub.domain.AppUser;
import com.agentpluginhub.domain.UserRole;
import com.agentpluginhub.mapper.AppUserMapper;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    private final AppUserMapper users;
    private final UserRoleMapper roles;
    private final SecurityProperties props;

    public LocalUserService(AppUserMapper users, UserRoleMapper roles, SecurityProperties props) {
        this.users = users;
        this.roles = roles;
        this.props = props;
    }

    @Transactional
    public List<String> upsertAndLoadRoles(String subject, String email) {
        AppUser user = MapperQueries.one(users, Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getSubject, subject))
                .orElseGet(() -> {
                    AppUser created = new AppUser(subject, email, Instant.now());
                    users.insert(created);
                    return created;
                });
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            users.updateById(user);
        }
        // 新用户默认 AUTHOR
        if (!MapperQueries.exists(roles, Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, user.getId())
                .eq(UserRole::getRole, AUTHOR))) {
            roles.insert(new UserRole(user.getId(), AUTHOR));
        }
        // 引导初始 admin
        String bootstrapSubject = props.getBootstrapAdminSubject();
        if (StringUtils.hasText(bootstrapSubject) && bootstrapSubject.equals(subject)
                && !MapperQueries.exists(roles, Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getUserId, user.getId())
                        .eq(UserRole::getRole, ADMIN))) {
            roles.insert(new UserRole(user.getId(), ADMIN));
        }
        return roles.selectList(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, user.getId()))
                .stream()
                .map(UserRole::getRole)
                .toList();
    }
}
