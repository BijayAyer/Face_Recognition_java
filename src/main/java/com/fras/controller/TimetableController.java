package com.fras.controller;

import com.fras.Refreshable;
import com.fras.model.Classroom;
import com.fras.model.Subject;
import com.fras.model.Timetable;
import com.fras.service.ClassroomService;
import com.fras.service.SubjectService;
import com.fras.service.TimetableService;
import com.fras.service.impl.ClassroomServiceImpl;
import com.fras.service.impl.SubjectServiceImpl;
import com.fras.service.impl.TimetableServiceImpl;
import com.fras.ui.Toast;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The timetable: a subject taught in a classroom on a weekday between two times.
 *
 * <p>Three requests fill this tab - classrooms, subjects and the timetable
 * itself - and every one of them ran on the JavaFX thread. See {@link Crud} for
 * why that is now off it.
 *
 * <p>Double-booking is refused by {@code TimetableServiceImpl}, which throws
 * {@link IllegalStateException} with a sentence written for a person.
 * {@link com.fras.ui.Async#describe} passes that message through unchanged, so
 * {@link Crud#write} reports it as a toast and this class needs no catch of its
 * own. The old code caught it around Add and Update but not around Delete, and
 * caught nothing else anywhere - so a server that refused the write for any
 * other reason said nothing at all.
 */
public class TimetableController implements Refreshable {

    /** The only accepted time format, in the field and in the table. */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private ComboBox<String> comboDay;
    @FXML private TextField txtStartTime;
    @FXML private TextField txtEndTime;
    @FXML private ComboBox<Classroom> comboClassroom;
    @FXML private ComboBox<Subject> comboSubject;

    @FXML private TableView<Timetable> timetableTable;
    @FXML private TableColumn<Timetable, Long> colId;
    @FXML private TableColumn<Timetable, String> colDay;
    @FXML private TableColumn<Timetable, String> colStartTime;
    @FXML private TableColumn<Timetable, String> colEndTime;
    @FXML private TableColumn<Timetable, String> colClassroom;
    @FXML private TableColumn<Timetable, String> colSubject;

    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnDelete;
    @FXML private Button btnClear;

    private final ClassroomService classroomService = new ClassroomServiceImpl();
    private final SubjectService subjectService = new SubjectServiceImpl();
    private final TimetableService timetableService = new TimetableServiceImpl();
    private final ObservableList<Timetable> rows = FXCollections.observableArrayList();

    private Timetable selected;

    @FXML
    public void initialize() {
        comboDay.setItems(FXCollections.observableArrayList(
                "Monday", "Tuesday", "Wednesday", "Thursday",
                "Friday", "Saturday", "Sunday"));
        comboClassroom.setConverter(classroomLabels());
        comboSubject.setConverter(subjectLabels());
        setupTableColumns();
        timetableTable.setItems(rows);
        timetableTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, before, after) -> {
                    if (after != null) {
                        selected = after;
                        populateForm(after);
                    }
                });
        loadClassrooms();
        loadSubjects();
        load();
    }

    /** Set once. The old version rebuilt both of these on every refresh. */
    private StringConverter<Classroom> classroomLabels() {
        return new StringConverter<>() {
            @Override
            public String toString(Classroom classroom) {
                return classroom == null
                        ? ""
                        : classroom.getRoomNumber() + " (" + classroom.getBuilding() + ")";
            }

            @Override
            public Classroom fromString(String text) {
                return null;
            }
        };
    }

    private StringConverter<Subject> subjectLabels() {
        return new StringConverter<>() {
            @Override
            public String toString(Subject subject) {
                return subject == null
                        ? ""
                        : subject.getCode() + " - " + subject.getName();
            }

            @Override
            public Subject fromString(String text) {
                return null;
            }
        };
    }

    /**
     * The four derived columns are null-guarded. The relations are
     * {@code optional = false} server-side, so this needs a row that arrived
     * without one - but an exception thrown inside a cell value factory is
     * swallowed by JavaFX, and the symptom was a blank table with no
     * explanation. The old version dereferenced all four straight away.
     */
    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDay.setCellValueFactory(new PropertyValueFactory<>("day"));
        colStartTime.setCellValueFactory(data ->
                new SimpleStringProperty(format(data.getValue().getStartTime())));
        colEndTime.setCellValueFactory(data ->
                new SimpleStringProperty(format(data.getValue().getEndTime())));
        colClassroom.setCellValueFactory(data -> {
            Classroom classroom = data.getValue().getClassroom();
            return new SimpleStringProperty(
                    classroom == null ? "" : classroom.getRoomNumber());
        });
        colSubject.setCellValueFactory(data -> {
            Subject subject = data.getValue().getSubject();
            return new SimpleStringProperty(subject == null ? "" : subject.getCode());
        });
    }

    private static String format(LocalTime time) {
        return time == null ? "" : time.format(TIME_FORMAT);
    }

    @Override
    public void refreshData() {
        loadClassrooms();
        loadSubjects();
        load();
    }

    private void loadClassrooms() {
        Crud.read(classroomService::getAllClassrooms,
                classrooms -> Crud.putItems(comboClassroom, classrooms, Classroom::getId));
    }

    private void loadSubjects() {
        Crud.read(subjectService::getAllSubjects,
                subjects -> Crud.putItems(comboSubject, subjects, Subject::getId));
    }

    private void load() {
        Crud.loading(timetableTable);
        Crud.read(timetableService::getAllTimetables, loaded -> {
            Crud.empty(timetableTable,
                    "Nothing scheduled yet. Add a class on the left.");
            rows.setAll(loaded);
        });
    }

    private void populateForm(Timetable timetable) {
        comboDay.setValue(timetable.getDay());
        txtStartTime.setText(format(timetable.getStartTime()));
        txtEndTime.setText(format(timetable.getEndTime()));
        Crud.selectById(comboClassroom, timetable.getClassroom(), Classroom::getId);
        Crud.selectById(comboSubject, timetable.getSubject(), Subject::getId);
    }

    // =========================================================
    // WRITES
    // =========================================================

    @FXML
    private void addTimetable() {
        LocalTime start = parseTime(txtStartTime.getText());
        LocalTime end = parseTime(txtEndTime.getText());
        if (!validateForm(start, end)) {
            return;
        }
        Timetable entry = fromForm(null, start, end);
        Crud.write(() -> timetableService.addTimetable(entry),
                () -> {
                    Toast.ok(entry.getSubject().getCode() + " scheduled on "
                            + entry.getDay() + " at " + format(start) + ".");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void updateTimetable() {
        if (selected == null) {
            Toast.warn("Choose a class in the table to update.");
            return;
        }
        LocalTime start = parseTime(txtStartTime.getText());
        LocalTime end = parseTime(txtEndTime.getText());
        if (!validateForm(start, end)) {
            return;
        }
        Timetable edited = fromForm(selected.getId(), start, end);
        Crud.write(() -> timetableService.updateTimetable(edited),
                () -> {
                    Toast.ok("Class updated.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void deleteTimetable() {
        if (selected == null) {
            Toast.warn("Choose a class in the table to delete.");
            return;
        }
        Timetable doomed = selected;
        Subject subject = doomed.getSubject();
        String what = subject == null ? "this class" : subject.getCode();
        boolean go = Crud.confirmDelete(timetableTable,
                what,
                what + " on " + doomed.getDay() + " at "
                        + format(doomed.getStartTime()) + " will be removed from the "
                        + "timetable. Attendance already recorded against it is kept.");
        if (!go) {
            return;
        }
        Crud.write(() -> timetableService.deleteTimetable(doomed.getId()),
                () -> {
                    Toast.ok(what + " removed from the timetable.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    // =========================================================
    // THE FORM
    // =========================================================

    private Timetable fromForm(Long id, LocalTime start, LocalTime end) {
        return new Timetable(
                id,
                comboDay.getValue(),
                start,
                end,
                comboClassroom.getValue(),
                comboSubject.getValue());
    }

    @FXML
    private void clearForm() {
        comboDay.setValue(null);
        txtStartTime.clear();
        txtEndTime.clear();
        comboClassroom.setValue(null);
        comboSubject.setValue(null);
        timetableTable.getSelectionModel().clearSelection();
        selected = null;
    }

    /** {@code null} for anything that is not an {@code HH:mm} time. */
    private LocalTime parseTime(String text) {
        if (Crud.blank(text)) {
            return null;
        }
        try {
            return LocalTime.parse(text.trim(), TIME_FORMAT);
        } catch (DateTimeParseException notATime) {
            return null;
        }
    }

    /**
     * Both times are parsed by the callers and passed in, so nothing downstream
     * re-parses them and they cannot differ between the check and the write.
     */
    private boolean validateForm(LocalTime start, LocalTime end) {
        if (comboDay.getValue() == null) {
            Toast.warn("Choose the day of the week.");
            return false;
        }
        if (comboClassroom.getValue() == null) {
            Toast.warn("Choose the classroom.");
            return false;
        }
        if (comboSubject.getValue() == null) {
            Toast.warn("Choose the subject.");
            return false;
        }
        if (start == null || end == null) {
            Toast.warn("Enter both times as HH:mm, for example 09:00.");
            return false;
        }
        if (!start.isBefore(end)) {
            Toast.warn("The class has to end after it starts.");
            return false;
        }
        return true;
    }
}
