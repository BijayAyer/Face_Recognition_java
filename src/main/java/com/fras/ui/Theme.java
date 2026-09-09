package com.fras.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.prefs.Preferences;

/**
 * Stylesheet and light/dark mode.
 *
 * <p>Every scene in the app is decorated through {@link #apply(Scene)}, so
 * there is exactly one place that knows where the stylesheet lives. The
 * chosen mode is remembered between runs in the user's preference store,
 * which is per-user and needs no file of our own.
 *
 * <p>Both modes are the same greyscale ramp read in opposite directions -
 * there is no hue in the stylesheet at all. Only the page inverts: the
 * navigation rail, the sign-in panel, the viewfinder housing and the toasts
 * are near-black in both, which is why switching modes does not move the dark
 * edge of the window.
 */
public final class Theme {

    private static final String STYLESHEET = "/css/app.css";

    /**
     * Style class that flips the page tokens in app.css. The chassis tokens
     * ({@code -ink*}) are deliberately not among them.
     */
    private static final String DARK_CLASS = "theme-dark";

    private static final String PREF_KEY = "ui.theme";

    private static final Preferences PREFS =
            Preferences.userRoot().node("com/fras/ui");

    private static boolean dark = PREFS.get(PREF_KEY, "light").equals("dark");

    private Theme() {
    }

    public static boolean isDark() {
        return dark;
    }

    public static String stylesheet() {
        var url = Theme.class.getResource(STYLESHEET);
        if (url == null) {
            throw new IllegalStateException(
                    "Missing " + STYLESHEET + " - the app cannot be styled without it.");
        }
        return url.toExternalForm();
    }

    /** Adds the stylesheet once and puts the root in the current mode. */
    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        String sheet = stylesheet();
        if (!scene.getStylesheets().contains(sheet)) {
            scene.getStylesheets().add(sheet);
        }
        applyMode(scene.getRoot());
    }

    /** For dialog panes and other roots that live outside the main scene. */
    public static void applyTo(Parent root) {
        if (root == null) {
            return;
        }
        String sheet = stylesheet();
        if (!root.getStylesheets().contains(sheet)) {
            root.getStylesheets().add(sheet);
        }
        applyMode(root);
    }

    /**
     * Switches mode for the given scene and remembers the choice. Only the
     * root's style class changes, so nothing is rebuilt and no state is lost.
     */
    public static void toggle(Scene scene) {
        dark = !dark;
        PREFS.put(PREF_KEY, dark ? "dark" : "light");
        if (scene != null) {
            applyMode(scene.getRoot());
        }
    }

    private static void applyMode(Parent root) {
        if (root == null) {
            return;
        }
        if (dark) {
            if (!root.getStyleClass().contains(DARK_CLASS)) {
                root.getStyleClass().add(DARK_CLASS);
            }
        } else {
            root.getStyleClass().remove(DARK_CLASS);
        }
    }
}
