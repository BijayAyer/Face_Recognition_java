package com.school.school_management_system.security;

import com.school.school_management_system.exception.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns 401 with a JSON body for unauthenticated API calls.
 *
 * <p>Without this, Spring Security's default entry point answered with a
 * 403 (because form login is not configured), so the desktop client could
 * never tell "your session expired, sign in again" apart from "you are not
 * allowed to do that" - and therefore never cleared its stale token.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ApiErrors.write(response, HttpStatus.UNAUTHORIZED,
                "Authentication required. Please sign in again.");
    }
}
