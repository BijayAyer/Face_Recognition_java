package com.fras.ui.pages;

import com.fras.app.dto.StudentRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.config.CameraService;
import com.fras.face.RecognitionResult;
import com.fras.service.ApiService;
import com.fras.ui.Async;
import com.fras.ui.Page;
import com.fras.ui.SessionPicker;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Point the camera at the room and take the register.
 *
 * <p>Three things were wrong with the screen this replaces, and all three are
 * fixed here rather than papered over. The session was two numbers typed from
 * memory; it is now two lists. The roll was a text area of append-only log
 * lines, so "who is still missing" was a question you answered by reading; it
 * is now a strip of names that change state. And the mark was sent without the
 * session's start time, which made {@code LATE} unreachable and every arrival
 * {@code PRESENT} - the start time is now stamped when the session opens and
 * sent with every mark.
 */
public final class LivePage extends Page {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final ApiService api;
    private final CameraService camera;
    private final SessionPicker session;

    private final ImageView feed = new ImageView();
    private final Button startStop = Ui.primary("Open session");
    private final Button closeOut = Ui.secondary("Mark the rest absent");
    private final Region lamp = Ui.lamp();
    private final Label state = Ui.hint("Choose a room and a subject to open a session.");
    private final Label clock = Ui.mono("--:--");
    private final Label seen = Ui.styledLabel("0", "metric-value");
    private final Label ofRoster = Ui.hint("recognised");

    private final VBox roll = new VBox(6);
    private final Label rollNote = Ui.hint("The roster appears here once a session is open.");

    /** Row per student, so a name can be re-styled when it is recognised. */
    private final Map<Long, Label> rollEntries = new HashMap<>();

    /** Written from the camera thread, read from the FX thread. */
    private final Set<Long> marked = ConcurrentHashMap.newKeySet();

    private List<StudentRow> roster = Collections.emptyList();
    private volatile boolean running;
    private String startedAt;

    /** The camera's last reported state, so the session text can reflect it. */
    private boolean cameraLive;
    private String cameraMessage = "Starting the camera...";

    public LivePage(ApiService api, CameraService camera) {
        this.api = api;
        this.camera = camera;
        this.session = new SessionPicker(api);
    }

    @Override
    public String title() {
        return "Live session";
    }

    @Override
    protected Node build() {
        feed.setFitWidth(720);
        feed.setFitHeight(405);
        feed.setPreserveRatio(true);
        StackPane housing = Ui.viewfinder(feed);

        startStop.setMaxWidth(Double.MAX_VALUE);
        startStop.setDisable(true);
        startStop.setOnAction(event -> {
            if (running) {
                closeSession("Session closed.");
            } else {
                openSession();
            }
        });

        closeOut.setMaxWidth(Double.MAX_VALUE);
        closeOut.setDisable(true);
        closeOut.setOnAction(event -> markRemainingAbsent());

        session.onChange(this::syncStartButton);
        state.setWrapText(true);

        VBox tile = Ui.column(0, seen, ofRoster);
        tile.getStyleClass().addAll("metric-tile", "tile-accent");

        ScrollPane rollScroller = new ScrollPane(Ui.column(6, rollNote, roll));
        rollScroller.setFitToWidth(true);
        rollScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rollScroller.setPrefHeight(210);
        rollScroller.setFocusTraversable(false);

        VBox side = Ui.column(Ui.GAP,
                session.asColumn(),
                startStop,
                Ui.row(lamp, clock),
                state,
                tile,
                Ui.divider(),
                Ui.eyebrow("Roll call"),
                rollScroller,
                closeOut);
        side.setMinWidth(300);
        side.setPrefWidth(300);
        side.setMaxWidth(300);
        VBox.setVgrow(rollScroller, Priority.ALWAYS);

        HBox split = new HBox(Ui.GAP, housing, side);
        HBox.setHgrow(housing, Priority.ALWAYS);

        VBox page = Ui.column(Ui.GAP,
                Ui.pageHeader("Live session",
                        "Students are marked as they are recognised. Each one is marked once."),
                Ui.panel(split));
        page.getStyleClass().add("page");
        VBox.setVgrow(page.getChildren().get(1), Priority.ALWAYS);
        return page;
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void onShow() {
        session.load(false);
        loadRoster();
        cameraLive = false;
        cameraMessage = "Starting the camera...";
        camera.start(feed, this::onCameraStatus);
        Ui.lampState(lamp, running ? "live" : "idle");
        syncStartButton();
    }

    /**
     * The camera's own account of itself, on the JavaFX thread.
     *
     * <p>Worth showing even in the middle of a session - especially then. A
     * webcam unplugged halfway through a lesson used to leave this screen
     * claiming to be recognising, with a frozen picture and a register that had
     * quietly stopped growing. A paused session says so.
     */
    private void onCameraStatus(CameraService.Status status) {
        cameraLive = status.live();
        cameraMessage = status.message();

        if (!running) {
            syncStartButton();
            return;
        }
        if (cameraLive) {
            Ui.lampState(lamp, "live");
            state.setText("Recognising. " + session.describe() + ".");
        } else {
            Ui.lampState(lamp, "error");
            state.setText("The session is open but nobody can be recognised: " + cameraMessage);
        }
    }

    /**
     * Leaving the page ends the session. A recogniser that keeps marking
     * attendance for a room nobody is looking at is worse than one that stops.
     */
    @Override
    public void onHide() {
        // Detached first, so nothing can be marked against a session that is
        // being closed, and stopped before the session is closed so the closing
        // message is the last thing written to the status line.
        camera.setOnRecognized(null);
        camera.stop();
        if (running) {
            closeSession("Session closed because the screen was left.");
        }
        Ui.lampState(lamp, "idle");
    }

    private void syncStartButton() {
        startStop.setDisable(!running && !session.isReady());
        startStop.setText(running ? "Close session" : "Open session");
        if (running) {
            return;
        }
        if (!session.isReady()) {
            state.setText("Choose a room and a subject to open a session.");
        } else if (cameraLive) {
            state.setText("Ready. Opening the session stamps its start time, so late "
                    + "arrivals are recorded as late.");
        } else {
            // Deliberately not disabled: a teacher with a dead webcam can still
            // open the session and close it out by hand, and being told why the
            // picture is black is more use than a button that does nothing.
            state.setText(cameraMessage);
        }
    }

    // =========================================================
    // THE ROLL
    // =========================================================

    private void loadRoster() {
        Async.run(() -> JsonTableUtil.parseList(api.getStudents(), StudentRow.class),
                rows -> {
                    roster = rows;
                    rebuildRoll();
                },
                Toast::error);
    }

    /**
     * One line per student, dim until recognised. This is the signature element
     * of the screen: the answer to "who is still missing" should be readable at
     * a glance from across the room, not reconstructed from a scrolling log.
     */
    private void rebuildRoll() {
        roll.getChildren().clear();
        rollEntries.clear();

        if (roster.isEmpty()) {
            rollNote.setText("No students on the roster. Add students before opening a session.");
            showSeen();
            return;
        }

        rollNote.setText(roster.size() + " on the roster.");
        for (StudentRow row : roster) {
            if (row.getId() == null) {
                continue;
            }
            String name = row.getName() == null || row.getName().isBlank()
                    ? "ID " + row.getId() : row.getName();
            Label entry = Ui.styledLabel(name, "roll-entry");
            entry.setMaxWidth(Double.MAX_VALUE);
            if (marked.contains(row.getId())) {
                entry.getStyleClass().add("roll-entry-present");
            }
            rollEntries.put(row.getId(), entry);
            roll.getChildren().add(entry);
        }
        showSeen();
    }

    private void showSeen() {
        int total = roster.size();
        seen.setText(marked.size() + (total > 0 ? " / " + total : ""));
        ofRoster.setText(total > 0 ? "recognised of " + total : "recognised");
    }

    // =========================================================
    // THE SESSION
    // =========================================================

    private void openSession() {
        Long classroomId = session.classroomId();
        Long subjectId = session.subjectId();
        if (classroomId == null || subjectId == null) {
            return;
        }

        marked.clear();
        rebuildRoll();

        // Stamped once, here. The backend compares an arrival against this to
        // decide PRESENT or LATE; the old client never sent it, so nothing was
        // ever late.
        startedAt = LocalTime.now().format(CLOCK);
        running = true;

        session.lock(true);
        closeOut.setDisable(false);
        clock.setText("since " + startedAt);
        syncStartButton();

        // Reuses the camera-aware wording, so opening a session with no working
        // camera says so instead of claiming to be recognising.
        onCameraStatus(new CameraService.Status(cameraLive, cameraMessage));

        // Fires on the camera thread on purpose - the HTTP call below blocks,
        // and doing it here keeps the capture loop's scheduling out of it.
        camera.setOnRecognized(results -> onRecognised(results, classroomId, subjectId));
    }

    private void closeSession(String why) {
        camera.setOnRecognized(null);
        running = false;
        session.lock(false);
        closeOut.setDisable(marked.isEmpty());
        Ui.lampState(lamp, "idle");

        // Before the text, not after. syncStartButton rewrites the status line
        // whenever no session is open, so setting the closing message first - as
        // this did - meant it was overwritten on the next line and the teacher
        // never saw how many had been recognised.
        syncStartButton();
        state.setText(why + " " + marked.size() + " recognised.");
    }

    /**
     * Runs on the camera thread. Everything that touches a control is handed
     * back to the FX thread; the old version appended to a {@code TextArea}
     * from here, which is a scene-graph mutation off the FX thread and is the
     * kind of bug that shows up as a random crash weeks later.
     */
    private void onRecognised(List<RecognitionResult> results, long classroomId, long subjectId) {
        if (!running) {
            return;
        }

        for (RecognitionResult result : results) {
            long studentId;
            try {
                studentId = Long.parseLong(result.getStudentId());
            } catch (NumberFormatException notAnId) {
                // A template enrolled under a name rather than an id. Enrolment
                // can no longer produce one of these, but older data can.
                Platform.runLater(() -> Toast.warn(
                        "A face is enrolled under \"" + result.getStudentId()
                                + "\" instead of a student ID, so it cannot be marked. Re-enrol it."));
                continue;
            }

            if (!marked.add(studentId)) {
                continue;
            }

            double score = result.getScore();
            try {
                api.markAttendance(studentId, classroomId, subjectId,
                        "FACE_RECOGNITION", score, startedAt);
                Platform.runLater(() -> present(studentId, score));
            } catch (ApiService.ApiException refused) {
                // Nearly always "already recorded today", which is the expected
                // end state, not a failure worth stopping for.
                marked.remove(studentId);
                Platform.runLater(() -> note(studentId, refused.getMessage()));
            }
        }
    }

    private void present(long studentId, double score) {
        Label entry = rollEntries.get(studentId);
        if (entry != null && !entry.getStyleClass().contains("roll-entry-present")) {
            entry.getStyleClass().add("roll-entry-present");
            entry.setText(entry.getText() + "   " + Math.round(score * 100) + "%");
        }
        showSeen();
    }

    private void note(long studentId, String message) {
        Label entry = rollEntries.get(studentId);
        if (entry != null) {
            entry.setTooltip(new javafx.scene.control.Tooltip(message));
        }
        state.setText("Student " + studentId + ": " + message);
        showSeen();
    }

    /**
     * The other half of taking a register: everyone who was never recognised is
     * absent. Without this the day's record only ever contains the people who
     * turned up, and an empty row is indistinguishable from a missing one.
     */
    private void markRemainingAbsent() {
        Long classroomId = session.classroomId();
        Long subjectId = session.subjectId();
        if (classroomId == null || subjectId == null) {
            return;
        }

        List<Long> everyone = new ArrayList<>();
        for (StudentRow row : roster) {
            if (row.getId() != null) {
                everyone.add(row.getId());
            }
        }
        if (everyone.isEmpty()) {
            Toast.warn("There is no roster to mark against.");
            return;
        }

        int missing = everyone.size() - marked.size();
        boolean go = Ui.confirm(
                startStop.getScene() == null ? null : startStop.getScene().getWindow(),
                "Close out the register",
                missing + " of " + everyone.size() + " have not been recognised. They will be "
                        + "recorded as absent for " + session.describe() + " today.",
                "Mark absent");
        if (!go) {
            return;
        }

        Ui.busy(true, closeOut);
        String today = java.time.LocalDate.now().toString();
        Async.run(() -> api.markAbsentees(classroomId, subjectId, today, everyone),
                written -> {
                    Ui.busy(false, closeOut);
                    // The server's count, not "everyone minus everyone I saw".
                    // Those differ whenever somebody was already marked
                    // elsewhere - another device, another teacher, an earlier
                    // close-out - and reporting the local guess is how a
                    // register comes to claim thirty absences when eleven were
                    // written. A negative count means the server did not say.
                    String absent = written < 0 ? String.valueOf(missing) : String.valueOf(written);
                    Toast.ok("The register is closed out for today.");
                    state.setText("Register closed out. " + marked.size() + " present, "
                            + absent + " absent.");
                },
                message -> {
                    Ui.busy(false, closeOut);
                    Toast.error(message);
                });
    }
}
