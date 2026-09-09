package com.fras.ui.pages;

import com.fras.app.dto.AttendanceRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.service.ApiService;
import com.fras.ui.Async;
import com.fras.ui.Page;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A student's own attendance, and nothing else.
 *
 * <p>Sign-up is public and, without a registration code, can only produce a
 * STUDENT - so this screen decides what a stranger who fills in the form can
 * see. Until now the answer was "the staff screens", because the shell only
 * distinguished admins from everybody else: a student got the class register,
 * the roster picker and the export buttons, all of which now correctly fail
 * with a 403. Showing somebody four screens that refuse to work is worse than
 * showing them one that does.
 *
 * <p>There is no student picker here on purpose. The page asks
 * {@code GET /attendance/me} and the server resolves which roster row the
 * login belongs to, so there is no id in the request for anyone to change.
 */
public final class MyAttendancePage extends Page {

    private final ApiService api;

    private final TableView<AttendanceRow> table = new TableView<>();
    private final Label note = Ui.hint("Loading your record...");
    private final Button refresh = Ui.secondary("Refresh");

    private final Label attendedValue = Ui.styledLabel("-", "metric-value");
    private final Label presentValue = Ui.styledLabel("-", "metric-value");
    private final Label lateValue = Ui.styledLabel("-", "metric-value");
    private final Label absentValue = Ui.styledLabel("-", "metric-value");

    public MyAttendancePage(ApiService api) {
        this.api = api;
    }

    @Override
    public String title() {
        return "My attendance";
    }

    @Override
    protected Node build() {
        refresh.setOnAction(event -> load());

        FlowPane tiles = new FlowPane(Ui.GAP, Ui.GAP,
                tile("Attended", attendedValue, "of the sessions on record"),
                tile("Present", presentValue, "marked on time"),
                tile("Late", lateValue, "counted as attended"),
                tile("Absent", absentValue, "did not attend"));

        table.getColumns().setAll(attendanceColumns());
        table.setPlaceholder(Ui.emptyState("Nothing recorded yet",
                "Your attendance appears here once a teacher has run a session you were in.",
                null));
        Ui.stretch(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        // The panel and the column inside it both have to grow, or the table
        // keeps its default height and the window shows a strip of rows with
        // empty space under it.
        VBox panelBody = Ui.column(Ui.GAP, note, table);
        VBox.setVgrow(panelBody, Priority.ALWAYS);
        VBox panel = Ui.panel(panelBody);
        VBox.setVgrow(panel, Priority.ALWAYS);

        VBox body = Ui.column(Ui.GAP,
                Ui.pageHeader("My attendance",
                        "Every session you have been marked in, most recent first.",
                        refresh),
                tiles,
                panel);
        return body;
    }

    /**
     * A metric tile whose value can change. {@link Ui#metricTile} takes the
     * value as a string and keeps no handle on the label, which is fine for a
     * fixed figure and no use for one that is filled in after a request comes
     * back.
     */
    private static VBox tile(String label, Label value, String hint) {
        VBox box = Ui.column(2, Ui.styledLabel(label, "metric-label"), value, Ui.hint(hint));
        box.getStyleClass().addAll("metric-tile", "tile-accent");
        box.setMinWidth(168);
        return box;
    }

    @Override
    public void onShow() {
        load();
    }

    private void load() {
        note.setText("Loading your record...");
        Ui.busy(true, refresh);

        Async.run(() -> JsonTableUtil.parseList(api.getMyAttendance(), AttendanceRow.class),
                rows -> {
                    Ui.busy(false, refresh);
                    table.setItems(FXCollections.observableArrayList(rows));
                    summarise(rows);
                },
                message -> {
                    Ui.busy(false, refresh);
                    table.getItems().clear();
                    reset();
                    // The likely failures here are both worth saying plainly:
                    // the account is not on the roster (the server explains
                    // that itself), or the backend is not running.
                    note.setText(message);
                    Toast.error(message);
                });
    }

    private void summarise(List<AttendanceRow> rows) {
        if (rows.isEmpty()) {
            reset();
            note.setText("Nothing recorded yet.");
            return;
        }

        long present = count(rows, "PRESENT");
        long late = count(rows, "LATE");
        long absent = count(rows, "ABSENT");
        // LATE still counts as having turned up; only ABSENT does not. The
        // denominator is every row on record, not present+late+absent, so a
        // status the server adds later cannot quietly inflate the percentage.
        long attended = present + late;
        long percent = Math.round((attended * 100.0) / rows.size());

        attendedValue.setText(percent + "%");
        presentValue.setText(String.valueOf(present));
        lateValue.setText(String.valueOf(late));
        absentValue.setText(String.valueOf(absent));

        note.setText(rows.size() + " sessions on record, " + attended + " attended.");
    }

    private void reset() {
        attendedValue.setText("-");
        presentValue.setText("-");
        lateValue.setText("-");
        absentValue.setText("-");
    }

    /** How many rows carry one status. */
    private static long count(List<AttendanceRow> rows, String status) {
        return rows.stream().filter(row -> status.equalsIgnoreCase(row.getStatus())).count();
    }

    /**
     * Same columns as the staff history table minus the student id: every row
     * here belongs to the same person, so a column repeating their id would be
     * one column of the same number.
     *
     * <p>The room and the subject are the names the server resolves, not the
     * ids it stores. A student reading "Room 2 | Subject 2" had no way to tell
     * which of their classes the line was about, which on the one screen a
     * student account can open is most of the screen's purpose.
     */
    private List<TableColumn<AttendanceRow, ?>> attendanceColumns() {
        List<TableColumn<AttendanceRow, ?>> columns = new ArrayList<>();
        columns.add(Ui.<AttendanceRow, String>column("Date", "attendanceDate", 115));
        columns.add(Ui.<AttendanceRow, String>column("Time", "attendanceTime", 95));
        columns.add(Ui.<AttendanceRow>statusColumn("Status", "status", 110));
        columns.add(Ui.<AttendanceRow, String>column("Subject", "subject", 170));
        columns.add(Ui.<AttendanceRow, String>column("Room", "room", 130));
        columns.add(Ui.<AttendanceRow, String>column("Marked by", "markedBy", 140));
        return columns;
    }
}
