package com.school.school_management_system.controller;

import com.school.school_management_system.dto.AuthResponse;
import com.school.school_management_system.dto.ChangePasswordRequest;
import com.school.school_management_system.dto.CreateUserRequest;
import com.school.school_management_system.dto.LoginRequest;
import com.school.school_management_system.dto.RegisterRequest;
import com.school.school_management_system.dto.UserSummary;
import com.school.school_management_system.entity.Role;
import com.school.school_management_system.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.school.school_management_system.security.CustomUserDetails;

import java.util.List;

/**
 * Authentication and account endpoints.
 *
 * <pre>
 *   POST /auth/register          public   - creates a STUDENT, or refuses
 *   POST /auth/login             public
 *   GET  /auth/me                any signed-in account
 *   POST /auth/change-password   any signed-in account
 *   GET  /auth/users             ADMIN
 *   POST /auth/users             ADMIN    - create an account with any role
 *   POST /auth/users/{id}/enabled ADMIN
 *   POST /auth/users/{id}/role   ADMIN    - promote or demote an account
 * </pre>
 *
 * <p>Sign-up asking for TEACHER or ADMIN is answered with 403 unless it
 * carries the privileged-registration code. It is not downgraded to a student
 * account, which is what it used to do: the person got a login that worked and
 * a role they had not asked for, and nothing on screen said so.
 *
 * <p>The role endpoint exists because refusing a privileged sign-up is only
 * half an answer: somebody has to be able to say yes. Before it, promoting a
 * new sign-up to teacher meant an UPDATE statement against the database.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Who am I? The desktop client calls this on start-up to find out
     * whether a token it still holds is usable, instead of discovering it
     * from a failed data request.
     */
    @GetMapping("/me")
    public ResponseEntity<UserSummary> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(authService.currentUser(principal.getUsername()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSummary> listUsers() {
        return authService.listUsers();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUser(request));
    }

    @PostMapping("/users/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> setEnabled(@PathVariable Long id,
                                                  @RequestParam boolean enabled) {
        return ResponseEntity.ok(authService.setEnabled(id, enabled));
    }

    /**
     * Promote or demote an account. {@code role} is bound as the enum, so an
     * unknown value is a 400 from {@code MethodArgumentTypeMismatchException}
     * rather than a silent no-op.
     */
    @PostMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> setRole(@PathVariable Long id,
                                               @RequestParam Role role) {
        return ResponseEntity.ok(authService.setRole(id, role));
    }
}
