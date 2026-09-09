package com.fras.ui;

import com.fras.service.ApiService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Sign in, or create an account.
 *
 * <p>There used to be no way to create an account at all: the only route in
 * was a row someone had already put in the database. Registration is now part
 * of the client, and both halves of the job live on one screen because they
 * are the same decision - "do I already have an account here?" - and switching
 * between them should not feel like navigating.
 *
 * <p>The layout is a split rather than a card floating on a gradient. The dark
 * half says what the product does, in the three steps it actually works in;
 * the light half is nothing but the form. Each field reports its own problem
 * under itself, the password can be read back before it is submitted, and the
 * address of the server is printed at the bottom, so "it will not let me in"
 * and "it cannot reach the server" are told apart without opening a log.
 */
public final class AuthScreen extends StackPane {

    /** Same node as {@link Theme}: one place for this client's preferences. */
    private static final Preferences PREFS = Preferences.userRoot().node("com/fras/ui");

    private static final String REMEMBER_KEY = "auth.email";

    private final ApiService api;
    private final Consumer<ApiService.LoginResult> onSignedIn;

    private boolean signUp;
    private boolean working;

    private final Label heading = Ui.styledLabel("Welcome back", "auth-heading");
    private final Label subheading = Ui.hint("");

    private final Button signInTab = tab("Sign in");
    private final Button signUpTab = tab("Create account");

    private final Label noticeText = Ui.styledLabel("", "auth-notice-text");
    private final HBox notice = new HBox(noticeText);

    private final VBox fields = new VBox(Ui.GAP);

    private final Button submit = Ui.primary("Sign in");
    private final ProgressIndicator spinner = new ProgressIndicator();

    private final Field nameField =
            new Field("Full name", Ui.input("Ada Lovelace", 300));
    private final Field emailField =
            new Field("Email address", Ui.input("you@school.edu", 300));
    private final Field passwordField =
            new Field("Password", new PasswordBox("Your password", 0));
    private final Field confirmField =
            new Field("Confirm password", new PasswordBox("Type it again", 0));
    private final ComboBox<String> roleChoice = new ComboBox<>();
    private final Field roleField;
    private final Field codeField;

    private final CheckBox remember = new CheckBox("Remember my email on this computer");

    public AuthScreen(ApiService api, Consumer<ApiService.LoginResult> onSignedIn) {
        this.api = api;
        this.onSignedIn = onSignedIn;

        roleChoice.getItems().addAll("Student", "Teacher", "Administrator");
        roleChoice.setValue("Student");
        roleChoice.setMaxWidth(Double.MAX_VALUE);
        roleField = new Field("This account is for", roleChoice,
                "Teacher and administrator accounts need a registration code. "
                        + "Without one, ask an administrator to create the account for you.");
        codeField = new Field("Registration code",
                Ui.input("Leave blank for a student account", 300),
                "Only needed for a teacher or administrator account.");

        notice.getStyleClass().add("auth-notice");
        notice.setAlignment(Pos.CENTER_LEFT);
        noticeText.setWrapText(true);
        hideNotice();

        spinner.setPrefSize(18, 18);
        spinner.setMaxSize(18, 18);
        show(spinner, false);

        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setOnAction(event -> attempt());
        HBox.setHgrow(submit, Priority.ALWAYS);

        signInTab.setOnAction(event -> mode(false));
        signUpTab.setOnAction(event -> mode(true));

        String remembered = PREFS.get(REMEMBER_KEY, "");
        if (!remembered.isBlank() && emailField.control() instanceof TextField input) {
            input.setText(remembered);
            remember.setSelected(true);
        }

        getStyleClass().add("auth-root");
        HBox split = new HBox(brandPanel(), formPanel());
        split.setFillHeight(true);
        getChildren().add(split);

        mode(false);
    }

    private static Button tab(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("auth-switch-button");
        return button;
    }

    // =========================================================
    // THE DARK HALF - what this thing is
    // =========================================================

    /**
     * The product in the three steps it actually works in. They are numbered
     * because they are a real sequence - a session cannot recognise a face that
     * was never enrolled - and not because numbers look tidy.
     */
    private VBox brandPanel() {
        VBox panel = new VBox(Ui.GAP);
        panel.getStyleClass().add("auth-brand-panel");
        panel.setMinWidth(360);
        panel.setPrefWidth(360);
        panel.setMaxWidth(360);

        Region rule = new Region();
        rule.getStyleClass().add("auth-brand-rule");

        Label line = Ui.styledLabel(
                "Face-recognition attendance for classrooms.", "auth-brand-line");
        line.setWrapText(true);

        VBox steps = new VBox(16,
                step("01", "Enrol a face",
                        "One short capture per student, kept as a template rather than a photograph."),
                step("02", "Open a session",
                        "Point the camera at the room. Students are marked as they are recognised."),
                step("03", "Take the register",
                        "Export the day as Excel or PDF, by class and by subject."));

        Label foot = Ui.styledLabel(
                "Attendance is always recorded against a signed-in account.",
                "auth-brand-foot");
        foot.setWrapText(true);

        panel.getChildren().addAll(
                Ui.styledLabel("FRAS", "auth-brand-mark"),
                rule,
                line,
                Ui.growV(),
                steps,
                Ui.growV(),
                foot);
        return panel;
    }

    private HBox step(String index, String title, String body) {
        Label bodyLabel = Ui.styledLabel(body, "auth-brand-step-text");
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxWidth(232);

        VBox text = new VBox(3, Ui.styledLabel(title, "auth-brand-step-title"), bodyLabel);
        HBox row = new HBox(13, Ui.styledLabel(index, "auth-brand-step"), text);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    // =========================================================
    // THE LIGHT HALF - the form
    // =========================================================

    private Region formPanel() {
        HBox tabs = new HBox(signInTab, signUpTab);
        tabs.getStyleClass().add("auth-switch");
        tabs.setAlignment(Pos.CENTER_LEFT);
        tabs.setMaxWidth(Region.USE_PREF_SIZE);

        HBox action = new HBox(10, submit, spinner);
        action.setAlignment(Pos.CENTER_LEFT);

        VBox column = new VBox(Ui.GAP,
                new VBox(4, heading, subheading),
                tabs,
                notice,
                fields,
                action,
                Ui.styledLabel("server  " + ApiService.baseUrl(), "auth-endpoint"));
        column.setMaxWidth(400);

        // Centred vertically, and scrollable rather than clipped: the sign-up
        // form is four fields taller than the sign-in form and the window is
        // not always going to be tall enough for it.
        VBox holder = new VBox(column);
        holder.setAlignment(Pos.CENTER);

        ScrollPane scroller = new ScrollPane(holder);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setFocusTraversable(false);

        VBox panel = new VBox(scroller);
        panel.getStyleClass().add("auth-form-panel");
        VBox.setVgrow(scroller, Priority.ALWAYS);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    // =========================================================
    // SWITCHING BETWEEN THE TWO
    // =========================================================

    private void mode(boolean toSignUp) {
        if (working) {
            return;
        }
        signUp = toSignUp;

        heading.setText(signUp ? "Create an account" : "Welcome back");
        subheading.setText(signUp
                ? "Signing up signs you in as well, so this is the only time you fill it in."
                : "Sign in to record attendance and open the register.");

        activeTab(signInTab, !signUp);
        activeTab(signUpTab, signUp);
        submit.setText(signUp ? "Create account" : "Sign in");

        fields.getChildren().clear();
        if (signUp) {
            fields.getChildren().addAll(
                    nameField, emailField, passwordField, confirmField, roleField, codeField);
        } else {
            fields.getChildren().addAll(emailField, passwordField, remember);
        }

        nameField.accept();
        emailField.accept();
        passwordField.accept();
        confirmField.accept();
        roleField.accept();
        codeField.accept();
        hideNotice();

        Platform.runLater(this::focusFirst);
    }

    private static void activeTab(Button tab, boolean on) {
        if (on) {
            if (!tab.getStyleClass().contains("auth-switch-button-active")) {
                tab.getStyleClass().add("auth-switch-button-active");
            }
        } else {
            tab.getStyleClass().remove("auth-switch-button-active");
        }
    }

    /** Puts the caret in the first thing still needing an answer. */
    public void focusFirst() {
        if (signUp) {
            nameField.focus();
        } else if (emailField.isEmpty()) {
            emailField.focus();
        } else {
            passwordField.focus();
        }
    }

    // =========================================================
    // THE ONE-LINE ANSWER AT THE TOP OF THE FORM
    // =========================================================

    /** Something went wrong, and the sign-in screen is back because of it. */
    public void showProblem(String message) {
        fail(message);
    }

    /** A neutral or good outcome - being signed out is not a failure. */
    public void showMessage(String message) {
        inform(message);
    }

    private void fail(String message) {
        notice.getStyleClass().remove("auth-notice-ok");
        noticeText.setText(message);
        show(notice, true);
    }

    private void inform(String message) {
        if (!notice.getStyleClass().contains("auth-notice-ok")) {
            notice.getStyleClass().add("auth-notice-ok");
        }
        noticeText.setText(message);
        show(notice, true);
    }

    private void hideNotice() {
        noticeText.setText("");
        show(notice, false);
    }

    /** Hidden and taking no space, rather than hidden and leaving a gap. */
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    /**
     * Reports everything wrong with the form at once and sends the caret to the
     * first problem. Checking one rule per attempt turns a form with three
     * mistakes in it into three round trips to the server.
     */
    private boolean formIsUsable() {
        boolean ok = true;
        Field first = null;

        if (signUp && !nameField.require("Enter the name this account belongs to.")) {
            ok = false;
            first = nameField;
        }

        if (!emailField.require("Enter your email address.")) {
            ok = false;
            first = first == null ? emailField : first;
        } else if (!looksLikeEmail(emailField.text())) {
            emailField.reject("That does not look like an email address.");
            ok = false;
            first = first == null ? emailField : first;
        }

        if (!passwordField.require("Enter your password.")) {
            ok = false;
            first = first == null ? passwordField : first;
        } else if (signUp && passwordField.raw().length() < 8) {
            passwordField.reject("Use at least 8 characters.");
            ok = false;
            first = first == null ? passwordField : first;
        }

        if (signUp) {
            if (!confirmField.require("Type the password a second time.")) {
                ok = false;
                first = first == null ? confirmField : first;
            } else if (!confirmField.raw().equals(passwordField.raw())) {
                confirmField.reject("The two passwords are different.");
                ok = false;
                first = first == null ? confirmField : first;
            }

            // The server refuses a teacher or administrator sign-up without a
            // valid code, so submitting a blank one can only fail. Saying so
            // here names the field that is wrong, which a 403 cannot.
            if (!"STUDENT".equals(selectedRole()) && codeField.text().isEmpty()) {
                codeField.reject("A registration code is needed for this kind of account."
                        + " Choose Student, or ask an administrator to create the account.");
                ok = false;
                first = first == null ? codeField : first;
            }
        }

        if (!ok && first != null) {
            first.focus();
        }
        return ok;
    }

    /**
     * Deliberately loose. A client that enforces its own idea of a valid
     * address ends up rejecting real ones, so this only catches shapes that
     * cannot reach anybody at all; the server has the last word.
     */
    private static boolean looksLikeEmail(String value) {
        int at = value.indexOf('@');
        int dot = value.lastIndexOf('.');
        return at > 0
                && dot > at + 1
                && dot < value.length() - 1
                && value.indexOf(' ') < 0
                && value.indexOf('@', at + 1) < 0;
    }

    // =========================================================
    // SUBMIT
    // =========================================================

    private void attempt() {
        if (working || !formIsUsable()) {
            return;
        }
        hideNotice();

        boolean creating = signUp;
        String email = emailField.text();
        String password = passwordField.raw();
        String name = nameField.text();
        String role = selectedRole();
        String code = codeField.text();

        busy(true);
        Async.run(
                () -> creating
                        ? api.register(name, email, password, role, code)
                        : api.login(email, password),
                result -> finish(result, creating, email),
                message -> {
                    busy(false);
                    fail(message);
                });
    }

    private void finish(ApiService.LoginResult result, boolean creating, String email) {
        busy(false);
        if (!result.isSuccess()) {
            fail(result.getMessage());
            passwordField.focus();
            return;
        }

        rememberEmail(email, creating);

        // No mismatch case to report any more. Sign-up used to be answered with
        // a lesser role than the one asked for - a teacher sign-up quietly
        // became a student account - and this is where that had to be explained
        // after the fact. The server now refuses instead, which arrives as a
        // failure on the form with the reason next to the field.
        Toast.ok(creating
                ? "Account created. Signed in as " + api.getDisplayName() + "."
                : "Signed in as " + api.getDisplayName() + ".");
        onSignedIn.accept(result);
    }

    /** Which account the sign-up form is asking for, in the server's words. */
    private String selectedRole() {
        String choice = roleChoice.getValue();
        if ("Administrator".equals(choice)) {
            return "ADMIN";
        }
        if ("Teacher".equals(choice)) {
            return "TEACHER";
        }
        return "STUDENT";
    }

    /** The address only, never the password. */
    private void rememberEmail(String email, boolean creating) {
        try {
            if (creating || remember.isSelected()) {
                PREFS.put(REMEMBER_KEY, email);
            } else {
                PREFS.remove(REMEMBER_KEY);
            }
        } catch (RuntimeException ignored) {
            // A preference store that will not take a value is not a reason to
            // fail a sign-in that already worked.
        }
    }

    /**
     * The whole form goes quiet while a request is in flight, and the button
     * says what is happening. A form that stays live during the round trip is
     * how you end up submitting it twice.
     */
    private void busy(boolean busy) {
        working = busy;
        show(spinner, busy);
        if (busy) {
            submit.setText(signUp ? "Creating account..." : "Signing in...");
        } else {
            submit.setText(signUp ? "Create account" : "Sign in");
        }
        Ui.busy(busy, submit, signInTab, signUpTab, fields, remember);
    }
}
