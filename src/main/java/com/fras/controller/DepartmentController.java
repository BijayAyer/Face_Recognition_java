package com.fras.controller;

import com.fras.Refreshable;
import com.fras.model.Department;
import com.fras.service.DepartmentService;
import com.fras.service.impl.DepartmentServiceImpl;
import com.fras.ui.Toast;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Departments.
 *
 * <p>Reads and writes go through {@link Crud}, which is where the reasoning
 * about threading and error reporting lives. Two things specific to this tab:
 *
 * <p>The table is bound to {@code shown} for its whole life and the search filter
 * recomputes it, rather than the old arrangement where reloading called
 * {@code setItems(fullList)} and so silently dropped whatever the user had typed
 * in the search box.
 *
 * <p>Update sends a detached copy. The old version wrote the form's values
 * straight into the object already in the table and only then called the server,
 * so a rejected update - a duplicate code, most often - left the screen showing
 * an edit the database had never accepted.
 */
public class DepartmentController implements Refreshable {

    @FXML private TextField txtDepartmentCode;
    @FXML private TextField txtDepartmentName;
    @FXML private TextArea txtDescription;
    @FXML private TextField txtSearch;

    @FXML private TableView<Department> departmentTable;
    @FXML private TableColumn<Department, Long> colId;
    @FXML private TableColumn<Department, String> colCode;
    @FXML private TableColumn<Department, String> colName;
    @FXML private TableColumn<Department, String> colDescription;

    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnDelete;
    @FXML private Button btnClear;

    private final DepartmentService departmentService = new DepartmentServiceImpl();

    /** Everything the last load returned. */
    private final ObservableList<Department> all = FXCollections.observableArrayList();

    /** What the table shows: {@code all}, or the part of it that matches the search. */
    private final ObservableList<Department> shown = FXCollections.observableArrayList();

    private Department selected;

    @FXML
    public void initialize() {
        setupTableColumns();
        departmentTable.setItems(shown);
        txtSearch.textProperty().addListener((observable, before, after) -> applyFilter());
        departmentTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, before, after) -> {
                    if (after != null) {
                        selected = after;
                        populateForm(after);
                    }
                });
        load();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCode.setCellValueFactory(new PropertyValueFactory<>("departmentCode"));
        colName.setCellValueFactory(new PropertyValueFactory<>("departmentName"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    @Override
    public void refreshData() {
        load();
    }

    private void load() {
        Crud.loading(departmentTable);
        Crud.read(departmentService::getAllDepartments, rows -> {
            all.setAll(rows);
            Crud.empty(departmentTable, "No departments yet. Add the first one on the left.");
            applyFilter();
        });
    }

    /**
     * Narrows the table to rows matching the search box.
     *
     * <p>Both accessors are null-guarded. The columns are {@code nullable = false}
     * server-side, so this needs a row that arrived without them - but the old
     * unguarded version threw inside a {@code textProperty} listener, which
     * swallows the exception, and the visible symptom was a search box that had
     * simply stopped filtering.
     */
    private void applyFilter() {
        String keyword = Crud.lower(txtSearch.getText()).trim();
        if (keyword.isEmpty()) {
            shown.setAll(all);
            return;
        }
        ObservableList<Department> matches = FXCollections.observableArrayList();
        for (Department department : all) {
            if (Crud.lower(department.getDepartmentCode()).contains(keyword)
                    || Crud.lower(department.getDepartmentName()).contains(keyword)) {
                matches.add(department);
            }
        }
        shown.setAll(matches);
    }

    private void populateForm(Department department) {
        txtDepartmentCode.setText(department.getDepartmentCode());
        txtDepartmentName.setText(department.getDepartmentName());
        txtDescription.setText(department.getDescription());
    }

    // =========================================================
    // WRITES
    // =========================================================

    @FXML
    private void addDepartment() {
        if (!validateForm()) {
            return;
        }
        Department department = fromForm(null);
        Crud.write(() -> departmentService.addDepartment(department),
                () -> {
                    Toast.ok("Department " + department.getDepartmentCode() + " added.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void updateDepartment() {
        if (selected == null) {
            Toast.warn("Choose a department in the table to update.");
            return;
        }
        if (!validateForm()) {
            return;
        }
        // A copy carrying the selected row's id, so a refused update changes
        // nothing on screen. See the class comment.
        Department edited = fromForm(selected.getId());
        Crud.write(() -> departmentService.updateDepartment(edited),
                () -> {
                    Toast.ok("Department " + edited.getDepartmentCode() + " updated.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void deleteDepartment() {
        if (selected == null) {
            Toast.warn("Choose a department in the table to delete.");
            return;
        }
        Department doomed = selected;
        boolean go = Crud.confirmDelete(departmentTable,
                "department " + doomed.getDepartmentCode(),
                "\"" + doomed.getDepartmentName() + "\" will be removed. A department "
                        + "that still has semesters in it cannot be deleted.");
        if (!go) {
            return;
        }
        Crud.write(() -> departmentService.deleteDepartment(doomed.getId()),
                () -> {
                    Toast.ok("Department " + doomed.getDepartmentCode() + " deleted.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    // =========================================================
    // THE FORM
    // =========================================================

    private Department fromForm(Long id) {
        return new Department(
                id,
                txtDepartmentCode.getText().trim(),
                txtDepartmentName.getText().trim(),
                txtDescription.getText().trim());
    }

    @FXML
    private void clearForm() {
        txtDepartmentCode.clear();
        txtDepartmentName.clear();
        txtDescription.clear();
        departmentTable.getSelectionModel().clearSelection();
        selected = null;
    }

    private boolean validateForm() {
        if (Crud.blank(txtDepartmentCode.getText())) {
            Toast.warn("A department needs a code, for example CSE.");
            return false;
        }
        if (Crud.blank(txtDepartmentName.getText())) {
            Toast.warn("A department needs a name.");
            return false;
        }
        return true;
    }
}
