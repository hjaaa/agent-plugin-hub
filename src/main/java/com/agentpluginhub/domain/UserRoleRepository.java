package com.agentpluginhub.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);
    boolean existsByUserIdAndRole(Long userId, String role);
}
