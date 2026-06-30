package com.agentpluginhub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_role")
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String role;

    protected UserRole() {
    }

    public UserRole(Long userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRole() { return role; }
}
