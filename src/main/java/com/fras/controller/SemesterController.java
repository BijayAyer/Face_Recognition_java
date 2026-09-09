package com.fras.controller;

import com.fras.Refreshable;
import com.fras.model.Department;
import com.fras.model.Semester;
import com.fras.service.DepartmentService;
import com.fras.service.SemesterService;
import com.fras.service.impl.DepartmentServiceImpl;
import com.fras.service.impl.SemesterServiceImpl;
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
 * Semesters, each belonging to a department.
 *
 * <p>The department combo is filled by its own request, separate from the one
 * that fills the table, so the {@code Department} hanging off a semester row is
 * never the same object as the matching entry in the combo. Selection is
 * therefore made by id through {@link Crud#selectById}; setting the value by
 * reference, as this used to, left the dropdown with nothing highlighted while
 * the form looked filled in.
 */
public class SemesterController implements Refreshable {

    @FXML private ComboBox<Department> comboDepartment;
    @FXML private TextField txtSemesterName;
    @FXML private TextField txtSemesterNumber;

    @FXML private TableView<Semester> semesterTable;
    @FXML private TableColumn<Semester, Long> colId;
    @FXML private TableColumn<Semester, String> colName;
    @FXML private TableColumn<Semester, Integer> colNumber;
    @FXML private TableColumn<Semester, String> colDepartment;

    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnDelete;
    @FXML private Button btnClear;

    private final DepartmentService departmentService = new DepartmentServiceImpl();
    private final SemesterService semesterService = new SemesterServiceImpl();
    private final ObservableList<Semester> rows = FXCollections.observableArrayList();

    private Semester selected;

    @FXML
    public void initialize() {
        comboDepartment.setConverter(departmentNames());
        setupTableColumns();
        semesterTable.setItems(rows);
        semesterTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, before, after) -> {
                    if (after != null) {
                        selected = after;
                        populateForm(after);
                    }
                });
        loadDepartments();
        load();
    }

    /** Set once. The old version rebuilt it on every refresh. */
    private StringConverter<Department> departmentNames() {
        return new StringConverter<>() {
            @Override
            public String toString(Department department) {
                return department == null ? "" : department.getDepartmentName();
            }

            @Override
            public Department fromString(String text) {
                return null;
            }
        };
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colNumber.setCellValueFactory(new PropertyValueFactory<>("number"));
        colDepartment.setCellValueFactory(data -> {
            Department department = data.getValue().getDepartment();
            return new SimpleStringProperty(
                    department == null ? "" : department.getDepartmentName());
        });
    }

    @Override
    public void refreshData() {
        loadDepartments();
        load();
    }

    private void loadDepartments() {
        Crud.read(departmentService::getAllDepartments,
                departments -> Crud.putItems(comboDepartment, departments, Department::getId));
    }

    private void load() {
        Crud.loading(semesterTable);
        Crud.read(semesterService::getAllSemesters, loaded -> {
            Crud.empty(semesterTable, "No semesters yet. Pick a department and add one.");
            rows.setAll(loaded);
        });
    }

    private void populateForm(Semester semester) {
        Crud.selectById(comboDepartment, semester.getDepartment(), Department::getId);
        txtSemesterName.setText(semester.getName());
        txtSemesterNumber.setText(String.valueOf(semester.getNumber()));
    }

    // =========================================================
    // WRITES
    // =========================================================

    @FXML
    private void addSemester() {
        if (!validateForm()) {
            return;
        }
        Semester semester = fromForm(null);
        Crud.write(() -> semesterService.addSemester(semester),
                () -> {
                    Toast.ok("Semester " + semester.getName() + " added.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void updateSemester() {
        if (selected == null) {
            Toast.warn("Choose a semester in the table to update.");
            return;
        }
        if (!validateForm()) {
            return;
        }
        Semester edited = fromForm(selected.getId());
        Crud.write(() -> semesterService.updateSemester(edited),
                () -> {
                    Toast.ok("Semester " + edited.getName() + " updated.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void deleteSemester() {
        if (selected == null) {
            Toast.warn("Choose a semester in the table to delete.");
            return;
        }
        Semester doomed = selected;
        boolean go = Crud.confirmDelete(semesterTable,
                "semester " + doomed.getName(),
                "\"" + doomed.getName() + "\" will be removed. A semester that still "
                        + "has subjects in it cannot be deleted.");
        if (!go) {
            return;
        }
        Crud.write(() -> semesterService.deleteSemester(doomed.getId()),
                () -> {
                    Toast.ok("Semester " + doomed.getName() + " deleted.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    // =========================================================
    // THE FORM
    // =========================================================

    private Semester fromForm(Long id) {
        return new Semester(
                id,
                txtSemesterName.getText().trim(),
                Integer.parseInt(txtSemesterNumber.getText().trim()),
                comboDepartment.getValue());
    }

    @FXML
    private void clearForm() {
        comboDepartment.setValue(null);
        txtSemesterName.clear();
        txtSemesterNumber.clear();
        semesterTable.getSelectionModel().clearSelection();
        selected = null;
    }

    /**
     * The department is required here, so {@link #fromForm(Long)} cannot build a
     * semester with none - which the server refuses anyway, since the column is
     * {@code nullable = false}. The number is parsed here too, so
     * {@code parseInt} there cannot throw once this has passed.
     */
    private boolean validateForm() {
        if (comboDepartment.getValue() == null) {
            Toast.warn("Choose the department this semester belongs to.");
            return false;
        }
        if (Crud.blank(txtSemesterName.getText())) {
            Toast.warn("A semester needs a name, for example Spring 2026.");
            return false;
        }
        int number;
        try {
            number = Integer.parseInt(txtSemesterNumber.getText().trim());
        } catch (NumberFormatException notANumber) {
            Toast.warn("The semester number must be a whole number.");
            return false;
        }
        if (number < 1) {
            Toast.warn("Semester numbers start at 1.");
            return false;
        }
        return true;
    }
}
