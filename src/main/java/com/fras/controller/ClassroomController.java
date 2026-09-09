package com.fras.controller;

import com.fras.Refreshable;
import com.fras.model.Classroom;
import com.fras.service.ClassroomService;
import com.fras.service.impl.ClassroomServiceImpl;
import com.fras.ui.Toast;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Classrooms - the rooms attendance is taken in.
 *
 * <p>Same shape as {@link DepartmentController}: {@link Crud} does the threading
 * and the reporting, and Update sends a detached copy so a refused write leaves
 * the table showing what the database actually holds.
 */
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
    private final ObservableList<Classroom> rows = FXCollections.observableArrayList();

    private Classroom selected;

    @FXML
    public void initialize() {
        setupTableColumns();
        classroomTable.setItems(rows);
        classroomTable.getSelectionModel().selectedItemProperty()
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
        colRoomNumber.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        colBuilding.setCellValueFactory(new PropertyValueFactory<>("building"));
        colFloor.setCellValueFactory(new PropertyValueFactory<>("floor"));
        colCapacity.setCellValueFactory(new PropertyValueFactory<>("capacity"));
    }

    @Override
    public void refreshData() {
        load();
    }

    private void load() {
        Crud.loading(classroomTable);
        Crud.read(classroomService::getAllClassrooms, loaded -> {
            Crud.empty(classroomTable, "No classrooms yet. Add the first one on the left.");
            rows.setAll(loaded);
        });
    }

    private void populateForm(Classroom classroom) {
        txtRoomNumber.setText(classroom.getRoomNumber());
        txtBuilding.setText(classroom.getBuilding());
        txtFloor.setText(String.valueOf(classroom.getFloor()));
        txtCapacity.setText(String.valueOf(classroom.getCapacity()));
    }

    // =========================================================
    // WRITES
    // =========================================================

    @FXML
    private void addClassroom() {
        if (!validateForm()) {
            return;
        }
        Classroom classroom = fromForm(null);
        Crud.write(() -> classroomService.addClassroom(classroom),
                () -> {
                    Toast.ok("Room " + classroom.getRoomNumber() + " added.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void updateClassroom() {
        if (selected == null) {
            Toast.warn("Choose a classroom in the table to update.");
            return;
        }
        if (!validateForm()) {
            return;
        }
        Classroom edited = fromForm(selected.getId());
        Crud.write(() -> classroomService.updateClassroom(edited),
                () -> {
                    Toast.ok("Room " + edited.getRoomNumber() + " updated.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    @FXML
    private void deleteClassroom() {
        if (selected == null) {
            Toast.warn("Choose a classroom in the table to delete.");
            return;
        }
        Classroom doomed = selected;
        boolean go = Crud.confirmDelete(classroomTable,
                "room " + doomed.getRoomNumber(),
                "Room " + doomed.getRoomNumber() + " in " + doomed.getBuilding()
                        + " will be removed. A room cannot be deleted while it is booked "
                        + "on the timetable, or once attendance has been taken in it.");
        if (!go) {
            return;
        }
        Crud.write(() -> classroomService.deleteClassroom(doomed.getId()),
                () -> {
                    Toast.ok("Room " + doomed.getRoomNumber() + " deleted.");
                    load();
                    clearForm();
                },
                btnAdd, btnUpdate, btnDelete);
    }

    // =========================================================
    // THE FORM
    // =========================================================

    private Classroom fromForm(Long id) {
        return new Classroom(
                id,
                txtRoomNumber.getText().trim(),
                txtBuilding.getText().trim(),
                Integer.parseInt(txtFloor.getText().trim()),
                Integer.parseInt(txtCapacity.getText().trim()));
    }

    @FXML
    private void clearForm() {
        txtRoomNumber.clear();
        txtBuilding.clear();
        txtFloor.clear();
        txtCapacity.clear();
        classroomTable.getSelectionModel().clearSelection();
        selected = null;
    }

    /**
     * The numbers are parsed here and only read in {@link #fromForm(Long)}, so
     * {@code parseInt} there cannot throw once this has passed.
     */
    private boolean validateForm() {
        if (Crud.blank(txtRoomNumber.getText())) {
            Toast.warn("A classroom needs a room number.");
            return false;
        }
        if (Crud.blank(txtBuilding.getText())) {
            Toast.warn("A classroom needs a building.");
            return false;
        }
        int floor;
        int capacity;
        try {
            floor = Integer.parseInt(txtFloor.getText().trim());
            capacity = Integer.parseInt(txtCapacity.getText().trim());
        } catch (NumberFormatException notANumber) {
            Toast.warn("Floor and capacity must be whole numbers.");
            return false;
        }
        if (floor < 0) {
            Toast.warn("A floor cannot be negative.");
            return false;
        }
        if (capacity < 1) {
            Toast.warn("A classroom needs room for at least one person.");
            return false;
        }
        return true;
    }
}
