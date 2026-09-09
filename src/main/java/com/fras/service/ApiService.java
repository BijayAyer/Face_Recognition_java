package com.fras.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The desktop client's single door to the Spring Boot backend.
 *
 * <p>Rewritten around one {@link #exchange} core. Previously each of GET,
 * POST, PUT, DELETE and the binary download carried its own copy of the
 * request-building and error-handling code, which had drifted: only some of
 * them cleared the token on 401, none of them could tell an expired session
 * (401) from a permission problem (403), and a 403 left the dead token in
 * place so every later call failed too.
 *
 * <p>What it now guarantees:
 * <ul>
 *   <li>401 clears the session once and notifies
 *       {@link #onSessionExpired} listeners, so the UI can return to the
 *       sign-in screen instead of showing a wall of failures.</li>
 *   <li>403 leaves the session alone - the account is signed in, it simply
 *       may not do that.</li>
 *   <li>The server's own {@code message} field is surfaced, so validation
 *       and lock-out text reaches the user verbatim.</li>
 *   <li>Session fields are {@code volatile}: they are written on background
 *       worker threads and read on the JavaFX thread.</li>
 * </ul>
 */
public class ApiService {

    // =========================================================
    // CONFIGURATION
    // =========================================================

    /**
     * Overridable so the client can be pointed at a backend on another
     * host without a rebuild: {@code -Dfras.api.url=...} wins, then the
     * {@code FRAS_API_URL} environment variable, then localhost.
     */
    private static final String BASE_URL = resolveBaseUrl();

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Ordinary JSON calls. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /** Excel/PDF generation walks the whole register, so it gets longer. */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);

    private static String resolveBaseUrl() {
        String configured = System.getProperty("fras.api.url");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("FRAS_API_URL");
        }
        if (configured == null || configured.isBlank()) {
            return "http://localhost:8080";
        }
        String trimmed = configured.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /**
     * The backend this client talks to. Shown on the sign-in screen so that
     * "cannot reach the server" is diagnosable without reading a log.
     */
    public static String baseUrl() {
        return BASE_URL;
    }

    // =========================================================
    // HTTP / JSON
    // =========================================================

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // =========================================================
    // SESSION STATE
    // =========================================================

    private volatile String token;
    private volatile String email;
    private volatile String role;
    private volatile String fullName;

    /** Wall-clock instant the token stops being accepted; 0 when unknown. */
    private volatile long expiresAtEpochMs;

    private final List<Runnable> sessionExpiredListeners = new CopyOnWriteArrayList<>();

    public ApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * One instance shared by the whole desktop app. The academic-setup
     * module builds its DAOs itself, with nothing injecting them, so this
     * is how those DAOs reach the session established at sign-in rather
     * than each needing its own.
     */
    private static final ApiService SHARED = new ApiService();

    public static ApiService getShared() {
        return SHARED;
    }

    // =========================================================
    // SESSION
    // =========================================================

    /** Called whenever the server rejects the token; used to show the sign-in screen. */
    public void onSessionExpired(Runnable listener) {
        if (listener != null) {
            sessionExpiredListeners.add(listener);
        }
    }

    public String getToken() {
        return token;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    /** Best available human label: full name, else the local part of the email. */
    public String getDisplayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String current = email;
        if (current == null || current.isBlank()) {
            return "Signed in";
        }
        int at = current.indexOf('@');
        return at > 0 ? current.substring(0, at) : current;
    }

    public boolean isAuthenticated() {
        return token != null && !token.isBlank() && !isSessionExpired();
    }

    /** True once the token's own expiry has passed, without asking the server. */
    public boolean isSessionExpired() {
        long expiry = expiresAtEpochMs;
        return expiry > 0 && System.currentTimeMillis() >= expiry;
    }

    public boolean hasRole(String... wanted) {
        String current = role;
        if (current == null || wanted == null) {
            return false;
        }
        for (String candidate : wanted) {
            if (candidate != null && candidate.equalsIgnoreCase(current)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /** Admins can do everything a teacher can, so this covers both. */
    public boolean canRecordAttendance() {
        return hasRole("ADMIN", "TEACHER");
    }

    /**
     * True for an account that is neither an admin nor a teacher.
     *
     * <p>Written as "not staff" rather than "role == STUDENT" on purpose. If a
     * role is ever added to the enum, an account carrying it must not silently
     * inherit the staff screens; the worst this can do is show somebody the
     * read-only view of their own attendance.
     */
    public boolean isStudent() {
        return isAuthenticated() && !canRecordAttendance();
    }

    /** Deliberate sign-out. Does not fire the session-expired listeners. */
    public void logout() {
        clearSession();
    }

    private void clearSession() {
        token = null;
        email = null;
        role = null;
        fullName = null;
        expiresAtEpochMs = 0L;
    }

    private void notifySessionExpired() {
        for (Runnable listener : sessionExpiredListeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A misbehaving listener must not turn into a failed API call.
            }
        }
    }

    // =========================================================
    // SIGN IN / SIGN UP
    // =========================================================

    /** {@code POST /auth/login}. Never throws: the failure text is in the result. */
    public LoginResult login(String email, String password) {
        logout();

        if (email == null || email.isBlank()) {
            return LoginResult.failed("Enter your email address.");
        }
        if (password == null || password.isEmpty()) {
            return LoginResult.failed("Enter your password.");
        }

        ObjectNode payload = objectMapper.createObjectNode()
                .put("email", email.trim())
                .put("password", password);

        return authenticate("/auth/login", payload, "Sign-in");
    }

    /**
     * {@code POST /auth/register}. A successful sign-up returns a token, so it
     * signs the person in as well.
     *
     * <p>{@code role} is only honoured when the server has been given a
     * privileged-registration code and {@code registrationCode} matches it.
     * Otherwise asking for TEACHER or ADMIN is <em>refused</em> with a 403 and
     * no account is created - it is not quietly turned into a student account,
     * which is what used to happen and left people signed in as something they
     * had not asked to be. The endpoint is public, so it cannot be allowed to
     * mint privileged accounts either way.
     */
    public LoginResult register(String fullName,
                                String email,
                                String password,
                                String role,
                                String registrationCode) {
        logout();

        if (email == null || email.isBlank()) {
            return LoginResult.failed("Enter your email address.");
        }
        if (password == null || password.length() < 8) {
            return LoginResult.failed("Choose a password of at least 8 characters.");
        }

        ObjectNode payload = objectMapper.createObjectNode()
                .put("email", email.trim())
                .put("password", password);

        if (fullName != null && !fullName.isBlank()) {
            payload.put("fullName", fullName.trim());
        }
        if (role != null && !role.isBlank()) {
            payload.put("role", role.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (registrationCode != null && !registrationCode.isBlank()) {
            payload.put("registrationCode", registrationCode.trim());
        }

        return authenticate("/auth/register", payload, "Sign-up");
    }

    /** Shared body of login and register: post the payload, adopt the token. */
    private LoginResult authenticate(String endpoint, ObjectNode payload, String what) {
        try {
            String body = exchange("POST", endpoint, payload.toString(), false, REQUEST_TIMEOUT);
            JsonNode json = objectMapper.readTree(body == null ? "" : body);

            String receivedToken = text(json, "token");
            if (receivedToken == null) {
                return LoginResult.failed(what + " failed: the server did not return a token.");
            }

            this.token = receivedToken;
            this.email = firstNonBlank(text(json, "email"), payload.path("email").asText(null));
            this.role = text(json, "role");
            this.fullName = text(json, "fullName");

            long lifetimeMs = json.path("expiresInMs").asLong(0L);
            // Expire the session slightly early so a call cannot be sent with
            // a token that dies in flight.
            this.expiresAtEpochMs = lifetimeMs > 0
                    ? System.currentTimeMillis() + Math.max(0L, lifetimeMs - 5_000L)
                    : 0L;

            return new LoginResult(true, what + " successful.",
                    this.email, this.role, this.fullName, this.token);

        } catch (ApiException e) {
            clearSession();
            return LoginResult.failed(e.getMessage());
        } catch (IOException e) {
            clearSession();
            return LoginResult.failed(what + " failed: the server's reply could not be read.");
        }
    }

    /**
     * {@code GET /auth/me}. Confirms the token is still good and refreshes the
     * cached name and role. Returns false instead of throwing when the
     * session has gone, because the caller's next move is the same either way.
     */
    public boolean verifySession() {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            JsonNode json = objectMapper.readTree(get("/auth/me"));
            this.email = firstNonBlank(text(json, "email"), this.email);
            this.role = firstNonBlank(text(json, "role"), this.role);
            this.fullName = firstNonBlank(text(json, "fullName"), this.fullName);
            return true;
        } catch (ApiException | IOException e) {
            return false;
        }
    }

    /** {@code POST /auth/change-password}. */
    public void changePassword(String currentPassword, String newPassword) throws ApiException {
        String json = objectMapper.createObjectNode()
                .put("currentPassword", currentPassword == null ? "" : currentPassword)
                .put("newPassword", newPassword == null ? "" : newPassword)
                .toString();
        exchange("POST", "/auth/change-password", json, true, REQUEST_TIMEOUT);
    }

    // =========================================================
    // ACCOUNTS (ADMIN)
    // =========================================================

    /** {@code GET /auth/users} - a JSON array of accounts. */
    public String getUsers() throws ApiException {
        return get("/auth/users");
    }

    /** {@code POST /auth/users} - create an account with any role. */
    public String createUser(String fullName, String email, String password, String role)
            throws ApiException {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("email", email == null ? "" : email.trim())
                .put("password", password == null ? "" : password)
                .put("role", role == null ? "STUDENT" : role.trim().toUpperCase(java.util.Locale.ROOT));
        if (fullName != null && !fullName.isBlank()) {
            payload.put("fullName", fullName.trim());
        }
        return post("/auth/users", payload.toString());
    }

    /** {@code POST /auth/users/{id}/enabled?enabled=...} */
    public String setUserEnabled(long userId, boolean enabled) throws ApiException {
        return post("/auth/users/" + userId + "/enabled?enabled=" + enabled, null);
    }

    /**
     * {@code POST /auth/users/{id}/role?role=...} - promote or demote.
     *
     * <p>The role is upper-cased here because the server binds it as an enum
     * and would answer a lower-case value with a 400 about a format, which
     * tells the person nothing they can act on.
     */
    public String setUserRole(long userId, String role) throws ApiException {
        String wanted = role == null ? "" : role.trim().toUpperCase(java.util.Locale.ROOT);
        return post("/auth/users/" + userId + "/role?role=" + wanted, null);
    }

    // =========================================================
    // ROSTER
    // =========================================================

    /** {@code GET /students}, optionally filtered by name or email. */
    public String getStudents() throws ApiException {
        return get("/students");
    }

    public String searchStudents(String query) throws ApiException {
        if (query == null || query.isBlank()) {
            return getStudents();
        }
        return get("/students?q=" + enc(query.trim()));
    }

    public String getTeachers() throws ApiException {
        return get("/teachers");
    }

    public String createStudent(String name, String email, int age, String section)
            throws ApiException {
        return post("/students", studentBody(name, email, age, section));
    }

    /**
     * {@code PUT /students/{id}} - replaces the row, so every editable value has
     * to be sent, not only the changed ones.
     */
    public String updateStudent(long id, String name, String email, int age, String section)
            throws ApiException {
        return put("/students/" + id, studentBody(name, email, age, section));
    }

    /**
     * The body both student writes take.
     *
     * <p>A blank section is left out of the JSON rather than sent as "". The
     * column answers "which group is this student in", and an empty string is
     * not a group; the server maps blank to null anyway, so omitting it says the
     * same thing without relying on that. On an update, leaving it out is how a
     * section gets cleared, which is what an emptied field on the edit form
     * means.
     */
    private String studentBody(String name, String email, int age, String section) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("name", name)
                .put("email", email)
                .put("age", age);
        if (section != null && !section.isBlank()) {
            body.put("section", section.trim());
        }
        return body.toString();
    }

    public String deleteStudent(long id) throws ApiException {
        return delete("/students/" + id);
    }

    public String createTeacher(String name, String email, String subject) throws ApiException {
        String json = objectMapper.createObjectNode()
                .put("name", name)
                .put("email", email)
                .put("subject", subject)
                .toString();
        return post("/teachers", json);
    }

    /**
     * {@code PUT /teachers/{id}} - replaces the row. Subject is NOT NULL on the
     * server, so it cannot be sent blank the way a student's section can.
     */
    public String updateTeacher(long id, String name, String email, String subject) throws ApiException {
        String json = objectMapper.createObjectNode()
                .put("name", name)
                .put("email", email)
                .put("subject", subject)
                .toString();
        return put("/teachers/" + id, json);
    }

    public String deleteTeacher(long id) throws ApiException {
        return delete("/teachers/" + id);
    }

    // =========================================================
    // ACADEMIC LOOKUPS
    // =========================================================

    /**
     * {@code GET /academic/classrooms}. The client used to ask a person to type
     * the numeric classroom id from memory, which is the only reason "Classroom
     * ID and Subject ID must both be numbers" was ever an error worth showing.
     */
    public String getClassrooms() throws ApiException {
        return get("/academic/classrooms");
    }

    /** {@code GET /academic/subjects}, optionally narrowed to one semester. */
    public String getSubjects(Long semesterId) throws ApiException {
        return semesterId == null
                ? get("/academic/subjects")
                : get("/academic/subjects?semesterId=" + semesterId);
    }

    // =========================================================
    // ATTENDANCE
    // =========================================================

    /**
     * {@code POST /attendance/mark}. {@code studentId} is the backend's
     * numeric student id, which is also the face-enrolment folder name, so a
     * recognised label maps straight onto this call.
     */
    public String markAttendance(long studentId,
                                 long classroomId,
                                 long subjectId,
                                 String markedBy,
                                 Double confidenceScore) throws ApiException {
        return markAttendance(studentId, classroomId, subjectId, markedBy, confidenceScore, null);
    }

    /**
     * As above, but passing the session's start time so the backend can
     * record a late arrival. Without it every mark is PRESENT and the LATE
     * status is unreachable - which is exactly what used to happen, because
     * the client never sent this parameter.
     *
     * @param sessionStartTime {@code HH:mm}, or null for "do not judge lateness"
     */
    public String markAttendance(long studentId,
                                 long classroomId,
                                 long subjectId,
                                 String markedBy,
                                 Double confidenceScore,
                                 String sessionStartTime) throws ApiException {

        ObjectNode payload = objectMapper.createObjectNode()
                .put("studentId", studentId)
                .put("classroomId", classroomId)
                .put("subjectId", subjectId)
                .put("markedBy", markedBy);

        if (confidenceScore != null) {
            payload.put("confidenceScore", confidenceScore);
        }

        String endpoint = "/attendance/mark";
        if (sessionStartTime != null && !sessionStartTime.isBlank()) {
            endpoint += "?sessionStartTime=" + enc(sessionStartTime.trim());
        }
        return post(endpoint, payload.toString());
    }

    /**
     * {@code POST /attendance/mark-absentees} - everyone unmarked becomes ABSENT.
     *
     * @return how many ABSENT rows the server actually wrote. Worth having
     *         rather than assuming: the caller's own "roster minus recognised"
     *         arithmetic counts people who may already have been marked
     *         somewhere else - another device, another teacher, an earlier
     *         close-out - and the server writes only the rows that were
     *         genuinely missing. Reporting the local guess as the outcome is
     *         how a register comes to claim thirty absences when it recorded
     *         eleven.
     */
    public int markAbsentees(long classroomId, long subjectId, String isoDate, List<Long> roster)
            throws ApiException {
        if (roster == null || roster.isEmpty()) {
            return 0;
        }
        StringBuilder endpoint = new StringBuilder("/attendance/mark-absentees")
                .append("?classroomId=").append(classroomId)
                .append("&subjectId=").append(subjectId)
                .append("&roster=").append(joinRoster(roster));
        if (isoDate != null && !isoDate.isBlank()) {
            endpoint.append("&date=").append(enc(isoDate.trim()));
        }
        String body = post(endpoint.toString(), null);

        // The write succeeded by the time we are here; only the count is in
        // doubt. An older server answers with an empty 200, so an unreadable
        // or absent figure means "we do not know", not "nothing happened" -
        // and -1 lets the caller say so instead of claiming zero.
        try {
            JsonNode json = objectMapper.readTree(body == null ? "" : body);
            return json.path("marked").asInt(-1);
        } catch (IOException unreadable) {
            return -1;
        }
    }

    /** {@code GET /attendance/student/{id}} - full history, most recent first. */
    public String getStudentAttendance(long studentId) throws ApiException {
        return get("/attendance/student/" + studentId);
    }

    /**
     * {@code GET /attendance/me} - the signed-in student's own history.
     *
     * <p>Separate from {@link #getStudentAttendance(long)} because a student
     * account has no way to know its own roster id: the login lives in
     * {@code users} and the roster row in {@code students}, joined only by
     * email address. Asking the server to do that lookup is also the only
     * version of the question that cannot be pointed at somebody else.
     */
    public String getMyAttendance() throws ApiException {
        return get("/attendance/me");
    }

    /** {@code GET /attendance/classroom/{c}/subject/{s}/date/{yyyy-MM-dd}} */
    public String getClassRegister(long classroomId, long subjectId, String isoDate)
            throws ApiException {
        return get("/attendance/classroom/" + classroomId
                + "/subject/" + subjectId
                + "/date/" + enc(isoDate));
    }

    /** {@code GET /attendance/summary/student/{s}/subject/{j}} - percentage over a range. */
    public String getAttendanceSummary(long studentId, long subjectId,
                                       String startIsoDate, String endIsoDate) throws ApiException {
        return get("/attendance/summary/student/" + studentId + "/subject/" + subjectId
                + "?startDate=" + enc(startIsoDate)
                + "&endDate=" + enc(endIsoDate));
    }

    /** {@code GET /attendance/export/excel/daily} - raw .xlsx bytes. */
    public byte[] downloadAttendanceExcel(long classroomId, long subjectId,
                                          String isoDate, List<Long> roster) throws ApiException {
        return getBytes(exportEndpoint("excel", classroomId, subjectId, isoDate, roster));
    }

    /** {@code GET /attendance/export/pdf/daily} - raw .pdf bytes. */
    public byte[] downloadAttendancePdf(long classroomId, long subjectId,
                                        String isoDate, List<Long> roster) throws ApiException {
        return getBytes(exportEndpoint("pdf", classroomId, subjectId, isoDate, roster));
    }

    private String exportEndpoint(String format, long classroomId, long subjectId,
                                  String isoDate, List<Long> roster) {
        return "/attendance/export/" + format + "/daily"
                + "?classroomId=" + classroomId
                + "&subjectId=" + subjectId
                + "&date=" + enc(isoDate)
                + "&roster=" + joinRoster(roster);
    }

    private static String joinRoster(List<Long> roster) {
        if (roster == null || roster.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roster.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(roster.get(i));
        }
        return sb.toString();
    }

    // =========================================================
    // GENERIC VERBS
    // =========================================================

    public String get(String endpoint) throws ApiException {
        return exchange("GET", endpoint, null, true, REQUEST_TIMEOUT);
    }

    public String post(String endpoint, String jsonBody) throws ApiException {
        return exchange("POST", endpoint, jsonBody, true, REQUEST_TIMEOUT);
    }

    public String put(String endpoint, String jsonBody) throws ApiException {
        return exchange("PUT", endpoint, jsonBody, true, REQUEST_TIMEOUT);
    }

    public String delete(String endpoint) throws ApiException {
        return exchange("DELETE", endpoint, null, true, REQUEST_TIMEOUT);
    }

    // =========================================================
    // THE ONE REQUEST PATH
    // =========================================================

    /**
     * Builds, sends and interprets a request. Every call in this class goes
     * through here, so the 401/403 rules and the error-message extraction
     * cannot drift between verbs the way they had.
     */
    private String exchange(String method,
                            String endpoint,
                            String jsonBody,
                            boolean authenticated,
                            Duration timeout) throws ApiException {

        if (authenticated) {
            ensureAuthenticated();
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri(endpoint))
                .timeout(timeout)
                .header("Accept", "application/json");

        if (jsonBody == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        }

        if (authenticated) {
            String bearer = token;
            if (bearer != null) {
                builder.header("Authorization", "Bearer " + bearer);
            }
        }

        HttpResponse<String> response = sendRaw(builder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (isSuccess(status)) {
            return response.body();
        }
        throw failure(status, response.body());
    }

    /** Binary download: exports come back as a file, not JSON. */
    private byte[] getBytes(String endpoint) throws ApiException {
        ensureAuthenticated();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri(endpoint))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("Accept", "application/octet-stream")
                .GET();

        String bearer = token;
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }

        HttpResponse<byte[]> response = sendRaw(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        byte[] body = response.body() == null ? new byte[0] : response.body();

        if (isSuccess(status)) {
            if (body.length == 0) {
                throw new ApiException("The server returned an empty file.", status);
            }
            return body;
        }
        throw failure(status, new String(body, StandardCharsets.UTF_8));
    }

    private <T> HttpResponse<T> sendRaw(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws ApiException {
        try {
            return httpClient.send(request, handler);

        } catch (HttpTimeoutException e) {
            throw new ApiException("The server took too long to respond. Please try again.", e);

        } catch (ConnectException e) {
            throw new ApiException(
                    "Cannot reach the server at " + BASE_URL
                            + ". Start the backend, then try again.", e);

        } catch (IOException e) {
            throw new ApiException("Network error: " + safeMessage(e), e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("The request was cancelled.", e);
        }
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    /**
     * Turns a non-2xx response into an exception the UI can act on.
     *
     * <p>The 401/403 split is the important part. 401 means the token is gone
     * or expired, so the session is cleared once and the sign-in screen is
     * requested. 403 means the account is fine but may not do this, so the
     * token is left alone - previously a single 403 was treated the same as an
     * expired session by some verbs and ignored by others.
     */
    private ApiException failure(int status, String body) {
        String serverMessage = extractErrorMessage(body);

        if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            boolean hadSession = token != null;
            clearSession();
            if (hadSession) {
                notifySessionExpired();
            }
            return new ApiException(
                    firstNonBlank(serverMessage, "Your session has ended. Please sign in again."),
                    status);
        }

        if (status == HttpURLConnection.HTTP_FORBIDDEN) {
            return new ApiException(
                    firstNonBlank(serverMessage, "Your account does not have permission to do that."),
                    status);
        }

        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            return new ApiException(firstNonBlank(serverMessage, "That record no longer exists."), status);
        }

        if (status == HttpURLConnection.HTTP_CONFLICT) {
            return new ApiException(firstNonBlank(serverMessage, "That record already exists."), status);
        }

        if (status == 429) {
            return new ApiException(
                    firstNonBlank(serverMessage, "Too many attempts. Please wait and try again."), status);
        }

        if (status >= 500) {
            return new ApiException(
                    firstNonBlank(serverMessage, "The server hit an unexpected problem."), status);
        }

        return new ApiException(
                firstNonBlank(serverMessage, "The request failed (HTTP " + status + ")."), status);
    }

    private void ensureAuthenticated() throws ApiException {
        String current = token;
        if (current == null || current.isBlank()) {
            throw new ApiException("Please sign in first.", HttpURLConnection.HTTP_UNAUTHORIZED);
        }
        if (isSessionExpired()) {
            clearSession();
            notifySessionExpired();
            throw new ApiException("Your session has ended. Please sign in again.",
                    HttpURLConnection.HTTP_UNAUTHORIZED);
        }
    }

    private static URI uri(String endpoint) {
        String path = endpoint == null ? "" : endpoint;
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(BASE_URL + path);
    }

    /** Percent-encodes one query-string value. */
    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Pulls the human-readable sentence out of an error body. The backend
     * always answers with {@code {"timestamp","status","error","message"}},
     * so {@code message} is preferred; the rest are fallbacks, and a body
     * that is not JSON at all is never forwarded verbatim because it may be
     * an HTML error page.
     */
    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            String message = text(json, "message");
            if (message != null) {
                return message;
            }
            JsonNode errors = json.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode node : errors) {
                    if (!sb.isEmpty()) {
                        sb.append("; ");
                    }
                    sb.append(node.isTextual() ? node.asText() : node.path("defaultMessage").asText(""));
                }
                if (!sb.isEmpty()) {
                    return sb.toString();
                }
            }
            return text(json, "error");

        } catch (Exception e) {
            return null;
        }
    }

    /** Reads a JSON field, mapping missing/null/blank all to null. */
    private static String text(JsonNode json, String field) {
        if (json == null) {
            return null;
        }
        JsonNode node = json.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    // =========================================================
    // RESULT AND FAILURE TYPES
    // =========================================================

    /** Outcome of a sign-in or sign-up attempt. */
    public static class LoginResult {

        private final boolean success;
        private final String message;
        private final String email;
        private final String role;
        private final String fullName;
        private final String token;

        public LoginResult(boolean success, String message, String email,
                           String role, String fullName, String token) {
            this.success = success;
            this.message = message;
            this.email = email;
            this.role = role;
            this.fullName = fullName;
            this.token = token;
        }

        static LoginResult failed(String message) {
            return new LoginResult(false,
                    message == null || message.isBlank() ? "That did not work." : message,
                    null, null, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }

        public String getFullName() {
            return fullName;
        }

        public String getToken() {
            return token;
        }
    }

    /**
     * A failed API call. Carries the HTTP status so callers can branch -
     * {@link #isUnauthorized()} in particular tells the UI to go back to the
     * sign-in screen rather than just showing a message.
     */
    public static class ApiException extends Exception {

        private static final long serialVersionUID = 1L;

        /** 0 when the call never reached the server. */
        private final int statusCode;

        public ApiException(String message) {
            this(message, 0);
        }

        public ApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = 0;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isUnauthorized() {
            return statusCode == HttpURLConnection.HTTP_UNAUTHORIZED;
        }

        public boolean isForbidden() {
            return statusCode == HttpURLConnection.HTTP_FORBIDDEN;
        }

        public boolean isNotFound() {
            return statusCode == HttpURLConnection.HTTP_NOT_FOUND;
        }

        public boolean isConflict() {
            return statusCode == HttpURLConnection.HTTP_CONFLICT;
        }

        /** True when the request never got a reply at all. */
        public boolean isNetworkFailure() {
            return statusCode == 0;
        }
    }
}
