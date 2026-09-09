package com.fras.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One account, as the admin screens need it.
 *
 * <p>The timestamps are kept as strings on purpose. The server sends ISO-8601
 * (it sets {@code write-dates-as-timestamps=false}), and the only thing the
 * client does with them is print a date - parsing to {@code Instant} here would
 * buy nothing and would make a change of format on the server a crash rather
 * than a cosmetic problem.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRow {

    private Long id;
    private String email;
    private String fullName;
    private String role;
    private boolean enabled;
    private String createdAt;
    private String lastLoginAt;

    public UserRow() {
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(String lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /** The name if there is one, otherwise the email, which there always is. */
    public String label() {
        return fullName == null || fullName.isBlank() ? email : fullName;
    }

    /** "Active" / "Disabled", for a column that reads as a state. */
    public String getStateLabel() {
        return enabled ? "Active" : "Disabled";
    }

    /** The date part of the ISO timestamp, or "never" - shown as-is, not parsed. */
    public String getLastSeen() {
        if (lastLoginAt == null || lastLoginAt.isBlank()) {
            return "never";
        }
        int splitAt = lastLoginAt.indexOf('T');
        return splitAt > 0 ? lastLoginAt.substring(0, splitAt) : lastLoginAt;
    }
}
