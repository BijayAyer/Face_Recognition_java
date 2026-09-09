package com.fras.ui.pages;

import com.fras.app.dto.StudentRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.config.CameraService;
import com.fras.service.ApiService;
import com.fras.ui.Async;
import com.fras.ui.Page;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enrolling a face.
 *
 * <p>This screen is the reason auto-marking used to silently do nothing. The
 * old version asked for a "Student ID" in a free-text box, and whatever was
 * typed became the name of the folder the template was saved under - so folders
 * called {@code Bijaya} and {@code bijay} existed, live recognition parsed the
 * folder name with {@code Long.parseLong}, threw, and skipped the student
 * without a word. Here the student is chosen from the roster, so the label is
 * always the numeric id the attendance endpoint expects and it cannot be
 * anything else.
 */
public final class EnrollPage extends Page {

    private final ApiService api;
    private final CameraService camera;

    private final ImageView feed = new ImageView();
    private final ComboBox<StudentRow> picker = new ComboBox<>();
    private final Button capture = Ui.primary("Capture sample");
    private final Button reload = Ui.ghost("Reload roster");
    private final Region lamp = Ui.lamp();
    private final Label state = Ui.hint("Camera starting...");
    private final Label tally = Ui.styledLabel("0", "metric-value");
    private final Label tallyNote = Ui.hint("samples captured in this sitting");

    /** Samples taken per student while this page has been open. */
    private final Map<Long, Integer> taken = new HashMap<>();

    private boolean live;

    public EnrollPage(ApiService api, CameraService camera) {
        this.api = api;
        this.camera = camera;
    }

    @Override
    public String title() {
        return "Enrol a face";
    }

    @Override
    protected Node build() {
        feed.setFitWidth(720);
        feed.setFitHeight(405);
        feed.setPreserveRatio(true);
        StackPane housing = Ui.viewfinder(feed);

        picker.setPrefWidth(300);
        picker.setPromptText("Choose a student");
        picker.setPlaceholder(Ui.hint("No students on the roster yet."));
        picker.setButtonCell(studentCell());
        picker.setCellFactory(ignored -> studentCell());
        picker.valueProperty().addListener((observable, before, after) -> {
            capture.setDisable(after == null || !live);
            showTally();
        });

        capture.setDisable(true);
        capture.setOnAction(event -> captureOne());
        reload.setOnAction(event -> loadRoster());

        HBox status = Ui.row(lamp, state);
        state.setWrapText(true);

        VBox counter = Ui.column(0, tally, tallyNote);
        counter.getStyleClass().addAll("metric-tile", "tile-accent");
        counter.setMinWidth(190);

        VBox controls = Ui.column(Ui.GAP,
                Ui.column(5, Ui.fieldLabel("Student"), picker),
                Ui.row(capture, reload),
                status,
                counter,
                Ui.growV(),
                guidance());
        controls.setMinWidth(300);
        controls.setPrefWidth(300);
        controls.setMaxWidth(300);

        HBox split = new HBox(Ui.GAP, housing, controls);
        HBox.setHgrow(housing, Priority.ALWAYS);

        VBox page = Ui.column(Ui.GAP,
                Ui.pageHeader("Enrol a face",
                        "One short capture per student. Templates are stored under the student's "
                                + "numeric ID, which is what live recognition matches against."),
                Ui.panel(split));
        page.getStyleClass().add("page");
        VBox.setVgrow(page.getChildren().get(1), Priority.ALWAYS);
        return page;
    }

    /**
     * What actually makes recognition work, in the order it matters. This is
     * advice rather than decoration: the difference between one straight-on
     * sample and five is the difference between a recogniser that works in the
     * room it was enrolled in and one that works.
     */
    private VBox guidance() {
        VBox box = Ui.column(6,
                Ui.eyebrow("For a template that holds up"),
                bullet("Take five or more samples, turning the head slightly between each."),
                bullet("Keep one face in frame. A second face makes the capture ambiguous and it is refused."),
                bullet("Even, front-on light. Backlight from a window is the usual culprit."));
        return box;
    }

    private Label bullet(String text) {
        Label line = Ui.hint("- " + text);
        line.setWrapText(true);
        line.setMaxWidth(280);
        return line;
    }

    /** Name first, id second: the id is what is being enrolled, so it is shown. */
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

    // =========================================================
    // LIFECYCLE
    // =========================================================

    /**
     * The camera reports its own state now.
     *
     * <p>This used to light the lamp and say "Camera running" on the line
     * after calling {@code start}, before a single frame had arrived - so a
     * machine with no webcam, or one whose camera was held by another
     * application, showed a lit lamp over a black rectangle for ever. The
     * lamp follows {@link CameraService.Status} instead, which is only live once
     * frames are actually being read.
     */
    @Override
    public void onShow() {
        loadRoster();
        live = false;
        capture.setDisable(true);
        Ui.lampState(lamp, "idle");
        camera.start(feed, this::onCameraStatus);
    }

    /** On the JavaFX thread; see {@link CameraService#start(ImageView, java.util.function.Consumer)}. */
    private void onCameraStatus(CameraService.Status status) {
        live = status.live();
        Ui.lampState(lamp, status.live() ? "live" : "idle");
        state.setText(status.live()
                ? "Camera running. Choose a student, then capture."
                : status.message());
        capture.setDisable(!live || picker.getValue() == null);
    }

    /**
     * The camera is released the moment this page is left. It used to be
     * stopped by every other screen's builder instead, which is how it ended up
     * being stopped eleven times and released none.
     */
    @Override
    public void onHide() {
        camera.stop();
        live = false;
        Ui.lampState(lamp, "idle");
        state.setText("Camera stopped.");
        capture.setDisable(true);
    }

    // =========================================================
    // ROSTER
    // =========================================================

    private void loadRoster() {
        Ui.busy(true, reload);
        StudentRow chosen = picker.getValue();
        Async.run(() -> JsonTableUtil.parseList(api.getStudents(), StudentRow.class),
                rows -> {
                    Ui.busy(false, reload);
                    picker.getItems().setAll(rows);
                    restoreSelection(chosen, rows);
                },
                message -> {
                    Ui.busy(false, reload);
                    Toast.error(message);
                });
    }

    /** Keeps the person you were enrolling selected across a reload. */
    private void restoreSelection(StudentRow before, List<StudentRow> rows) {
        if (before == null) {
            return;
        }
        for (StudentRow row : rows) {
            if (row.getId() != null && row.getId().equals(before.getId())) {
                picker.setValue(row);
                return;
            }
        }
    }

    // =========================================================
    // CAPTURE
    // =========================================================

    private void captureOne() {
        StudentRow row = picker.getValue();
        if (row == null || row.getId() == null) {
            return;
        }

        long id = row.getId();
        capture.setDisable(true);
        state.setText("Capturing...");

        // The label is the numeric id, always. Nothing on this screen can make
        // it anything else.
        camera.captureSampleAsync(String.valueOf(id), result -> {
            capture.setDisable(!live || picker.getValue() == null);
            if (result.saved()) {
                taken.merge(id, 1, Integer::sum);
                showTally();
                Ui.lampState(lamp, "live");
                int count = taken.getOrDefault(id, 0);
                state.setText(count < 5
                        ? "Sample " + count + " saved. Turn the head slightly and take another."
                        : "Sample " + count + " saved. That is enough for a solid template.");
            } else {
                // The reason, not "nothing was saved". Step closer, ask the
                // second person to move out of shot, and check the disk are
                // three different actions, and the old screen gave the same
                // sentence for all of them while the real reason went to
                // standard output.
                Ui.lampState(lamp, "error");
                state.setText(result.message());
            }
        });
    }

    private void showTally() {
        StudentRow row = picker.getValue();
        int count = row == null || row.getId() == null ? 0 : taken.getOrDefault(row.getId(), 0);
        tally.setText(String.valueOf(count));
        tallyNote.setText(row == null
                ? "samples captured in this sitting"
                : "saved for " + (row.getName() == null || row.getName().isBlank()
                        ? "ID " + row.getId() : row.getName()));
    }
}
