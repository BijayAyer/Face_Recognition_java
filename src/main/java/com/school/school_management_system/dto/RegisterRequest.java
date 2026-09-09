package com.school.school_management_system.dto;

import com.school.school_management_system.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public sign-up payload.
 *
 * <p>{@code role} is a <em>request</em>, not a grant. The server creates a
 * STUDENT account unless {@code registrationCode} matches
 * {@code app.security.privileged-registration-code}. Previously the role was
 * taken at face value, which meant anyone who could reach the endpoint could
 * mint themselves an administrator.
 */
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 180, message = "Email must be at most 180 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @Size(max = 120, message = "Name must be at most 120 characters")
    private String fullName;

    /** Ignored unless a valid registrationCode is supplied. */
    private Role role;

    private String registrationCode;

    public RegisterRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getRegistrationCode() {
        return registrationCode;
    }

    public void setRegistrationCode(String registrationCode) {
        this.registrationCode = registrationCode;
    }
}
