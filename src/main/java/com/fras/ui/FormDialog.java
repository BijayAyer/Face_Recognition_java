package com.fras.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * A small modal form built from {@link Field}s.
 *
 * <p>The two "add" dialogs in the old client each rebuilt this by hand, and
 * both did the same two things wrong: a blank name silently consumed the click
 * with no explanation, and the network call was fired from inside the button
 * filter so the dialog closed before anyone knew whether it had worked. Here
 * validation is per-field and visible, the dialog stays open and quiet while
 * the request is in flight, and it closes only once the server has agreed.
 */
public final class FormDialog {

    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox body = new VBox(Ui.GAP);
    private final List<Field> fields = new ArrayList<>();
    private final ButtonType saveType;
    private final Button saveButton;

    private boolean working;

    public FormDialog(Window owner, String heading, String purpose, String saveLabel) {
        dialog.initOwner(owner);
        dialog.setTitle(heading);
        dialog.setHeaderText(heading);

        if (purpose != null && !purpose.isBlank()) {
            Label note = Ui.hint(purpose);
            note.setWrapText(true);
            note.setMaxWidth(320);
            body.getChildren().add(note);
        }

        body.setPrefWidth(340);
        dialog.getDialogPane().setContent(body);

        saveType = new ButtonType(saveLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType);
        Theme.applyTo(dialog.getDialogPane());

        saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.getStyleClass().add("button-primary");
    }

    /** Adds a field and returns it, so the caller can read it back later. */
    public Field add(Field field) {
        fields.add(field);
        body.getChildren().add(field);
        return field;
    }

    /**
     * Shows the form and keeps showing it until the work succeeds or the person
     * cancels.
     *
     * @param validate  reports problems on the fields; false keeps the form open
     * @param work      the request, run off the FX thread
     * @param onSuccess run on the FX thread after the dialog closes
     */
    public void showAndSave(Validation validate, Async.Job work, Runnable onSuccess) {
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            // Always consume: this dialog closes itself once the server agrees,
            // never on the click alone.
            event.consume();

            if (working || !validate.check()) {
                return;
            }

            working = true;
            Ui.busy(true, saveButton, body);
            Async.run(work,
                    () -> {
                        working = false;
                        Ui.busy(false, saveButton, body);
                        dialog.setResult(null);
                        dialog.close();
                        onSuccess.run();
                    },
                    message -> {
                        working = false;
                        Ui.busy(false, saveButton, body);
                        Toast.error(message);
                    });
        });

        if (!fields.isEmpty()) {
            javafx.application.Platform.runLater(fields.get(0)::focus);
        }
        dialog.showAndWait();
    }

    /** Convenience for the common "every one of these is required" case. */
    public static boolean requireAll(Field... required) {
        boolean ok = true;
        Field first = null;
        for (Field field : required) {
            if (!field.require("This cannot be empty.")) {
                ok = false;
                first = first == null ? field : first;
            }
        }
        if (first != null) {
            first.focus();
        }
        return ok;
    }

    /** Reports problems on fields and answers whether the form can be sent. */
    @FunctionalInterface
    public interface Validation {
        boolean check();
    }
}
