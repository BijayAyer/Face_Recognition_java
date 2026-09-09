package com.school.school_management_system.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles password guessing on {@code /auth/login}.
 *
 * <p>Counts consecutive failures per email address and refuses further
 * attempts for a cool-off window once the limit is hit. A successful login
 * clears the counter. State is in-memory, which is the right trade-off for
 * a single-process desktop backend: it costs nothing, needs no schema, and
 * the worst case of a restart is that an attacker gets their budget back.
 *
 * <p>Deliberately keyed on the email rather than the client IP, because
 * every desktop client here connects from localhost.
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockout;
    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.max-login-attempts:5}") int maxAttempts,
            @Value("${app.security.login-lockout-minutes:15}") long lockoutMinutes) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockout = Duration.ofMinutes(Math.max(1, lockoutMinutes));
    }

    public boolean isBlocked(String email) {
        Attempts attempts = attemptsByEmail.get(key(email));
        if (attempts == null) {
            return false;
        }
        if (Instant.now().isAfter(attempts.lastFailure.plus(lockout))) {
            attemptsByEmail.remove(key(email));
            return false;
        }
        return attempts.count >= maxAttempts;
    }

    /** Whole minutes left on the cool-off, at least 1 while still blocked. */
    public long minutesRemaining(String email) {
        Attempts attempts = attemptsByEmail.get(key(email));
        if (attempts == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), attempts.lastFailure.plus(lockout)).getSeconds();
        return seconds <= 0 ? 0 : Math.max(1, seconds / 60);
    }

    public void recordFailure(String email) {
        attemptsByEmail.compute(key(email), (k, existing) -> {
            if (existing == null || Instant.now().isAfter(existing.lastFailure.plus(lockout))) {
                return new Attempts(1, Instant.now());
            }
            return new Attempts(existing.count + 1, Instant.now());
        });
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(key(email));
    }

    /** Attempts left before the lockout kicks in; 0 once blocked. */
    public int remainingAttempts(String email) {
        Attempts attempts = attemptsByEmail.get(key(email));
        int used = attempts == null ? 0 : attempts.count;
        return Math.max(0, maxAttempts - used);
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        private final int count;
        private final Instant lastFailure;

        private Attempts(int count, Instant lastFailure) {
            this.count = count;
            this.lastFailure = lastFailure;
        }
    }
}
