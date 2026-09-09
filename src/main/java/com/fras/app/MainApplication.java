package com.fras.app;

import com.fras.config.CameraService;
import com.fras.service.ApiService;
import com.fras.ui.AppShell;
import com.fras.ui.AuthScreen;
import com.fras.ui.Theme;
import com.fras.ui.Toast;
import com.fras.ui.pages.AcademicPage;
import com.fras.ui.pages.AccountPage;
import com.fras.ui.pages.DashboardPage;
import com.fras.ui.pages.EnrollPage;
import com.fras.ui.pages.LivePage;
import com.fras.ui.pages.MyAttendancePage;
import com.fras.ui.pages.ReportsPage;
import com.fras.ui.pages.StudentsPage;
import com.fras.ui.pages.TeachersPage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Starts the window and decides which of the two things is in it: the sign-in
 * screen, or the shell.
 *
 * <p>This class used to be the whole client - every screen was a method on it
 * that built a fresh {@link Scene} and handed it to the stage, which is why
 * navigating anywhere resized the window, lost focus, and needed the camera
 * stopped from eleven different places. The screens are {@code Page}s now, the
 * rail is {@link AppShell}, and what is left here is the part that only an
 * {@code Application} can do: own the stage, own the one scene, and swap its
 * root between signed-out and signed-in.
 */
public final class MainApplication extends Application {

    private final CameraService camera = new CameraService();
    private final ApiService api = ApiService.getShared();

    private Scene scene;
    private AppShell shell;

    /**
     * Where an expired session is handled right now. {@code ApiService} keeps
     * its listeners for the life of the process and offers no way to remove
     * one, so exactly one is registered - in {@link #start} - and it delegates
     * here. Registering a fresh listener per sign-in would mean the fifth
     * session bounced you to the sign-in screen five times.
     */
    private Runnable sessionExpired = () -> { };

    /**
     * Runs once JavaFX is up. Everything that lives for the whole run is set up
     * here - the stage, the single scene, the single session-expired listener -
     * and then the sign-in screen goes into it.
     */
    @Override
    public void start(Stage stage) {

        // Registered once, for the life of the process, because ApiService has
        // no way to remove a listener. It delegates to a field that is
        // repointed on each sign-in, so an expiry is handled once rather than
        // once per session ever started.
        api.onSessionExpired(() -> Platform.runLater(() -> sessionExpired.run()));

        AuthScreen auth = new AuthScreen(api, this::onSignedIn);
        scene = new Scene(auth, 1180, 760);
        Theme.apply(scene);
        Toast.attach(auth);

        stage.setTitle("FRAS - attendance by recognition");
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> shutdown());
        stage.show();

        auth.focusFirst();
    }

    // =========================================================
    // SIGNED OUT
    // =========================================================

    /**
     * Back to the sign-in screen, for either of the two reasons it happens: you
     * asked to sign out, or the token stopped being accepted.
     *
     * <p>The camera is stopped and the visible page released before anything
     * else. A page that owns the device cannot be trusted to notice that it is
     * no longer on screen, and the old build shut the camera down from eleven
     * call sites for exactly that reason.
     *
     * @param message what the sign-in screen should say about why you are here
     * @param asProblem true if that message is a failure rather than an outcome
     */
    private void showAuth(String message, boolean asProblem) {
        if (shell != null) {
            shell.releaseCurrent();
            shell = null;
        }
        camera.stop();
        api.logout();

        // Nothing to expire until somebody signs in again.
        sessionExpired = () -> { };

        AuthScreen auth = new AuthScreen(api, this::onSignedIn);
        setRoot(auth);

        // The toast layer belongs to whichever root is on screen; the shell's
        // went away with the shell.
        Toast.attach(auth);

        if (message != null && !message.isBlank()) {
            if (asProblem) {
                auth.showProblem(message);
            } else {
                auth.showMessage(message);
            }
        }
        auth.focusFirst();
    }

    // =========================================================
    // SIGNED IN
    // =========================================================

    /**
     * Handed the outcome of a sign-in or a sign-up by {@link AuthScreen}. The
     * shell is built for the account that just arrived, so a teacher's rail and
     * an admin's rail are different objects rather than the same rail with rows
     * greyed out.
     */
    private void onSignedIn(ApiService.LoginResult result) {
        if (result == null || !result.isSuccess()) {
            // AuthScreen only calls this on success. Guarded anyway: an empty
            // shell in front of somebody is worse than staying put.
            return;
        }

        AppShell built = buildShell();
        shell = built;

        // Set after the shell exists, so an expiry arriving mid-sign-in cannot
        // try to tear down something that is not on screen yet.
        sessionExpired = () ->
                showAuth("Your session has expired. Sign in again to carry on.", true);

        setRoot(built);

        String first = built.firstKey();
        if (first != null) {
            built.show(first);
        }
        Toast.ok("Signed in as " + api.getDisplayName() + ".");
    }

    /**
     * The rail, in the order it is read - which is the same order
     * Cmd/Ctrl + 1..9 follows, so the printed keystroke and the key that works
     * cannot drift apart.
     *
     * <p>Pages are registered as suppliers and built on first open. Signing in
     * therefore costs one screen and one set of requests, not seven of each.
     * Entries this account cannot use are left out rather than disabled, and
     * {@code DashboardPage} offers the same set on the same condition.
     *
     * <p>A student gets a different rail rather than a shorter one. Sign-up is
     * public and, without a registration code, can only produce a STUDENT - so
     * this method decides what a stranger who fills in the form is shown.
     * Roster reads, class registers and exports are staff-only on the server,
     * and per-student reads are checked against the caller's own identity, so
     * every staff page here would open and then fail with a 403 - starting with
     * the overview, which begins by counting the students it is not allowed to
     * list.
     *
     * <p>A teacher gets the staff rail without the People and Structure groups.
     * That is not a shortened admin rail either: a teacher may read the roster,
     * which is why the Enrol screen can offer a student picker, but may not
     * change it, so pages whose whole purpose is adding and removing people
     * would be a row of buttons that answer 403.
     */
    private AppShell buildShell() {
        AppShell built = new AppShell(api, () -> showAuth("You are signed out.", false));

        if (api.isStudent()) {
            built.group("You");
            built.add("mine", "My attendance", () -> new MyAttendancePage(api));
            built.add("account", "Account", () -> new AccountPage(api));
            return built;
        }

        built.group("Today");
        built.add("home", "Overview", () -> new DashboardPage(api, built));
        if (api.canRecordAttendance()) {
            built.add("live", "Live session", () -> new LivePage(api, camera));
            built.add("enroll", "Enrol a face", () -> new EnrollPage(api, camera));
        }
        built.add("register", "Register", () -> new ReportsPage(api));

        if (api.isAdmin()) {
            built.group("People");
            built.add("students", "Students", () -> new StudentsPage(api));
            built.add("teachers", "Teachers", () -> new TeachersPage(api));

            built.group("Structure");
            built.add("academic", "Academic setup", AcademicPage::new);
        }

        built.group("You");
        built.add("account", "Account", () -> new AccountPage(api));
        return built;
    }

    // =========================================================
    // WINDOW
    // =========================================================

    /**
     * Swaps what the window contains without replacing the scene. Replacing it
     * is what used to resize the window, drop the keyboard shortcuts and reset
     * the theme on every navigation. The stylesheet is on the scene and survives
     * the swap; the light/dark class is on the root and does not, which is what
     * the second line is for.
     */
    private void setRoot(Parent root) {
        scene.setRoot(root);
        Theme.apply(scene);
    }

    /**
     * The close button. The camera holds a native capture device and the thread
     * behind it is not a daemon, so releasing it is what actually lets the
     * process end rather than linger with no window.
     */
    private void shutdown() {
        if (shell != null) {
            shell.releaseCurrent();
        }
        camera.stop();
        api.logout();
        Platform.exit();
    }

    /**
     * Also runs when the platform exits some other way - a call to
     * {@code Platform.exit}, or the last window closing. Both paths are safe to
     * take twice: {@code stop} on an already-stopped camera does nothing, and
     * clearing an empty session does nothing either.
     */
    @Override
    public void stop() {
        camera.stop();
        api.logout();
    }
}
