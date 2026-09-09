package com.school.school_management_system.dto;

import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.User;

import java.time.Instant;

/**
 * Read-only view of an account. Never carries the password hash, which is
 * why the entity is not returned directly from the admin endpoints.
 */
public class UserSummary {

    private Long id;
    private String email;
    private String fullName;
    private Role role;
    private boolean enabled;
    private Instant createdAt;
    private Instant lastLoginAt;

    public UserSummary() {
    }

    public static UserSummary of(User user) {
        UserSummary summary = new UserSummary();
        summary.id = user.getId();
        summary.email = user.getEmail();
        summary.fullName = user.getDisplayName();
        summary.role = user.getRole();
        summary.enabled = user.isEnabled();
        summary.createdAt = user.getCreatedAt();
        summary.lastLoginAt = user.getLastLoginAt();
        return summary;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
