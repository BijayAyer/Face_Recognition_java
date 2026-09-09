package com.school.school_management_system.dto;

import com.school.school_management_system.entity.Role;

/**
 * What a successful {@code /auth/login} or {@code /auth/register} returns.
 * {@code expiresInMs} lets the client warn before the session dies instead
 * of discovering it on the next failed request.
 */
public class AuthResponse {

    private String token;
    private String email;
    private Role role;
    private String fullName;
    private long expiresInMs;

    public AuthResponse() {
    }

    public AuthResponse(String token, String email, Role role, String fullName, long expiresInMs) {
        this.token = token;
        this.email = email;
        this.role = role;
        this.fullName = fullName;
        this.expiresInMs = expiresInMs;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }

    public void setExpiresInMs(long expiresInMs) {
        this.expiresInMs = expiresInMs;
    }
}
