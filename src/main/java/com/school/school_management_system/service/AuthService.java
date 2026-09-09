package com.school.school_management_system.service;

import com.school.school_management_system.dto.AuthResponse;
import com.school.school_management_system.dto.ChangePasswordRequest;
import com.school.school_management_system.dto.CreateUserRequest;
import com.school.school_management_system.dto.LoginRequest;
import com.school.school_management_system.dto.RegisterRequest;
import com.school.school_management_system.dto.UserSummary;
import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.User;
import com.school.school_management_system.exception.EmailAlreadyExistsException;
import com.school.school_management_system.exception.TooManyAttemptsException;
import com.school.school_management_system.repository.UserRepository;
import com.school.school_management_system.security.CustomUserDetails;
import com.school.school_management_system.security.JwtUtil;
import com.school.school_management_system.security.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttempts;
    private final AccountProfileService accountProfiles;
    private final String privilegedRegistrationCode;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            LoginAttemptService loginAttempts,
            AccountProfileService accountProfiles,
            @Value("${app.security.privileged-registration-code:}") String privilegedRegistrationCode
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.loginAttempts = loginAttempts;
        this.accountProfiles = accountProfiles;
        this.privilegedRegistrationCode = privilegedRegistrationCode;
    }

    /**
     * Public sign-up. A STUDENT account is created unless the caller supplies
     * the configured privileged-registration code, in which case the requested
     * role is honoured.
     *
     * <p>Asking for TEACHER or ADMIN without a valid code is <em>refused</em>,
     * not quietly downgraded. It used to be downgraded: choosing "Teacher" on
     * the sign-up screen produced a student account, the new account could
     * still sign in, and the only sign anything had gone wrong was one warning
     * in the server log. Refusing is the only honest answer - the account the
     * person asked for cannot be created here, and pretending otherwise sends
     * them away believing they are a teacher.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Role role = resolveSelfServiceRole(request);

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setEnabled(Boolean.TRUE);
        user = userRepository.save(user);

        // The account alone is half a person: the roster or staff row is what
        // attendance, face enrolment and the Students/Teachers pages read.
        accountProfiles.linkOrCreate(user);

        log.info("Registered new {} account: {}", role, email);
        return tokenFor(user);
    }

    /**
     * The role a public sign-up may have. Both refusals - no code configured
     * and wrong code given - answer with the same status and the same words on
     * purpose: a different response would tell an anonymous caller whether
     * privileged registration is switched on at all.
     */
    private Role resolveSelfServiceRole(RegisterRequest request) {
        Role requested = request.getRole();

        if (requested == null || requested == Role.STUDENT) {
            return Role.STUDENT;
        }
        boolean configured = privilegedRegistrationCode != null && !privilegedRegistrationCode.isBlank();
        if (configured && privilegedRegistrationCode.equals(request.getRegistrationCode())) {
            return requested;
        }
        log.warn("Refused a self-service {} sign-up for {}: registration code missing or not valid.",
                requested, request.getEmail());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "A " + requested + " account needs a valid registration code, so this sign-up was not"
                        + " completed. Ask an administrator to create the account for you, or clear the"
                        + " code and choose Student to sign up for yourself.");
    }

    /**
     * Password login. Wrapped in a per-email attempt limiter, and the
     * failure message never distinguishes "no such account" from "wrong
     * password" so the endpoint cannot be used to enumerate users.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalise(request.getEmail());

        if (loginAttempts.isBlocked(email)) {
            throw new TooManyAttemptsException(
                    "Too many failed sign-in attempts. Try again in "
                            + loginAttempts.minutesRemaining(email) + " minute(s).");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (DisabledException e) {
            loginAttempts.recordFailure(email);
            throw new DisabledException("This account has been disabled. Contact an administrator.");
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(email);
            int left = loginAttempts.remainingAttempts(email);
            String suffix = left > 0 && left <= 2 ? " " + left + " attempt(s) left." : "";
            throw new BadCredentialsException("Incorrect email or password." + suffix);
        }

        loginAttempts.recordSuccess(email);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Incorrect email or password."));

        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        return tokenFor(user);
    }

    /**
     * Admin-only account creation, where the requested role is respected. The
     * roster or staff row is created here too, so an account added on the
     * Account page shows up on the Students or Teachers page without anyone
     * having to type the same person in twice.
     */
    @Transactional
    public UserSummary createUser(CreateUserRequest request) {
        String email = normalise(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setEnabled(Boolean.TRUE);
        user = userRepository.save(user);

        accountProfiles.linkOrCreate(user);

        log.info("Administrator created a {} account: {}", request.getRole(), email);
        return UserSummary.of(user);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        return userRepository.findAllByOrderByEmailAsc().stream()
                .map(UserSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser(String email) {
        return UserSummary.of(requireUser(email));
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = requireUser(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Your current password is not correct.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("The new password must be different from the current one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for {}", user.getEmail());
    }

    /**
     * Enable or disable an account. Refuses to disable the last remaining
     * enabled administrator, which would lock everyone out of the system.
     */
    @Transactional
    public UserSummary setEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No account with id " + userId));

        if (!enabled && user.getRole() == Role.ADMIN && countEnabledAdmins() <= 1) {
            throw new IllegalArgumentException(
                    "This is the only enabled administrator; disabling it would lock everyone out.");
        }

        user.setEnabled(enabled);
        return UserSummary.of(userRepository.save(user));
    }

    /**
     * Change what an account is allowed to do.
     *
     * <p>There was no way to do this at all. The only documented route was a
     * hand-written {@code UPDATE users SET role = ...} against the database,
     * printed in the start-up log - which means the one operation an
     * administrator most obviously needs, turning a new sign-up into a teacher,
     * could not be done from the application that administrators use.
     *
     * <p>The new role's roster or staff row is created here, the same as on any
     * other account-creating path. The <em>old</em> role's row is deliberately
     * left in place: a student row owns attendance history and the id a face was
     * enrolled under, and deleting it to tidy up a role change would destroy
     * records that the change has nothing to do with. It stays linked, and an
     * administrator can remove it from the Students page if it really is
     * finished with.
     */
    @Transactional
    public UserSummary setRole(Long userId, Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Choose the role this account should have.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No account with id " + userId));

        if (user.getRole() == role) {
            return UserSummary.of(user);
        }
        // Same guard as disabling: an administrator who demotes the last
        // administrator - very likely themselves - locks the account screens
        // away from everybody, and cannot undo it from inside the application.
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN
                && user.isEnabled() && countEnabledAdmins() <= 1) {
            throw new IllegalArgumentException(
                    "This is the only enabled administrator; changing its role would leave nobody"
                            + " able to manage accounts.");
        }

        Role previous = user.getRole();
        user.setRole(role);
        user = userRepository.save(user);

        accountProfiles.linkOrCreate(user);

        log.info("Account {} changed from {} to {}.", user.getEmail(), previous, role);
        return UserSummary.of(user);
    }

    private long countEnabledAdmins() {
        return userRepository.findAllByOrderByEmailAsc().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .filter(User::isEnabled)
                .count();
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(normalise(email))
                .orElseThrow(() -> new IllegalStateException("Signed-in account no longer exists: " + email));
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtUtil.generateToken(new CustomUserDetails(user));
        return new AuthResponse(token, user.getEmail(), user.getRole(),
                user.getDisplayName(), jwtUtil.getExpirationMs());
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
