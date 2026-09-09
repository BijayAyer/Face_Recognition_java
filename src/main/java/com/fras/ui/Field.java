package com.fras.ui;

import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A label, an input, and the one line that says what is wrong with it.
 *
 * <p>Validation used to be a single shared status label at the bottom of a
 * form, so "please enter your email" appeared a long way from the email box
 * and only one problem could be reported at a time. Here each field carries
 * its own message and its own thickened border, and the message says what to do
 * rather than what failed.
 *
 * <p>The border is thicker rather than a different colour because the
 * stylesheet has no hue in it: the focus ring is already the darkest ink on the
 * page, so a rejected field cannot be "the dark-bordered one" and is the
 * heavy-bordered one instead. Either way the border was never the whole signal
 * - the sentence underneath it is.
 */
public final class Field extends VBox {

    /**
     * How wide the help or rejection line is allowed to get before it wraps.
     * Matches the widest input these forms use, so the message reads as
     * belonging to the box above it.
     */
    private static final double MESSAGE_WIDTH = 300;

    private final Region control;
    private final Label message;
    private final String help;

    public Field(String label, Region control) {
        this(label, control, null);
    }

    public Field(String label, Region control, String help) {
        super(5);
        this.control = control;
        this.help = help;
        this.message = Ui.hint(help == null ? "" : help);
        this.message.getStyleClass().add("field-help");
        // A sentence long enough to be worth saying is longer than a form is
        // wide. Without this it was cut off mid-word - which is how "Teacher and
        // administrator accounts need a registration code" ended at "need a".
        // The bound width also stops a long message widening the whole dialog.
        this.message.setWrapText(true);
        this.message.setMaxWidth(MESSAGE_WIDTH);
        getChildren().addAll(Ui.fieldLabel(label), control);
        if (help != null && !help.isBlank()) {
            getChildren().add(message);
        }
        healOnEdit();
    }

    /**
     * Drops the rejection the moment the person starts fixing the field. An
     * error that stays on screen while you are visibly correcting it reads as
     * the app not keeping up.
     */
    private void healOnEdit() {
        javafx.beans.value.ChangeListener<String> heal = (observable, before, after) -> {
            if (control.getStyleClass().contains("field-invalid")) {
                accept();
            }
        };
        if (control instanceof TextInputControl input) {
            input.textProperty().addListener(heal);
        } else if (control instanceof PasswordBox box) {
            box.textProperty().addListener(heal);
        }
    }

    public Region control() {
        return control;
    }

    /** Trimmed contents, or "" for anything that is not a text input. */
    public String text() {
        if (control instanceof TextInputControl input) {
            String value = input.getText();
            return value == null ? "" : value.trim();
        }
        if (control instanceof PasswordBox box) {
            return box.getText();
        }
        return "";
    }

    /** Raw contents - passwords must not be trimmed. */
    public String raw() {
        if (control instanceof PasswordBox box) {
            return box.getText();
        }
        if (control instanceof TextInputControl input) {
            String value = input.getText();
            return value == null ? "" : value;
        }
        return "";
    }

    public boolean isEmpty() {
        return raw().isEmpty();
    }

    /** Marks the field wrong and says what to do about it. */
    public void reject(String reason) {
        if (!control.getStyleClass().contains("field-invalid")) {
            control.getStyleClass().add("field-invalid");
        }
        message.setText(reason);
        message.getStyleClass().remove("hint-text");
        if (!message.getStyleClass().contains("field-error")) {
            message.getStyleClass().add("field-error");
        }
        if (!getChildren().contains(message)) {
            getChildren().add(message);
        }
    }

    /** Clears the rejection, restoring the original help text if there was one. */
    public void accept() {
        control.getStyleClass().remove("field-invalid");
        message.getStyleClass().remove("field-error");
        if (!message.getStyleClass().contains("hint-text")) {
            message.getStyleClass().add("hint-text");
        }
        if (help == null || help.isBlank()) {
            getChildren().remove(message);
            message.setText("");
        } else {
            message.setText(help);
        }
    }

    public void focus() {
        control.requestFocus();
    }

    /** Rejects when blank; returns true when the field is usable. */
    public boolean require(String reason) {
        if (isEmpty()) {
            reject(reason);
            return false;
        }
        accept();
        return true;
    }

    public void clear() {
        if (control instanceof TextInputControl input) {
            input.clear();
        } else if (control instanceof PasswordBox box) {
            box.clear();
        }
        accept();
    }
}
