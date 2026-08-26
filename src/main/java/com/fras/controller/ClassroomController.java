package com.fras.controller;

import com.fras.Main.Refreshable;
import com.fras.model.Classroom;
import com.fras.service.ClassroomService;
import com.fras.service.impl.ClassroomServiceImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

public class ClassroomController implements Refreshable {

    @FXML private TextField txtRoomNumber;
    @FXML private TextField txtBuilding;
    @FXML private TextField txtFloor;
    @FXML private TextField txtCapacity;

    @FXML private TableView<Classroom> classroomTable;
    @FXML private TableColumn<Classroom, Long> colId;
    @FXML private TableColumn<Classroom, String> colRoomNumber;
    @FXML private TableColumn<Classroom, String> colBuilding;
    @FXML private TableColumn<Classroom, Integer> colFloor;
    @FXML private TableColumn<Classroom, Integer> colCapacity;

    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnDelete;
    @FXML private Button btnClear;

    private final ClassroomService classroomService = new ClassroomServiceImpl();
    private final ObservableList<Classroom> classroomList = FXCollections.observableArrayList();
    private Classroom selectedClassroom;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupTableSelectionListener();
        loadClassrooms();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colRoomNumber.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        colBuilding.setCellValueFactory(new PropertyValueFactory<>("building"));
        colFloor.setCellValueFactory(new PropertyValueFactory<>("floor"));
        colCapacity.setCellValueFactory(new PropertyValueFactory<>("capacity"));
    }

    private void setupTableSelectionListener() {
        classroomTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedClassroom = newVal;
                populateForm(newVal);
            }
        });
    }

    private void loadClassrooms() {
        classroomList.setAll(classroomService.getAllClassrooms());
        classroomTable.setItems(classroomList);
    }

    @Override
    public void refreshData() {
        loadClassrooms();
    }

    private void populateForm(Classroom classroom) {
        txtRoomNumber.setText(classroom.getRoomNumber());
        txtBuilding.setText(classroom.getBuilding());
        txtFloor.setText(String.valueOf(classroom.getFloor()));
        txtCapacity.setText(String.valueOf(classroom.getCapacity()));
    }

    @FXML
    private void addClassroom() {
        if (!validateForm()) return;

        Classroom classroom = new Classroom(
                null,
                txtRoomNumber.getText().trim(),
                txtBuilding.getText().trim(),
                Integer.parseInt(txtFloor.getText().trim()),
                Integer.parseInt(txtCapacity.getText().trim())
        );

        classroomService.addClassroom(classroom);
        loadClassrooms();
        clearForm();
    }

    @FXML
    private void updateClassroom() {
        if (selectedClassroom == null) {
            showAlert("Please select a classroom to update.");
            return;
        }
        if (!validateForm()) return;

        selectedClassroom.setRoomNumber(txtRoomNumber.getText().trim());
        selectedClassroom.setBuilding(txtBuilding.getText().trim());
        selectedClassroom.setFloor(Integer.parseInt(txtFloor.getText().trim()));
        selectedClassroom.setCapacity(Integer.parseInt(txtCapacity.getText().trim()));

        classroomService.updateClassroom(selectedClassroom);
        loadClassrooms();
        clearForm();
    }

    @FXML
    private void deleteClassroom() {
        if (selectedClassroom == null) {
            showAlert("Please select a classroom to delete.");
            return;
        }

        classroomService.deleteClassroom(selectedClassroom.getId());
        loadClassrooms();
        clearForm();
    }

    @FXML
    private void clearForm() {
        txtRoomNumber.clear();
        txtBuilding.clear();
        txtFloor.clear();
        txtCapacity.clear();
        classroomTable.getSelectionModel().clearSelection();
        selectedClassroom = null;
    }

    private boolean validateForm() {
        if (txtRoomNumber.getText().trim().isEmpty() || txtBuilding.getText().trim().isEmpty()) {
            showAlert("Room number and building are required.");
            return false;
        }
        try {
            Integer.parseInt(txtFloor.getText().trim());
            Integer.parseInt(txtCapacity.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Floor and capacity must be valid numbers.");
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
