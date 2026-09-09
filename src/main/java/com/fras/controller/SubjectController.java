package com.fras.controller;

import com.fras.Refreshable;
import com.fras.model.Semester;
import com.fras.model.Subject;
import com.fras.service.SemesterService;
import com.fras.service.SubjectService;
import com.fras.service.impl.SemesterServiceImpl;
import com.fras.service.impl.SubjectServiceImpl;
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

/**
 * Subjects, each belonging to a semester.
 *
 * <p>Like {@link SemesterController}, the combo's items and the semester hanging
 * off a subject row come from two separate requests and the models have no
 * {@code equals}, so selection goes through {@link Crud#selectById} by id. The
 * combo labels a semester with its department in brackets, because two
 * departments can both run a "Semester 1" and the name alone does not say which
 * one is which.
 */
public class SubjectController implements Refreshable {

    @FXML private ComboBox<Semester> comboSemester;
    @FXML private TextField txtSubjectCode;
    @FXML private TextField txtSubjectName;
    @FXML private TextField txtCredit;

    @FXML private TableView<Subject> subjectTable;
    @FXML private TableColumn<Subject, Long> colId;
    @FXML private TableColumn<Subject, String> colCode;
    @FXML private TableColumn<Subject, String> colName;
    @FXML private TableColumn<Subject, Integer> colCredit;
    @FXML private TableColumn<Subject, String> colSemester;

    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnDelete;
    @FXML private Button btnClear;

    private final SemesterService semesterService = new SemesterServiceImpl();
    private final SubjectService subjectService = new SubjectServiceImpl();
    private final ObservableList<Subject> rows = FXCollections.observableArrayList();

    private Subject selected;

    @FXML
    public void initialize() {
        comboSemester.setConverter(semesterLabels());
        setupTableColumns();
        subjectTable.setItems(rows);
        subjectTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, before, after) -> {
                    if (after != null) {
                        selected = after;
                        populateForm(after);
                    }
                });
        loadSemesters();
        load();
    }

    /** Set once. The old version rebuilt it on every refresh. */
    private StringConverter<Semester> semesterLabels() {
        return new StringConverter<>() {
            @Override
            public String toString(Semester semester) {
                if (semester == null) {
                    return "";
                }
                String department = semester.getDepartment() == null
                        ? "" : semester.getDepartment().getDepartmentName();
                return department.isEmpty()
                        ? semester.getName()
                        : semester.getName() + " (" + department + ")";
            }

            @Override
            public Semester fromString(String text) {
                return null;
            }
        };
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));
        colSemester.setCellValueFactory(data -> {
            Semester semester = data.getValue().getSemester();
            return new SimpleStringProperty(semester == null ? "" : semester.getName());
        });
    }

    @Override
    public void refreshData() {
        loadSemesters();
        load();
    }

    private void loadSemesters() {
        Crud.read(semesterService::getAllSemesters,
                semesters -> Crud.putItems(comboSemester, semesters, Semester::getId));
    }

    private void load() {
        Crud.loading(subjectTable);
        Crud.read(subjectService::getAllSubjects, loaded -> {
            Crud.empty(subjectTable, "No subjects yet. Pick a semester and add one.");
            rows.setAll(loaded);
        });
    }

    private void populateForm(Subject subject) {
        Crud.selectById(comboSemester, subject.getSemester(), Semester::getId);
        txtSubjectCode.setText(subject.getCode());
        txtSubjectName.setText(subject.getName());
        txtCredit.setText(String.valueOf(subject.getCredit()));
    }

    // =========================================================
    // WRITES
    // =========================================================

    @FXML
    private void addSubject() {
        if (!validateForm()) {
            return;
        }
        Subject subject = fromForm(null);
        Crud.write(() -> subjectService.addSubject(subject),
                () -> {
                    Toast.ok("Subject " + subject.getCode() + " added.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void updateSubject() {
        if (selected == null) {
            Toast.warn("Choose a subject in the table to update.");
            return;
        }
        if (!validateForm()) {
            return;
        }
        // A copy carrying the selected row's id. The old version wrote the form
        // into the row already in the table before calling the server, so a
        // refused update - a duplicate subject code, most often, since the
        // column is unique - left the screen showing an edit that never landed.
        Subject edited = fromForm(selected.getId());
        Crud.write(() -> subjectService.updateSubject(edited),
                () -> {
                    Toast.ok("Subject " + edited.getCode() + " updated.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void deleteSubject() {
        if (selected == null) {
            Toast.warn("Choose a subject in the table to delete.");
            return;
        }
        Subject doomed = selected;
        boolean go = Crud.confirmDelete(subjectTable,
                "subject " + doomed.getCode(),
                "\"" + doomed.getName() + "\" will be removed. A subject cannot be "
                        + "deleted while it is on the timetable, or once attendance has "
                        + "been recorded against it.");
        if (!go) {
            return;
        }
        Crud.write(() -> subjectService.deleteSubject(doomed.getId()),
                () -> {
                    Toast.ok("Subject " + doomed.getCode() + " deleted.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    // =========================================================
    // THE FORM
    // =========================================================

    private Subject fromForm(Long id) {
        return new Subject(
                id,
                txtSubjectCode.getText().trim(),
                txtSubjectName.getText().trim(),
                Integer.parseInt(txtCredit.getText().trim()),
                comboSemester.getValue());
    }

    @FXML
    private void clearForm() {
        comboSemester.setValue(null);
        txtSubjectCode.clear();
        txtSubjectName.clear();
        txtCredit.clear();
        subjectTable.getSelectionModel().clearSelection();
        selected = null;
    }

    /**
     * The credit is parsed here and only read in {@link #fromForm(Long)}, so
     * {@code parseInt} there cannot throw once this has passed. The old version
     * checked that it parsed but not that it was positive, so a subject worth
     * zero or minus three credits went to the server unchallenged.
     */
    private boolean validateForm() {
        if (comboSemester.getValue() == null) {
            Toast.warn("Choose the semester this subject belongs to.");
            return false;
        }
        if (Crud.blank(txtSubjectCode.getText())) {
            Toast.warn("A subject needs a code, for example CS101.");
            return false;
        }
        if (Crud.blank(txtSubjectName.getText())) {
            Toast.warn("A subject needs a name.");
            return false;
        }
        int credit;
        try {
            credit = Integer.parseInt(txtCredit.getText().trim());
        } catch (NumberFormatException notANumber) {
            Toast.warn("Credit must be a whole number.");
            return false;
        }
        if (credit < 1) {
            Toast.warn("A subject must be worth at least one credit.");
            return false;
        }
        return true;
    }
}
