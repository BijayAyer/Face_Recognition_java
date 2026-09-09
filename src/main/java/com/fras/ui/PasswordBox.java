package com.fras.ui;

import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/**
 * A password box you can look inside.
 *
 * <p>Typing a password you cannot see, into a form that then tells you it was
 * wrong, is the most common reason a person fails to sign in to something they
 * have the credentials for. A masked {@link PasswordField} and a plain
 * {@link TextField} are kept in sync and swapped by one button, so the
 * characters are checkable without retyping them.
 *
 * <p>This is an {@link HBox} rather than a {@code Control}: it wears the
 * text-field chrome itself (see {@code .password-box} in app.css) and the two
 * inputs inside it are transparent, so the border and focus ring belong to the
 * whole widget instead of jumping between two different-looking boxes.
 */
public final class PasswordBox extends HBox {

    private final PasswordField masked = new PasswordField();
    private final TextField plain = new TextField();
    private final Button toggle;

    private boolean revealed;

    public PasswordBox(String prompt, double width) {
        super(0);
        getStyleClass().add("password-box");
        setAlignment(Pos.CENTER_LEFT);
        if (width > 0) {
            setPrefWidth(width);
            setMaxWidth(width);
        }

        masked.setPromptText(prompt);
        plain.setPromptText(prompt);
        masked.getStyleClass().add("password-box-inner");
        plain.getStyleClass().add("password-box-inner");

        // One value, two views. Bidirectional so a keystroke in either lands in
        // both and the swap never loses what was typed.
        plain.textProperty().bindBidirectional(masked.textProperty());

        plain.setVisible(false);
        plain.setManaged(false);

        StackPane both = new StackPane(masked, plain);
        both.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(both, Priority.ALWAYS);

        toggle = new Button("Show");
        toggle.getStyleClass().add("password-toggle");
        toggle.setAccessibleText("Show password");
        toggle.setOnAction(event -> setRevealed(!revealed));

        getChildren().addAll(both, toggle);
    }

    /** Whichever of the two inputs is currently on screen. */
    private TextField active() {
        return revealed ? plain : masked;
    }

    public void setRevealed(boolean reveal) {
        applyRevealed(reveal, true);
    }

    private void applyRevealed(boolean reveal, boolean moveFocus) {
        revealed = reveal;

        masked.setVisible(!reveal);
        masked.setManaged(!reveal);
        plain.setVisible(reveal);
        plain.setManaged(reveal);

        toggle.setText(reveal ? "Hide" : "Show");
        toggle.setAccessibleText(reveal ? "Hide password" : "Show password");

        if (moveFocus) {
            // The swap replaces the node that had the caret, so put it back
            // where it was rather than at the start of the text.
            TextField now = active();
            int caret = now.getText() == null ? 0 : now.getText().length();
            now.requestFocus();
            now.positionCaret(caret);
        }
    }

    public String getText() {
        String value = masked.getText();
        return value == null ? "" : value;
    }

    public void setText(String value) {
        masked.setText(value == null ? "" : value);
    }

    public StringProperty textProperty() {
        return masked.textProperty();
    }

    /** Empties the box and re-masks it, without grabbing focus. */
    public void clear() {
        masked.clear();
        applyRevealed(false, false);
    }

    /** Enter submits, from either view. */
    public void setOnAction(EventHandler<ActionEvent> handler) {
        masked.setOnAction(handler);
        plain.setOnAction(handler);
    }

    @Override
    public void requestFocus() {
        active().requestFocus();
    }
}
