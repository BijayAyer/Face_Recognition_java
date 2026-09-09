package com.fras.ui.pages;

import com.fras.app.dto.AttendanceRow;
import com.fras.app.dto.StudentRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.service.ApiService;
import com.fras.ui.Async;
import com.fras.ui.Page;
import com.fras.ui.SessionPicker;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The register, two ways: one student across time, or one room on one day.
 *
 * <p>The old version asked for a comma-separated list of student ids before it
 * would export anything - a roster the server already knows, retyped by hand,
 * with no feedback when a number was wrong. The roster is now fetched, and the
 * export uses it.
 */
public final class ReportsPage extends Page {

    private final ApiService api;
    private final SessionPicker session;

    private final ComboBox<StudentRow> studentPicker = new ComboBox<>();
    private final TableView<AttendanceRow> historyTable = new TableView<>();
    private final Label historyNote = Ui.hint("Choose a student to see their record.");
    private final Label historySummary = Ui.styledLabel("", "metric-value");
    private final VBox historyTile = Ui.column(0, historySummary,
            Ui.hint("attended, of the sessions on record"));

    private final DatePicker day = new DatePicker(LocalDate.now());
    private final TableView<AttendanceRow> registerTable = new TableView<>();
    private final Label registerNote = Ui.hint("Choose a room, a subject and a date.");
    private final Button loadRegister = Ui.primary("Load register");
    private final Button exportExcel = Ui.secondary("Export Excel");
    private final Button exportPdf = Ui.secondary("Export PDF");

    private List<StudentRow> roster = List.of();

    public ReportsPage(ApiService api) {
        this.api = api;
        this.session = new SessionPicker(api);
    }

    @Override
    public String title() {
        return "Register";
    }

    @Override
    protected Node build() {
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                closedTab("One student", studentSection()),
                closedTab("One session", registerSection()));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox page = Ui.column(Ui.GAP,
                Ui.pageHeader("Register",
                        "What was recorded, and the file to hand over."),
                tabs);
        page.getStyleClass().add("page");
        return page;
    }

    private Tab closedTab(String label, Node content) {
        Tab tab = new Tab(label, content);
        tab.setClosable(false);
        return tab;
    }

    @Override
    public void onShow() {
        session.load(false);
        loadRoster();
    }

    private void loadRoster() {
        Async.run(() -> JsonTableUtil.parseList(api.getStudents(), StudentRow.class),
                rows -> {
                    roster = rows;
                    StudentRow before = studentPicker.getValue();
                    studentPicker.getItems().setAll(rows);
                    if (before != null) {
                        for (StudentRow row : rows) {
                            if (row.getId() != null && row.getId().equals(before.getId())) {
                                studentPicker.setValue(row);
                                break;
                            }
                        }
                    }
                    // The export needs a roster, so its buttons only make sense
                    // once one has arrived.
                    syncRegisterButtons();
                },
                Toast::error);
    }

    // =========================================================
    // ONE STUDENT, ACROSS TIME
    // =========================================================

    private Node studentSection() {
        studentPicker.setPrefWidth(320);
        studentPicker.setPromptText("Choose a student");
        studentPicker.setPlaceholder(Ui.hint("No students on the roster yet."));
        studentPicker.setButtonCell(studentCell());
        studentPicker.setCellFactory(ignored -> studentCell());
        studentPicker.valueProperty().addListener((observable, before, after) -> loadHistory(after));

        historyTable.getColumns().setAll(historyColumns());
        historyTable.setPlaceholder(Ui.emptyState("Nothing recorded yet",
                "Once this student has been marked in a session, their record appears here.", null));
        Ui.stretch(historyTable);

        hide(historyTile);

        VBox body = Ui.column(Ui.GAP,
                Ui.row(Ui.column(5, Ui.fieldLabel("Student"), studentPicker), Ui.growH(), historyTile),
                historyNote,
                historyTable);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        VBox panel = Ui.panel(body);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private ListCell<StudentRow> studentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(StudentRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setText(null);
                } else {
                    String name = row.getName() == null || row.getName().isBlank()
                            ? "Unnamed student" : row.getName();
                    setText(name + "   (ID " + row.getId() + ")");
                }
            }
        };
    }

    private void loadHistory(StudentRow row) {
        if (row == null || row.getId() == null) {
            historyTable.getItems().clear();
            hide(historyTile);
            historyNote.setText("Choose a student to see their record.");
            return;
        }

        long id = row.getId();
        historyNote.setText("Loading...");
        hide(historyTile);

        Async.run(() -> JsonTableUtil.parseList(api.getStudentAttendance(id), AttendanceRow.class),
                rows -> {
                    historyTable.setItems(FXCollections.observableArrayList(rows));
                    if (rows.isEmpty()) {
                        historyNote.setText("Nothing recorded for this student yet.");
                        return;
                    }
                    long attended = rows.stream().filter(ReportsPage::counted).count();
                    long percent = Math.round((attended * 100.0) / rows.size());
                    historySummary.setText(percent + "%");
                    show(historyTile);
                    historyNote.setText(rows.size() + " sessions on record, " + attended + " attended.");
                },
                message -> {
                    historyNote.setText("Could not load that record.");
                    Toast.error(message);
                });
    }

    /** LATE still counts as having turned up; only ABSENT does not. */
    private static boolean counted(AttendanceRow row) {
        String status = row.getStatus();
        return "PRESENT".equalsIgnoreCase(status) || "LATE".equalsIgnoreCase(status);
    }

    private static void show(Node node) {
        node.setVisible(true);
        node.setManaged(true);
    }

    private static void hide(Node node) {
        node.setVisible(false);
        node.setManaged(false);
    }

    // =========================================================
    // ONE ROOM, ONE DAY
    // =========================================================

    private Node registerSection() {
        day.setPrefWidth(150);
        day.valueProperty().addListener((observable, before, after) -> syncRegisterButtons());
        session.onChange(this::syncRegisterButtons);

        loadRegister.setOnAction(event -> loadRegisterFor());
        exportExcel.setOnAction(event -> export(true));
        exportPdf.setOnAction(event -> export(false));

        registerTable.getColumns().setAll(registerColumns());
        registerTable.setPlaceholder(Ui.emptyState("No register loaded",
                "Choose a room, a subject and a date, then load the register.", null));
        Ui.stretch(registerTable);

        HBox toolbar = Ui.row(session.asRow(),
                Ui.column(5, Ui.fieldLabel("Date"), day),
                Ui.growH(),
                loadRegister);

        HBox handOff = Ui.row(Ui.eyebrow("Hand-off"), Ui.growH(), exportExcel, exportPdf);

        VBox body = Ui.column(Ui.GAP, toolbar, registerNote, registerTable, Ui.divider(), handOff);
        VBox.setVgrow(registerTable, Priority.ALWAYS);

        syncRegisterButtons();

        VBox panel = Ui.panel(body);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    /**
     * The export does not need the table to be loaded - the file is built on the
     * server from the same room, subject, date and roster. It does need a
     * roster, which is why an empty one disables it rather than producing an
     * empty spreadsheet.
     */
    private void syncRegisterButtons() {
        boolean ready = session.isReady() && day.getValue() != null;
        loadRegister.setDisable(!ready);
        exportExcel.setDisable(!ready || roster.isEmpty());
        exportPdf.setDisable(!ready || roster.isEmpty());
    }

    private void loadRegisterFor() {
        Long classroomId = session.classroomId();
        Long subjectId = session.subjectId();
        LocalDate date = day.getValue();
        if (classroomId == null || subjectId == null || date == null) {
            return;
        }

        String isoDate = date.toString();
        registerNote.setText("Loading...");
        Ui.busy(true, loadRegister);

        Async.run(() -> JsonTableUtil.parseList(
                        api.getClassRegister(classroomId, subjectId, isoDate), AttendanceRow.class),
                rows -> {
                    Ui.busy(false, loadRegister);
                    registerTable.setItems(FXCollections.observableArrayList(rows));
                    if (rows.isEmpty()) {
                        registerNote.setText("Nothing was recorded for " + session.describe()
                                + " on " + isoDate + ".");
                        return;
                    }
                    long present = count(rows, "PRESENT");
                    long late = count(rows, "LATE");
                    long absent = rows.size() - present - late;
                    registerNote.setText(session.describe() + " on " + isoDate + ": "
                            + present + " present, " + late + " late, " + absent + " absent.");
                },
                message -> {
                    Ui.busy(false, loadRegister);
                    registerNote.setText("Could not load that register.");
                    Toast.error(message);
                });
    }

    private static long count(List<AttendanceRow> rows, String status) {
        return rows.stream().filter(row -> status.equalsIgnoreCase(row.getStatus())).count();
    }

    // =========================================================
    // THE FILE TO HAND OVER
    // =========================================================

    /**
     * The roster sent with the export is the one fetched from the server, not a
     * comma-separated list somebody retyped. The server needs it to know who was
     * meant to be there, since an absent student has no attendance row to find.
     */
    private void export(boolean excel) {
        Long classroomId = session.classroomId();
        Long subjectId = session.subjectId();
        LocalDate date = day.getValue();
        if (classroomId == null || subjectId == null || date == null) {
            return;
        }

        List<Long> ids = rosterIds();
        if (ids.isEmpty()) {
            Toast.warn("There is no roster to report on. Add students first.");
            return;
        }

        String isoDate = date.toString();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(excel ? "Save the register as Excel" : "Save the register as PDF");
        chooser.setInitialFileName("register-" + isoDate + (excel ? ".xlsx" : ".pdf"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                excel ? "Excel workbook" : "PDF document", excel ? "*.xlsx" : "*.pdf"));

        File target = chooser.showSaveDialog(
                exportExcel.getScene() == null ? null : exportExcel.getScene().getWindow());
        if (target == null) {
            // Cancelled. Not an error, and not worth a message.
            return;
        }

        Button pressed = excel ? exportExcel : exportPdf;
        Ui.busy(true, pressed);
        Async.run(() -> {
                    byte[] bytes = excel
                            ? api.downloadAttendanceExcel(classroomId, subjectId, isoDate, ids)
                            : api.downloadAttendancePdf(classroomId, subjectId, isoDate, ids);
                    if (bytes == null || bytes.length == 0) {
                        throw new java.io.IOException("The server returned an empty file.");
                    }
                    Files.write(target.toPath(), bytes);
                    return target;
                },
                saved -> {
                    Ui.busy(false, pressed);
                    Toast.ok("Saved " + saved.getName() + ".");
                    registerNote.setText("Saved to " + saved.getAbsolutePath());
                },
                message -> {
                    Ui.busy(false, pressed);
                    Toast.error(message);
                });
    }

    private List<Long> rosterIds() {
        List<Long> ids = new ArrayList<>();
        for (StudentRow row : roster) {
            if (row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * The columns for one student across time. Each row is a different session,
     * so the subject, the room and the date are what vary; the student is named
     * by the picker above the table and is not repeated on every line.
     *
     * <p>Built fresh on each call: a {@code TableColumn} belongs to one table,
     * so sharing instances between two would empty whichever asked first.
     *
     * <p>"Room 2" and "Subject 2" used to sit here, which named the row by
     * where the data is stored. Both are now resolved by the server and read
     * exactly as the pickers on the next tab do.
     */
    private List<TableColumn<AttendanceRow, ?>> historyColumns() {
        List<TableColumn<AttendanceRow, ?>> columns = new ArrayList<>();
        columns.add(Ui.<AttendanceRow, String>column("Date", "attendanceDate", 105));
        columns.add(Ui.<AttendanceRow, String>column("Time", "attendanceTime", 85));
        columns.add(Ui.<AttendanceRow, String>column("Subject", "subject", 170));
        columns.add(Ui.<AttendanceRow, String>column("Room", "room", 130));
        columns.add(Ui.<AttendanceRow>statusColumn("Status", "status", 105));
        columns.add(Ui.<AttendanceRow, String>column("Marked by", "markedBy", 130));
        columns.add(Ui.<AttendanceRow>percentColumn("Confidence", "confidenceScore", 95));
        return columns;
    }

    /**
     * The columns for one session. Every row is the same room, subject and date
     * - the three pickers above the table say which - so what varies is who,
     * and the register leads with the name and the section.
     *
     * <p>The id kept its place at the front because it is not only a database
     * key here: it is the folder a face is enrolled into and the number the
     * enrolment screen asks for.
     */
    private List<TableColumn<AttendanceRow, ?>> registerColumns() {
        List<TableColumn<AttendanceRow, ?>> columns = new ArrayList<>();
        columns.add(Ui.<AttendanceRow, Long>numberColumn("ID", "studentId", 55));
        columns.add(Ui.<AttendanceRow, String>column("Student", "studentName", 190));
        columns.add(Ui.<AttendanceRow, String>column("Section", "section", 85));
        columns.add(Ui.<AttendanceRow>statusColumn("Status", "status", 105));
        columns.add(Ui.<AttendanceRow, String>column("Time", "attendanceTime", 85));
        columns.add(Ui.<AttendanceRow, String>column("Marked by", "markedBy", 130));
        columns.add(Ui.<AttendanceRow>percentColumn("Confidence", "confidenceScore", 95));
        return columns;
    }
}
