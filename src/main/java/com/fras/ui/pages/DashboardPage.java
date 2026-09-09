package com.fras.ui.pages;

import com.fras.app.dto.ClassroomRow;
import com.fras.app.dto.StudentRow;
import com.fras.app.dto.SubjectRow;
import com.fras.app.dto.TeacherRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.service.ApiService;
import com.fras.ui.AppShell;
import com.fras.ui.Async;
import com.fras.ui.Page;
import com.fras.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * The first thing you see: whether a register can be taken today, and the way
 * in to everything that can be done.
 *
 * <p>The app used to open on a grid of buttons with no information on it, so the
 * first question of every session - has the roster been loaded, are there rooms
 * and subjects to open a session against - was answered by visiting four screens
 * in turn. The counts answer it here, and a count of zero says what has to happen
 * rather than showing a zero.
 *
 * <p><b>Why this screen has no boxes on it.</b> It previously stacked four
 * bordered metric tiles, a bordered status panel and seven bordered 272px cards -
 * twelve outlined rectangles competing for the same glance, on the one screen
 * whose whole job is to be read at once. Everything here is now type on the page
 * separated by hairlines: one figure strip, one verdict sentence, and the
 * features as a plain list. Nothing has been dropped to achieve that, and the
 * list gained the sub-features that were previously only discoverable by opening
 * a page and looking - exports, the timetable, account administration.
 *
 * <p>The list is grouped under the same headings as the navigation rail, in the
 * same order, built from the same {@code api} predicates. Two lists of the same
 * pages in two different orders is how a person learns that one of them is
 * unreliable.
 */
public final class DashboardPage extends Page {

    private static final DateTimeFormatter TODAY =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

    private final ApiService api;
    private final AppShell shell;

    private final Figure students = new Figure("Students", "on the roster");
    private final Figure teachers = new Figure("Teachers", "on staff");
    private final Figure rooms = new Figure("Classrooms", "to sit a session in");
    private final Figure subjects = new Figure("Subjects", "to record against");

    private final Button refresh = Ui.secondary("Refresh");
    private final Label verdict = Ui.styledLabel("Checking...", "overview-verdict");

    /** Outstanding counts, so the verdict is written once they have all landed. */
    private int pending;

    /** The first failure of a reload, if any, kept for the verdict line. */
    private String problem;

    public DashboardPage(ApiService api, AppShell shell) {
        this.api = api;
        this.shell = shell;
    }

    @Override
    public String title() {
        return "Overview";
    }

    /**
     * A number, its name, and one line saying what it is for.
     *
     * <p>Kept as an object because the number arrives after the strip is on
     * screen, and it shows a dash until then - not a zero, which would read as
     * "there are none" while the request is still in the air.
     */
    private static final class Figure {

        private final Label value = Ui.styledLabel("--", "figure-value");
        private final VBox box;

        /** -1 until an answer arrives, so "none" and "not yet" stay distinct. */
        private int count = -1;

        Figure(String label, String note) {
            box = Ui.column(1,
                    value,
                    Ui.styledLabel(label, "figure-label"),
                    Ui.styledLabel(note, "figure-note"));
            box.setMinWidth(132);
        }

        Node node() {
            return box;
        }

        int count() {
            return count;
        }

        void set(int total) {
            count = total;
            value.setText(String.valueOf(total));
        }

        void unknown() {
            count = -1;
            value.setText("--");
        }
    }

    @Override
    protected Node build() {
        refresh.setOnAction(event -> reload());
        verdict.setWrapText(true);

        HBox figures = new HBox(38, students.node(), teachers.node(), rooms.node(), subjects.node());
        figures.setAlignment(Pos.BOTTOM_LEFT);
        figures.getStyleClass().add("figure-strip");

        VBox body = Ui.column(Ui.GAP,
                Ui.pageHeader("Overview", LocalDate.now().format(TODAY), refresh),
                figures,
                verdict,
                Ui.divider(),
                features(),
                footer());

        VBox page = Ui.column(0, scroll(body));
        page.getStyleClass().add("page");
        VBox.setVgrow(page.getChildren().get(0), Priority.ALWAYS);
        return page;
    }

    /**
     * Every page this account can open, in rail order, with what it is for.
     *
     * <p>Only pages this account can actually open are listed. A row that
     * answers a click with "you are not allowed to do that" is worse than no
     * row, and the rail hides the same entries - which is not a courtesy: the
     * server refuses roster reads, registers and exports to a student account,
     * so a student following one of these rows would collect a 403 per click.
     */
    private VBox features() {
        VBox list = Ui.column(0);

        group(list, "Today");
        if (api.canRecordAttendance()) {
            row(list, "live", "Live session",
                    "Open the camera on a room and a subject. Students are marked as they "
                            + "are recognised, and anyone the camera misses can be marked by hand.");
            row(list, "enroll", "Enrol a face",
                    "Capture samples for one student, so a session can recognise them. "
                            + "Nothing recognises anybody until this has been done once.");
        }
        row(list, "register", "Register",
                "One student across time with their attendance rate, or one room on one "
                        + "day. Either can be exported as Excel or PDF.");

        if (api.isAdmin()) {
            group(list, "People");
            row(list, "students", "Students",
                    "The roster attendance is recorded against. Add, edit and remove rows, "
                            + "and see which of them somebody can sign in as.");
            row(list, "teachers", "Teachers",
                    "Staff and the subject each one teaches. Rows marked Unassigned came "
                            + "from a sign-up and still need one.");

            group(list, "Structure");
            row(list, "academic", "Academic setup",
                    "Departments, the semesters inside them, the subjects inside those, "
                            + "classrooms, and the timetable that books a room for a period.");
        }

        group(list, "You");
        row(list, "account", "Account",
                api.isAdmin()
                        ? "Your own password. Also who can sign in: add an account, change "
                                + "what a role may do, or stop a login being used."
                        : "Your own password.");
        return list;
    }

    private void group(VBox list, String label) {
        Label heading = Ui.eyebrow(label);
        // No gap above the first group: it follows a divider that already
        // separates it from the verdict.
        VBox.setMargin(heading, new Insets(list.getChildren().isEmpty() ? 0 : 16, 0, 6, 0));
        list.getChildren().add(heading);
    }

    /**
     * One feature as a row of type rather than a card.
     *
     * <p>The keystroke printed on the right is the real one - it comes from the
     * shell, in rail order - so the printed key and the key that works cannot
     * drift apart. Rows past the ninth simply have no keystroke to print.
     */
    private void row(VBox list, String key, String label, String purpose) {
        String shortcut = shell.shortcutFor(key);

        Label purposeLabel = Ui.styledLabel(purpose, "overview-row-desc");
        purposeLabel.setWrapText(true);
        purposeLabel.setMaxWidth(560);

        VBox text = Ui.column(2, Ui.styledLabel(label, "overview-row-title"), purposeLabel);

        HBox inner = new HBox(14, text, Ui.growH());
        inner.setAlignment(Pos.CENTER_LEFT);
        if (shortcut != null) {
            inner.getChildren().add(Ui.styledLabel(shortcut, "overview-row-key"));
        }

        Button button = new Button();
        button.setGraphic(inner);
        button.getStyleClass().add("overview-row");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> shell.show(key));
        button.setAccessibleText(shortcut == null ? label : label + " (" + shortcut + ")");

        list.getChildren().add(button);
    }

    /** Which account this is and which server it is talking to, in one line. */
    private Node footer() {
        String role = api.getRole() == null || api.getRole().isBlank() ? "no role" : api.getRole();
        Label line = Ui.styledLabel(
                api.getDisplayName() + "  -  " + role + "  -  " + ApiService.baseUrl(),
                "overview-footer");
        VBox.setMargin(line, new Insets(18, 0, 0, 0));
        return line;
    }

    @Override
    public void onShow() {
        reload();
    }

    /**
     * One request per count rather than one for all of them. A failure then
     * costs one number instead of the whole screen, and each figure fills in as
     * its answer arrives.
     */
    private void reload() {
        Ui.busy(true, refresh);
        verdict.setText("Checking...");
        problem = null;
        pending = api.isAdmin() ? 4 : 3;

        count(() -> JsonTableUtil.parseList(api.getStudents(), StudentRow.class), students);
        count(() -> JsonTableUtil.parseList(api.getClassrooms(), ClassroomRow.class), rooms);
        count(() -> JsonTableUtil.parseList(api.getSubjects(null), SubjectRow.class), subjects);

        if (api.isAdmin()) {
            count(() -> JsonTableUtil.parseList(api.getTeachers(), TeacherRow.class), teachers);
        } else {
            // Not a failure and not a zero: this account cannot see that list.
            teachers.unknown();
        }
    }

    private <T> void count(Callable<List<T>> work, Figure figure) {
        Async.run(work,
                rows -> {
                    figure.set(rows.size());
                    settle();
                },
                message -> {
                    figure.unknown();
                    problem = message;
                    settle();
                });
    }

    /** Runs when the last outstanding count lands, whichever one that is. */
    private void settle() {
        pending--;
        if (pending > 0) {
            return;
        }
        Ui.busy(false, refresh);
        verdict.setText(problem == null
                ? readiness()
                : "Some of this could not be loaded. " + problem);
    }

    /**
     * The point of the screen. A zero is not reported as a zero - it is reported
     * as the thing that has to happen before a session can be opened at all.
     */
    private String readiness() {
        List<String> gaps = new ArrayList<>();
        if (rooms.count() == 0) {
            gaps.add("no classrooms");
        }
        if (subjects.count() == 0) {
            gaps.add("no subjects");
        }
        if (students.count() == 0) {
            gaps.add("nobody on the roster");
        }

        if (gaps.isEmpty()) {
            return "Ready. A session can be opened against any room and subject, and the "
                    + students.count() + " students on the roster can be marked as soon as "
                    + "their faces are enrolled.";
        }
        return "Not ready to take a register yet: " + sentence(gaps)
                + ". Classrooms and subjects are set up under Academic setup; the roster is "
                + "under Students.";
    }

    /** "a, b and c" - so the sentence reads as a sentence. */
    private static String sentence(List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                text.append(index == parts.size() - 1 ? " and " : ", ");
            }
            text.append(parts.get(index));
        }
        return text.toString();
    }
}
