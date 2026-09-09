package com.school.school_management_system.config;

import com.school.school_management_system.security.CustomUserDetailsService;
import com.school.school_management_system.security.JwtAuthFilter;
import com.school.school_management_system.security.JwtUtil;
import com.school.school_management_system.security.RestAccessDeniedHandler;
import com.school.school_management_system.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * HTTP security for the backend.
 *
 * <p>Changes from the original configuration, all of them things the
 * desktop client was tripping over:
 * <ul>
 *   <li><b>401 vs 403.</b> A custom {@code AuthenticationEntryPoint} is
 *       installed, so an expired or missing token produces 401 and a genuine
 *       permission problem produces 403. Previously everything came back as
 *       403 and the client could not tell it needed to re-authenticate.</li>
 *   <li><b>{@code /auth/**} is no longer wide open.</b> Only login and
 *       sign-up are public; {@code /auth/me}, {@code /auth/users} and
 *       password changes require a token.</li>
 *   <li><b>The H2 console is off unless explicitly enabled.</b> It used to
 *       be permitted unconditionally, which is an unauthenticated SQL shell
 *       over the live database on a listening port.</li>
 *   <li><b>The JWT filter is constructed here</b> rather than being a
 *       {@code @Component}, so Boot does not also register it in the plain
 *       servlet chain and run it twice per request.</li>
 *   <li><b>Attendance and roster data are no longer readable by "anyone
 *       signed in".</b> Sign-up is public and produces a STUDENT account, so
 *       that phrase meant "anyone at all, after filling in a form". A student
 *       could list every student with their email address, pull any class
 *       register, and read any other student's full attendance history by
 *       counting {@code studentId} upwards. Registers, exports and roster
 *       reads are now staff-only; per-student reads are checked against the
 *       caller's own identity by {@code AttendanceAccessPolicy}, because a
 *       URL pattern cannot express a rule that depends on the value of a
 *       path variable.</li>
 * </ul>
 *
 * <p>Note that this application binds an HTTP API on
 * {@code localhost:8080} with no TLS. That is appropriate for a desktop
 * client talking to a backend on the same machine; exposing the port beyond
 * localhost would need TLS and a real secret in {@code FRAS_JWT_SECRET}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final boolean h2ConsoleEnabled;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtUtil jwtUtil,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // Constructor injection of the UserDetailsService; the no-arg
        // constructor plus setUserDetailsService(..) is deprecated.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // An unknown email is reported as bad credentials rather than as
        // "no such user", so a stranger cannot use the sign-in form to work
        // out which addresses are registered. True is the default; it is set
        // explicitly because it is a decision, not an accident.
        //
        // It does not affect DisabledException or LockedException, which are
        // thrown after the account has been found and still reach the client
        // as their own 403 - see GlobalExceptionHandler.handleAccountStatus.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtUtil, userDetailsService);

        http
                // Stateless bearer-token API: there is no browser session or
                // form to protect, and the desktop client cannot hold a CSRF
                // token. Every mutating endpoint requires the Authorization
                // header, which a cross-site request cannot set.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {

                    // ---- Public ----
                    auth.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll();
                    auth.requestMatchers("/error").permitAll();

                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }

                    // ---- Accounts ----
                    auth.requestMatchers("/auth/users/**").hasRole("ADMIN");
                    auth.requestMatchers("/auth/**").authenticated();

                    // ---- Roster: staff only, reads included ----
                    // Sign-up is public, so "any signed-in account" is a
                    // boundary anyone can cross by filling in a form. A roster
                    // read returns every student's name and email address,
                    // which is not something a self-registered account should
                    // be handed.
                    //
                    // Writes are narrower than reads. A teacher may read the
                    // roster - the Enrol screen needs it to know which id a
                    // face belongs to - but may not change it. A roster row now
                    // owns the link to a sign-in account, the id a face was
                    // enrolled under, and every attendance record pointing at
                    // that id, so deleting one is not a roster edit; it is a
                    // records deletion. That is an administrator's decision,
                    // and the client agrees: the Students and Teachers pages
                    // are only in the rail for an admin.
                    //
                    // Order matters: the first matcher that matches decides, so
                    // the narrower write rules have to be declared before the
                    // rule that covers all of /students/** and /teachers/**.
                    auth.requestMatchers(HttpMethod.POST, "/students/**", "/teachers/**")
                            .hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.PUT, "/students/**", "/teachers/**")
                            .hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/students/**", "/teachers/**")
                            .hasRole("ADMIN");

                    auth.requestMatchers("/students/**", "/teachers/**").hasAnyRole("ADMIN", "TEACHER");

                    // ---- Attendance ----
                    // Two layers, because a URL rule and a record-level rule
                    // answer different questions. These matchers say who may
                    // reach the endpoint at all; AttendanceAccessPolicy, called
                    // from the controller, says whose records they may see -
                    // which cannot be expressed here, because it depends on the
                    // value of the studentId in the path.
                    //
                    // Registers and exports name every student in a session, so
                    // they are staff-only outright. They are listed before the
                    // general rule below because the first matcher to match wins.
                    auth.requestMatchers(HttpMethod.GET,
                            "/attendance/classroom/**",
                            "/attendance/export/**").hasAnyRole("ADMIN", "TEACHER");

                    auth.requestMatchers(HttpMethod.GET, "/attendance/**").authenticated();
                    auth.requestMatchers(HttpMethod.POST, "/attendance/**").hasAnyRole("ADMIN", "TEACHER");
                    auth.requestMatchers(HttpMethod.PUT, "/attendance/**").hasAnyRole("ADMIN", "TEACHER");
                    auth.requestMatchers(HttpMethod.DELETE, "/attendance/**").hasAnyRole("ADMIN", "TEACHER");

                    // ---- Academic setup: everyone reads, only ADMIN edits ----
                    auth.requestMatchers(HttpMethod.GET, "/academic/**").authenticated();
                    auth.requestMatchers(HttpMethod.POST, "/academic/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.PUT, "/academic/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/academic/**").hasRole("ADMIN");

                    auth.anyRequest().authenticated();
                })
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (h2ConsoleEnabled) {
            // The console renders inside a frame and posts its own forms.
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
            http.csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/h2-console/**")));
        } else {
            http.headers(headers -> headers.frameOptions(frame -> frame.deny()));
        }

        return http.build();
    }
}
