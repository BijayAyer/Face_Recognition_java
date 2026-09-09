package com.fras.controller;

import com.fras.Main.Refreshable;
import com.fras.model.Classroom;
import com.fras.model.Subject;
import com.fras.model.Timetable;
import com.fras.service.ClassroomService;
import com.fras.service.SubjectService;
import com.fras.service.TimetableService;
import com.fras.service.impl.ClassroomServiceImpl;
import com.fras.service.impl.SubjectServiceImpl;
import com.fras.service.impl.TimetableServiceImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TimetableController implements Refreshable {

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

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ClassroomService classroomService = new ClassroomServiceImpl();
    private final SubjectService subjectService = new SubjectServiceImpl();
    private final TimetableService timetableService = new TimetableServiceImpl();
    private final ObservableList<Timetable> timetableList = FXCollections.observableArrayList();
    private Timetable selectedTimetable;

    @FXML
    public void initialize() {
        setupDayCombo();
        setupClassroomCombo();
        setupSubjectCombo();
        setupTableColumns();
        setupTableSelectionListener();
        loadTimetables();
    }

    private void setupDayCombo() {
        comboDay.setItems(FXCollections.observableArrayList(
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        ));
    }

    private void setupClassroomCombo() {
        comboClassroom.setItems(FXCollections.observableArrayList(classroomService.getAllClassrooms()));
        comboClassroom.setConverter(new StringConverter<>() {
            @Override
            public String toString(Classroom c) {
                return c == null ? "" : c.getRoomNumber() + " (" + c.getBuilding() + ")";
            }

            @Override
            public Classroom fromString(String s) {
                return null;
            }
        });
    }

    private void setupSubjectCombo() {
        comboSubject.setItems(FXCollections.observableArrayList(subjectService.getAllSubjects()));
        comboSubject.setConverter(new StringConverter<>() {
            @Override
            public String toString(Subject s) {
                return s == null ? "" : s.getCode() + " - " + s.getName();
            }

            @Override
            public Subject fromString(String s) {
                return null;
            }
        });
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDay.setCellValueFactory(new PropertyValueFactory<>("day"));
        colStartTime.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getStartTime().format(TIME_FORMAT)));
        colEndTime.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getEndTime().format(TIME_FORMAT)));
        colClassroom.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getClassroom().getRoomNumber()));
        colSubject.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getSubject().getCode()));
    }

    private void setupTableSelectionListener() {
        timetableTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedTimetable = newVal;
                populateForm(newVal);
            }
        });
    }

    private void loadTimetables() {
        timetableList.setAll(timetableService.getAllTimetables());
        timetableTable.setItems(timetableList);
    }

    @Override
    public void refreshData() {
        setupClassroomCombo();
        setupSubjectCombo();
        loadTimetables();
    }

    private void populateForm(Timetable timetable) {
        comboDay.setValue(timetable.getDay());
        txtStartTime.setText(timetable.getStartTime().format(TIME_FORMAT));
        txtEndTime.setText(timetable.getEndTime().format(TIME_FORMAT));
        comboClassroom.setValue(timetable.getClassroom());
        comboSubject.setValue(timetable.getSubject());
    }

    @FXML
    private void addTimetable() {
        LocalTime start = parseTime(txtStartTime.getText());
        LocalTime end = parseTime(txtEndTime.getText());
        if (!validateForm(start, end)) return;

        Timetable timetable = new Timetable(
                null,
                comboDay.getValue(),
                start,
                end,
                comboClassroom.getValue(),
                comboSubject.getValue()
        );

        try {
            timetableService.addTimetable(timetable);
            loadTimetables();
            clearForm();
        } catch (IllegalStateException e) {
            showAlert(e.getMessage());
        }
    }

    @FXML
    private void updateTimetable() {
        if (selectedTimetable == null) {
            showAlert("Please select a timetable entry to update.");
            return;
        }
        LocalTime start = parseTime(txtStartTime.getText());
        LocalTime end = parseTime(txtEndTime.getText());
        if (!validateForm(start, end)) return;

        Timetable candidate = new Timetable(
                selectedTimetable.getId(),
                comboDay.getValue(),
                start,
                end,
                comboClassroom.getValue(),
                comboSubject.getValue()
        );

        try {
            timetableService.updateTimetable(candidate);
            selectedTimetable = candidate;
            loadTimetables();
            clearForm();
        } catch (IllegalStateException e) {
            showAlert(e.getMessage());
        }
    }

    @FXML
    private void deleteTimetable() {
        if (selectedTimetable == null) {
            showAlert("Please select a timetable entry to delete.");
            return;
        }
        timetableService.deleteTimetable(selectedTimetable.getId());
        loadTimetables();
        clearForm();
    }

    @FXML
    private void clearForm() {
        comboDay.setValue(null);
        txtStartTime.clear();
        txtEndTime.clear();
        comboClassroom.setValue(null);
        comboSubject.setValue(null);
        timetableTable.getSelectionModel().clearSelection();
        selectedTimetable = null;
    }

    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.trim(), TIME_FORMAT);
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    private boolean validateForm(LocalTime start, LocalTime end) {
        if (comboDay.getValue() == null || comboClassroom.getValue() == null || comboSubject.getValue() == null) {
            showAlert("Please select day, classroom, and subject.");
            return false;
        }
        if (start == null || end == null) {
            showAlert("Enter start and end time in HH:mm format (e.g. 09:00).");
            return false;
        }
        if (!start.isBefore(end)) {
            showAlert("Start time must be before end time.");
            return false;
        }
        return true;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
