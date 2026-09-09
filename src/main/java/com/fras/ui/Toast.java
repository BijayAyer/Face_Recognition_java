package com.fras.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Transient confirmations, stacked in the bottom-right corner.
 *
 * <p>Before this, every result - "student created", "export saved", "that
 * email is already in use" - was written into a status label somewhere on the
 * page, which meant the outcome of an action appeared nowhere near the action
 * and stayed on screen until something else overwrote it. Toasts announce
 * what happened, then get out of the way. Errors linger longer than successes
 * because they are the ones worth reading twice.
 *
 * <p>Identical messages are not stacked. Several requests can fail at once for
 * one reason - Academic Setup opens five tabs and fires nine reads, and an
 * unreachable server fails all nine with the same sentence - and nine copies of
 * it says no more than one does. A repeat restarts the timer on the copy already
 * showing instead, so the message stays up while the failures keep arriving.
 */
public final class Toast {

    private static final Duration IN = Duration.millis(150);
    private static final Duration OUT = Duration.millis(180);
    private static final Duration HOLD_OK = Duration.seconds(3);
    private static final Duration HOLD_BAD = Duration.seconds(6);

    private static final int MAX_VISIBLE = 4;

    private static VBox stack;

    private Toast() {
    }

    /**
     * Installs the toast layer over an existing {@link StackPane}. Call once,
     * with the pane that hosts the whole window.
     */
    public static void attach(StackPane host) {
        if (host == null) {
            return;
        }
        VBox layer = new VBox(8);
        layer.getStyleClass().add("toast-layer");
        layer.setAlignment(Pos.BOTTOM_RIGHT);
        layer.setPickOnBounds(false);
        layer.setMouseTransparent(true);
        StackPane.setAlignment(layer, Pos.BOTTOM_RIGHT);
        host.getChildren().add(layer);
        stack = layer;
    }

    public static void ok(String message) {
        show(message, "toast-ok", HOLD_OK);
    }

    public static void info(String message) {
        show(message, null, HOLD_OK);
    }

    public static void warn(String message) {
        show(message, "toast-warn", HOLD_BAD);
    }

    public static void error(String message) {
        show(message, "toast-error", HOLD_BAD);
    }

    private static void show(String message, String variant, Duration hold) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(message, variant, hold));
            return;
        }
        VBox layer = stack;
        if (layer == null) {
            // No window yet (startup failure, say) - at least leave a trace.
            System.err.println("[toast] " + message);
            return;
        }

        if (restartExisting(layer, message)) {
            return;
        }

        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);
        text.setMaxWidth(340);

        HBox card = new HBox(text);
        card.getStyleClass().add("toast");
        if (variant != null) {
            card.getStyleClass().add(variant);
        }
        card.setMaxWidth(380);
        card.setOpacity(0);
        card.setTranslateY(14);

        while (layer.getChildren().size() >= MAX_VISIBLE) {
            layer.getChildren().remove(0);
        }
        layer.getChildren().add(card);

        FadeTransition fadeIn = new FadeTransition(IN, card);
        fadeIn.setToValue(1);
        TranslateTransition riseIn = new TranslateTransition(IN, card);
        riseIn.setToY(0);
        fadeIn.play();
        riseIn.play();

        PauseTransition wait = new PauseTransition(hold);
        Live live = new Live(message, wait);
        card.setUserData(live);
        wait.setOnFinished(event -> {
            live.leaving = true;
            FadeTransition fadeOut = new FadeTransition(OUT, card);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(done -> layer.getChildren().remove(card));
            fadeOut.play();
        });
        wait.play();
    }

    /**
     * Restarts the hold on a card already showing {@code message}, if there is
     * one.
     *
     * <p>Cards already fading out are skipped rather than revived: their removal
     * is scheduled and the fade cannot be called back, so reviving one would
     * leave it stuck half-transparent. Matching on {@code leaving} rather than on
     * opacity matters, because a burst of identical failures arrives inside the
     * 150ms fade-in, when the first card's opacity is still climbing.
     *
     * @return true when a card was found, meaning nothing new should be added
     */
    private static boolean restartExisting(VBox layer, String message) {
        for (Node node : layer.getChildren()) {
            if (node.getUserData() instanceof Live live
                    && !live.leaving
                    && live.message.equals(message)) {
                live.hold.playFromStart();
                return true;
            }
        }
        return false;
    }

    /** What a visible card is showing, and the timer that will take it away. */
    private static final class Live {

        private final String message;
        private final PauseTransition hold;

        /** Set once the fade-out has started, after which the card is not reusable. */
        private boolean leaving;

        private Live(String message, PauseTransition hold) {
            this.message = message;
            this.hold = hold;
        }
    }
}
