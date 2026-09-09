package com.fras.ui;

import com.fras.service.ApiService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The window everything logged-in lives in: a navigation rail on the left, one
 * page at a time on the right.
 *
 * <p>The important difference from what this replaces is that there is one
 * scene for the whole session. Navigating swaps a node, not a scene, so the
 * window keeps its size, the theme keeps its state, and a page that owns
 * something expensive - the camera, above all - is told when it goes away
 * instead of every caller having to remember to shut it down.
 */
public final class AppShell extends StackPane {

    /** Cmd/Ctrl + 1..9 jumps straight to a page. */
    private static final KeyCode[] DIGITS = {
            KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5,
            KeyCode.DIGIT6, KeyCode.DIGIT7, KeyCode.DIGIT8, KeyCode.DIGIT9
    };

    /**
     * How the shortcut modifier is written on screen. {@code SHORTCUT_DOWN} is
     * already Command on macOS and Control everywhere else; this is only the
     * label for it, so what is printed matches what is pressed.
     */
    private static final String MODIFIER =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                    ? "Cmd " : "Ctrl ";

    private final ApiService api;
    private final Runnable onSignOut;

    private final VBox rail = new VBox();
    private final VBox navGroups = new VBox(4);
    private final StackPane host = new StackPane();
    private final Button themeButton = Ui.ghost("Dark mode");

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private String currentKey;

    private static final class Entry {
        final Button button;
        final Supplier<Page> factory;
        Page page;

        Entry(Button button, Supplier<Page> factory) {
            this.button = button;
            this.factory = factory;
        }
    }

    public AppShell(ApiService api, Runnable onSignOut) {
        this.api = api;
        this.onSignOut = onSignOut;

        rail.getStyleClass().add("rail");
        rail.getChildren().addAll(brand(), navGroups, Ui.growV(), footer());

        host.getStyleClass().add("content-host");

        BorderPane frame = new BorderPane();
        frame.setLeft(rail);
        frame.setCenter(host);

        getChildren().add(frame);
        Toast.attach(this);

        // Shortcuts are installed on the scene, which does not exist yet when
        // the shell is constructed.
        sceneProperty().addListener((observable, before, after) -> {
            if (after != null) {
                installShortcuts(after);
            }
        });
    }

    private VBox brand() {
        VBox box = new VBox(1,
                Ui.styledLabel("FRAS", "rail-mark"),
                Ui.styledLabel("attendance by recognition", "rail-mark-sub"));
        box.setPadding(new Insets(2, 0, 20, 0));
        return box;
    }

    private VBox footer() {
        Label who = Ui.styledLabel(api.getDisplayName(), "rail-user");
        who.setWrapText(true);

        String role = api.getRole();
        Label what = Ui.styledLabel(
                role == null || role.isBlank() ? "signed in" : role.toLowerCase(Locale.ROOT),
                "rail-role");

        syncThemeButton();
        themeButton.setMaxWidth(Double.MAX_VALUE);
        themeButton.setOnAction(event -> {
            Theme.toggle(getScene());
            syncThemeButton();
        });

        Button signOut = Ui.ghost("Sign out");
        signOut.setMaxWidth(Double.MAX_VALUE);
        signOut.setOnAction(event -> signOut());

        VBox box = new VBox(8, new VBox(1, who, what), Ui.divider(), themeButton, signOut);
        box.getStyleClass().add("rail-footer");
        return box;
    }

    /** The button says what it will do, not what mode you are in. */
    private void syncThemeButton() {
        themeButton.setText(Theme.isDark() ? "Light mode" : "Dark mode");
    }

    // =========================================================
    // BUILDING THE RAIL
    // =========================================================

    /** A heading over the entries that follow, so the rail reads in sections. */
    public void group(String label) {
        navGroups.getChildren().add(Ui.styledLabel(label, "rail-group"));
    }

    /**
     * Adds a page. The factory is not called until the page is first opened, so
     * starting the app does not mean building seven screens and firing off
     * seven requests for data nobody has asked to see yet.
     */
    public void add(String key, String label, Supplier<Page> factory) {
        Button button = new Button(label);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> show(key));

        entries.put(key, new Entry(button, factory));
        navGroups.getChildren().add(button);
    }

    /** The key of the first page added, for opening the app on it. */
    public String firstKey() {
        return entries.isEmpty() ? null : entries.keySet().iterator().next();
    }

    /**
     * The keystroke that opens a page, spelled the way this platform spells it,
     * or {@code null} for pages past the ninth. Screens that offer their own
     * jump-off buttons print this next to them, so the keyboard is learned from
     * the interface rather than from a manual nobody reads.
     */
    public String shortcutFor(String key) {
        int index = 0;
        for (String each : entries.keySet()) {
            if (each.equals(key)) {
                return index < DIGITS.length ? MODIFIER + (index + 1) : null;
            }
            index++;
        }
        return null;
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    public void show(String key) {
        Entry entry = entries.get(key);
        if (entry == null || key.equals(currentKey)) {
            return;
        }

        // Build the new page before letting go of the old one: if it cannot be
        // built, the screen that works stays on screen.
        Node body;
        try {
            if (entry.page == null) {
                entry.page = entry.factory.get();
            }
            body = entry.page.view();
        } catch (RuntimeException failure) {
            entry.page = null;
            Toast.error("That screen could not be opened. " + Async.describe(failure));
            return;
        }

        Entry leaving = currentKey == null ? null : entries.get(currentKey);
        if (leaving != null && leaving.page != null) {
            leaving.page.onHide();
        }

        currentKey = key;
        for (Map.Entry<String, Entry> each : entries.entrySet()) {
            markActive(each.getValue().button, each.getKey().equals(key));
        }

        host.getChildren().setAll(body);
        entry.page.onShow();
    }

    private static void markActive(Button button, boolean active) {
        if (active) {
            if (!button.getStyleClass().contains("nav-button-active")) {
                button.getStyleClass().add("nav-button-active");
            }
        } else {
            button.getStyleClass().remove("nav-button-active");
        }
    }

    /**
     * Lets go of whatever the visible page is holding - the camera, in
     * practice. Called before signing out and before the window closes.
     */
    public void releaseCurrent() {
        Entry entry = currentKey == null ? null : entries.get(currentKey);
        if (entry != null && entry.page != null) {
            entry.page.onHide();
        }
    }

    private void signOut() {
        boolean go = Ui.confirm(getScene() == null ? null : getScene().getWindow(),
                "Sign out",
                "You will need to sign in again before you can record attendance.",
                "Sign out");
        if (!go) {
            return;
        }
        releaseCurrent();
        onSignOut.run();
    }

    // =========================================================
    // KEYBOARD
    // =========================================================

    /**
     * Cmd/Ctrl + 1..9 opens the first nine pages in rail order, and
     * Cmd/Ctrl + D flips the theme. Nine is not a limit anybody will hit here;
     * it is where single keystrokes run out.
     *
     * <p>Accelerators live on the scene, so this runs when the shell is added
     * to one rather than in the constructor.
     */
    private void installShortcuts(Scene scene) {
        List<String> keys = new ArrayList<>(entries.keySet());
        for (int index = 0; index < keys.size() && index < DIGITS.length; index++) {
            String key = keys.get(index);
            scene.getAccelerators().put(
                    new KeyCodeCombination(DIGITS[index], KeyCombination.SHORTCUT_DOWN),
                    () -> show(key));
        }

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN),
                () -> {
                    Theme.toggle(scene);
                    syncThemeButton();
                });
    }
}
