package com.fras.controller;

import com.fras.model.Department;
import com.fras.Main.Refreshable;
import com.fras.service.DepartmentService;
import com.fras.service.impl.DepartmentServiceImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

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
    private final ObservableList<Department> departmentList = FXCollections.observableArrayList();
    private Department selectedDepartment;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearchListener();
        setupTableSelectionListener();
        loadDepartments();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCode.setCellValueFactory(new PropertyValueFactory<>("departmentCode"));
        colName.setCellValueFactory(new PropertyValueFactory<>("departmentName"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    private void setupTableSelectionListener() {
        departmentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedDepartment = newVal;
                populateForm(newVal);
            }
        });
    }

    private void setupSearchListener() {
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> filterDepartments(newVal));
    }

    private void loadDepartments() {
        departmentList.setAll(departmentService.getAllDepartments());
        departmentTable.setItems(departmentList);
    }

    @Override
    public void refreshData() {
        loadDepartments();
    }

    private void filterDepartments(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            departmentTable.setItems(departmentList);
            return;
        }
        String lower = keyword.toLowerCase();
        ObservableList<Department> filtered = FXCollections.observableArrayList();
        for (Department d : departmentList) {
            if (d.getDepartmentCode().toLowerCase().contains(lower)
                    || d.getDepartmentName().toLowerCase().contains(lower)) {
                filtered.add(d);
            }
        }
        departmentTable.setItems(filtered);
    }

    private void populateForm(Department department) {
        txtDepartmentCode.setText(department.getDepartmentCode());
        txtDepartmentName.setText(department.getDepartmentName());
        txtDescription.setText(department.getDescription());
    }

    @FXML
    private void addDepartment() {
        if (!validateForm()) return;

        Department department = new Department(
                null,
                txtDepartmentCode.getText().trim(),
                txtDepartmentName.getText().trim(),
                txtDescription.getText().trim()
        );

        departmentService.addDepartment(department);
        loadDepartments();
        clearForm();
    }

    @FXML
    private void updateDepartment() {
        if (selectedDepartment == null) {
            showAlert("Please select a department to update.");
            return;
        }
        if (!validateForm()) return;

        selectedDepartment.setDepartmentCode(txtDepartmentCode.getText().trim());
        selectedDepartment.setDepartmentName(txtDepartmentName.getText().trim());
        selectedDepartment.setDescription(txtDescription.getText().trim());

        departmentService.updateDepartment(selectedDepartment);
        loadDepartments();
        clearForm();
    }

    @FXML
    private void deleteDepartment() {
        if (selectedDepartment == null) {
            showAlert("Please select a department to delete.");
            return;
        }

        departmentService.deleteDepartment(selectedDepartment.getId());
        loadDepartments();
        clearForm();
    }

    @FXML
    private void clearForm() {
        txtDepartmentCode.clear();
        txtDepartmentName.clear();
        txtDescription.clear();
        departmentTable.getSelectionModel().clearSelection();
        selectedDepartment = null;
    }

    private boolean validateForm() {
        if (txtDepartmentCode.getText().trim().isEmpty()
                || txtDepartmentName.getText().trim().isEmpty()) {
            showAlert("Department Code and Department Name are required.");
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
