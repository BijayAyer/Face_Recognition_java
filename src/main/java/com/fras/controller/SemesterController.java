package com.fras.controller;

import com.fras.Main.Refreshable;
import com.fras.model.Department;
import com.fras.model.Semester;
import com.fras.service.DepartmentService;
import com.fras.service.SemesterService;
import com.fras.service.impl.DepartmentServiceImpl;
import com.fras.service.impl.SemesterServiceImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

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
    private final ObservableList<Semester> semesterList = FXCollections.observableArrayList();
    private Semester selectedSemester;

    @FXML
    public void initialize() {
        setupDepartmentCombo();
        setupTableColumns();
        setupTableSelectionListener();
        loadSemesters();
    }

    private void setupDepartmentCombo() {
        ObservableList<Department> departments = FXCollections.observableArrayList(departmentService.getAllDepartments());
        comboDepartment.setItems(departments);
        comboDepartment.setConverter(new StringConverter<>() {
            @Override
            public String toString(Department department) {
                return department == null ? "" : department.getDepartmentName();
            }

            @Override
            public Department fromString(String string) {
                return null;
            }
        });
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colNumber.setCellValueFactory(new PropertyValueFactory<>("number"));
        colDepartment.setCellValueFactory(data -> {
            Department dept = data.getValue().getDepartment();
            String name = dept != null ? dept.getDepartmentName() : "";
            return new javafx.beans.property.SimpleStringProperty(name);
        });
    }

    private void setupTableSelectionListener() {
        semesterTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedSemester = newVal;
                populateForm(newVal);
            }
        });
    }

    private void loadSemesters() {
        semesterList.setAll(semesterService.getAllSemesters());
        semesterTable.setItems(semesterList);
    }

    @Override
    public void refreshData() {
        setupDepartmentCombo();
        loadSemesters();
    }

    private void populateForm(Semester semester) {
        comboDepartment.setValue(semester.getDepartment());
        txtSemesterName.setText(semester.getName());
        txtSemesterNumber.setText(String.valueOf(semester.getNumber()));
    }

    @FXML
    private void addSemester() {
        if (!validateForm()) return;

        Semester semester = new Semester(
                null,
                txtSemesterName.getText().trim(),
                Integer.parseInt(txtSemesterNumber.getText().trim()),
                comboDepartment.getValue()
        );

        semesterService.addSemester(semester);
        loadSemesters();
        clearForm();
    }

    @FXML
    private void updateSemester() {
        if (selectedSemester == null) {
            showAlert("Please select a semester to update.");
            return;
        }
        if (!validateForm()) return;

        selectedSemester.setName(txtSemesterName.getText().trim());
        selectedSemester.setNumber(Integer.parseInt(txtSemesterNumber.getText().trim()));
        selectedSemester.setDepartment(comboDepartment.getValue());

        semesterService.updateSemester(selectedSemester);
        loadSemesters();
        clearForm();
    }

    @FXML
    private void deleteSemester() {
        if (selectedSemester == null) {
            showAlert("Please select a semester to delete.");
            return;
        }

        semesterService.deleteSemester(selectedSemester.getId());
        loadSemesters();
        clearForm();
    }

    @FXML
    private void clearForm() {
        comboDepartment.setValue(null);
        txtSemesterName.clear();
        txtSemesterNumber.clear();
        semesterTable.getSelectionModel().clearSelection();
        selectedSemester = null;
    }

    private boolean validateForm() {
        if (comboDepartment.getValue() == null) {
            showAlert("Please select a department.");
            return false;
        }
        if (txtSemesterName.getText().trim().isEmpty()) {
            showAlert("Semester name is required.");
            return false;
        }
        try {
            Integer.parseInt(txtSemesterNumber.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Semester number must be a valid number.");
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
