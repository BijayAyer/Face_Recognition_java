package com.fras.ui;

import com.fras.app.dto.ClassroomRow;
import com.fras.app.dto.SubjectRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.service.ApiService;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/**
 * Which room, and which subject.
 *
 * <p>Every screen that needed a session used to ask for two numbers in
 * free-text boxes and answer "Classroom ID and Subject ID must both be numbers"
 * when they were not - an error message that is really an admission that the
 * client never offered the choice in the first place. The ids exist on the
 * server; there is no reason for a person to be the one holding them.
 */
public final class SessionPicker {

    private final ApiService api;

    private final ComboBox<ClassroomRow> classrooms = new ComboBox<>();
    private final ComboBox<SubjectRow> subjects = new ComboBox<>();

    private boolean loaded;

    public SessionPicker(ApiService api) {
        this.api = api;
        prepare(classrooms, "Choose a room", ClassroomRow::label, 240);
        prepare(subjects, "Choose a subject", SubjectRow::label, 260);
    }

    /** Side by side, for a full-width toolbar. */
    public HBox asRow() {
        HBox row = new HBox(Ui.GAP, labelled("Classroom", classrooms), labelled("Subject", subjects));
        return row;
    }

    /** Stacked, for a narrow side column. */
    public VBox asColumn() {
        return Ui.column(Ui.GAP, labelled("Classroom", classrooms), labelled("Subject", subjects));
    }

    private VBox labelled(String label, ComboBox<?> box) {
        return Ui.column(5, Ui.fieldLabel(label), box);
    }

    private <T> void prepare(ComboBox<T> box, String prompt, Function<T, String> label, double width) {
        box.setPromptText(prompt);
        box.setPrefWidth(width);
        box.setPlaceholder(Ui.hint("Nothing set up yet - see Academic setup."));
        box.setButtonCell(cell(label));
        box.setCellFactory(ignored -> cell(label));
    }

    private <T> ListCell<T> cell(Function<T, String> label) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : label.apply(item));
            }
        };
    }

    /**
     * Fills both lists. Safe to call on every visit; the lists are only fetched
     * once unless {@code force} is set, so returning to a page does not throw
     * away a choice already made.
     */
    public void load(boolean force) {
        if (loaded && !force) {
            return;
        }
        loaded = true;

        Async.run(() -> JsonTableUtil.parseList(api.getClassrooms(), ClassroomRow.class),
                rows -> keepSelection(classrooms, rows, ClassroomRow::getId),
                Toast::error);

        Async.run(() -> JsonTableUtil.parseList(api.getSubjects(null), SubjectRow.class),
                rows -> keepSelection(subjects, rows, SubjectRow::getId),
                Toast::error);
    }

    private <T> void keepSelection(ComboBox<T> box, java.util.List<T> rows, Function<T, Long> id) {
        T before = box.getValue();
        box.getItems().setAll(rows);
        if (before == null) {
            return;
        }
        Long wanted = id.apply(before);
        for (T row : rows) {
            if (wanted != null && wanted.equals(id.apply(row))) {
                box.setValue(row);
                return;
            }
        }
    }

    public Long classroomId() {
        ClassroomRow row = classrooms.getValue();
        return row == null ? null : row.getId();
    }

    public Long subjectId() {
        SubjectRow row = subjects.getValue();
        return row == null ? null : row.getId();
    }

    /** What the session is, for a log line or a file name. */
    public String describe() {
        ClassroomRow room = classrooms.getValue();
        SubjectRow subject = subjects.getValue();
        if (room == null || subject == null) {
            return "no session";
        }
        return subject.label() + " in " + room.label();
    }

    public boolean isReady() {
        return classroomId() != null && subjectId() != null;
    }

    /** Fires whenever either choice changes, so a button can enable itself. */
    public void onChange(Runnable action) {
        ChangeListener<Object> listener = (observable, before, after) -> action.run();
        classrooms.valueProperty().addListener(listener);
        subjects.valueProperty().addListener(listener);
    }

    /** Locks both lists while a session is running. */
    public void lock(boolean locked) {
        Ui.busy(locked, classrooms, subjects);
    }
}
