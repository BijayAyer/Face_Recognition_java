package com.fras.controller;

import com.fras.Main.Refreshable;
import com.fras.model.Semester;
import com.fras.model.Subject;
import com.fras.service.SemesterService;
import com.fras.service.SubjectService;
import com.fras.service.impl.SemesterServiceImpl;
import com.fras.service.impl.SubjectServiceImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

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
    private final ObservableList<Subject> subjectList = FXCollections.observableArrayList();
    private Subject selectedSubject;

    @FXML
    public void initialize() {
        setupSemesterCombo();
        setupTableColumns();
        setupTableSelectionListener();
        loadSubjects();
    }

    private void setupSemesterCombo() {
        ObservableList<Semester> semesters = FXCollections.observableArrayList(semesterService.getAllSemesters());
        comboSemester.setItems(semesters);
        comboSemester.setConverter(new StringConverter<>() {
            @Override
            public String toString(Semester semester) {
                if (semester == null) return "";
                String deptName = semester.getDepartment() != null ? semester.getDepartment().getDepartmentName() : "";
                return semester.getName() + " (" + deptName + ")";
            }

            @Override
            public Semester fromString(String string) {
                return null;
            }
        });
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));
        colSemester.setCellValueFactory(data -> {
            Semester sem = data.getValue().getSemester();
            String name = sem != null ? sem.getName() : "";
            return new javafx.beans.property.SimpleStringProperty(name);
        });
    }

    private void setupTableSelectionListener() {
        subjectTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedSubject = newVal;
                populateForm(newVal);
            }
        });
    }

    private void loadSubjects() {
        subjectList.setAll(subjectService.getAllSubjects());
        subjectTable.setItems(subjectList);
    }

    @Override
    public void refreshData() {
        setupSemesterCombo();
        loadSubjects();
    }

    private void populateForm(Subject subject) {
        comboSemester.setValue(subject.getSemester());
        txtSubjectCode.setText(subject.getCode());
        txtSubjectName.setText(subject.getName());
        txtCredit.setText(String.valueOf(subject.getCredit()));
    }

    @FXML
    private void addSubject() {
        if (!validateForm()) return;

        Subject subject = new Subject(
                null,
                txtSubjectCode.getText().trim(),
                txtSubjectName.getText().trim(),
                Integer.parseInt(txtCredit.getText().trim()),
                comboSemester.getValue()
        );

        subjectService.addSubject(subject);
        loadSubjects();
        clearForm();
    }

    @FXML
    private void updateSubject() {
        if (selectedSubject == null) {
            showAlert("Please select a subject to update.");
            return;
        }
        if (!validateForm()) return;

        selectedSubject.setCode(txtSubjectCode.getText().trim());
        selectedSubject.setName(txtSubjectName.getText().trim());
        selectedSubject.setCredit(Integer.parseInt(txtCredit.getText().trim()));
        selectedSubject.setSemester(comboSemester.getValue());

        subjectService.updateSubject(selectedSubject);
        loadSubjects();
        clearForm();
    }

    @FXML
    private void deleteSubject() {
        if (selectedSubject == null) {
            showAlert("Please select a subject to delete.");
            return;
        }

        subjectService.deleteSubject(selectedSubject.getId());
        loadSubjects();
        clearForm();
    }

    @FXML
    private void clearForm() {
        comboSemester.setValue(null);
        txtSubjectCode.clear();
        txtSubjectName.clear();
        txtCredit.clear();
        subjectTable.getSelectionModel().clearSelection();
        selectedSubject = null;
    }

    private boolean validateForm() {
        if (comboSemester.getValue() == null) {
            showAlert("Please select a semester.");
            return false;
        }
        if (txtSubjectCode.getText().trim().isEmpty() || txtSubjectName.getText().trim().isEmpty()) {
            showAlert("Subject code and name are required.");
            return false;
        }
        try {
            Integer.parseInt(txtCredit.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Credit must be a valid number.");
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
