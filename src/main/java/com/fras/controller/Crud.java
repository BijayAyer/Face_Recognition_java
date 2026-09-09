package com.fras.controller;

import com.fras.ui.Async;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The shared machinery behind the five Academic Setup tabs.
 *
 * <p>It exists because those tabs had two faults in common, and both were
 * structural rather than local.
 *
 * <p><b>Every call was made on the JavaFX thread.</b> Each controller's
 * {@code initialize()} fetched its rows synchronously, and
 * {@link com.fras.ui.pages.AcademicPage} builds all five at once, so opening the
 * screen made nine HTTP requests in a row - each with a fifteen-second timeout -
 * on the thread that paints. A slow or unreachable server did not show an error;
 * it froze the whole window, for up to two and a half minutes. Every Add, Update
 * and Delete did the same thing again.
 *
 * <p><b>Nothing reported a failure.</b> The DAOs threw, no handler on the path
 * caught, and JavaFX's default handler printed a stack trace to a console nobody
 * running the app can see. Pressing Add with a duplicate department code was
 * enough: no row appeared, no message, nothing to do next.
 *
 * <p>So: {@link #read} and {@link #write} put the work on a background thread and
 * bring the outcome back as a toast, using the same {@link Async} pool and the
 * same wording conventions as the rest of the client. Package-private - this is
 * for the controllers beside it, not a general-purpose API.
 */
final class Crud {

    private Crud() {
    }

    /**
     * A read, off the FX thread, delivered back on it.
     *
     * <p>Failures are shown and the callback is not run, so a screen never fills
     * a table from a call that did not succeed.
     */
    static <T> void read(Callable<T> work, Consumer<T> onLoaded) {
        Async.run(work, onLoaded, Toast::error);
    }

    /**
     * A write, off the FX thread, with the buttons disabled while it is in
     * flight so it cannot be fired twice.
     *
     * @param onWritten runs on the FX thread, on success only
     */
    static void write(Async.Job work, Runnable onWritten, Node... buttons) {
        Ui.busy(true, buttons);
        Async.run(work,
                () -> {
                    Ui.busy(false, buttons);
                    onWritten.run();
                },
                message -> {
                    Ui.busy(false, buttons);
                    Toast.error(message);
                });
    }

    /**
     * Asks before deleting. Destructive and irreversible from the client's side,
     * and it used to happen on a single click of a button next to Update.
     *
     * @param inScene any node already in the window, used to own the dialog
     */
    static boolean confirmDelete(Node inScene, String what, String consequence) {
        return Ui.confirm(
                inScene == null || inScene.getScene() == null
                        ? null : inScene.getScene().getWindow(),
                "Delete " + what,
                consequence,
                "Delete");
    }

    /**
     * Replaces a combo's choices, keeping whatever was selected if it is still
     * one of them.
     *
     * <p>Matched by id rather than by object, which is the whole point. The
     * academic models have no {@code equals}, and a combo's items and the row
     * being edited arrive from two separate requests, so the department attached
     * to a semester is never the same object as the one in the department list
     * even when it is the same department. Selection by reference therefore never
     * matched, and refreshing a tab silently emptied a half-filled form.
     */
    static <T> void putItems(ComboBox<T> combo, List<T> items, Function<T, Long> idOf) {
        T before = combo.getValue();
        combo.setItems(FXCollections.observableArrayList(items));
        selectById(combo, before, idOf);
    }

    /**
     * Selects the combo's own copy of {@code wanted}, or nothing if it is no
     * longer on offer - which is honest: something deleted elsewhere should not
     * look selectable here.
     */
    static <T> void selectById(ComboBox<T> combo, T wanted, Function<T, Long> idOf) {
        Long id = wanted == null ? null : idOf.apply(wanted);
        if (id != null) {
            for (T item : combo.getItems()) {
                if (id.equals(idOf.apply(item))) {
                    combo.setValue(item);
                    return;
                }
            }
        }
        combo.setValue(null);
    }

    /**
     * What an empty table says while its first request is in flight. Without
     * this, an async load shows JavaFX's default "No content in table", which
     * reads as "there are none" during the second or two before there are.
     */
    static void loading(TableView<?> table) {
        table.setPlaceholder(new Label("Loading..."));
    }

    /** What an empty table says once a request has come back with no rows. */
    static void empty(TableView<?> table, String text) {
        table.setPlaceholder(new Label(text));
    }

    /** True when a field holds nothing but blanks. */
    static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** Lower-cased and never null, for case-insensitive matching. */
    static String lower(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
    }
}
