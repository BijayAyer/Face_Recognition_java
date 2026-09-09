package com.fras.ui.pages;

import com.fras.app.dto.TeacherRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.service.ApiService;
import com.fras.ui.Field;
import com.fras.ui.FormDialog;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.scene.control.TableColumn;

import java.util.List;
import java.util.Locale;

/**
 * The teaching staff, and which subject each of them takes.
 *
 * <p>A staff row and a sign-in account are two records, and the Account column
 * says whether this row has one. Creating a teacher account now writes the
 * staff row too - with the subject left as "Unassigned", because sign-up has
 * nowhere to ask - so a row reading Linked and Unassigned is somebody who has
 * signed up and is waiting for a subject to be set here.
 */
public final class TeachersPage extends RosterPage<TeacherRow> {

    public TeachersPage(ApiService api) {
        super(api);
    }

    @Override
    protected String noun() {
        return "teacher";
    }

    @Override
    protected String purpose() {
        return "Who teaches what. Rows marked Unassigned came from a sign-up and still need a subject.";
    }

    @Override
    protected List<TeacherRow> fetch() throws Exception {
        return JsonTableUtil.parseList(api.getTeachers(), TeacherRow.class);
    }

    @Override
    protected List<TableColumn<TeacherRow, ?>> columns() {
        return List.of(
                Ui.numberColumn("ID", "id", 70),
                Ui.column("Name", "name", 200),
                Ui.column("Email", "email", 230),
                Ui.column("Subject", "subject", 160),
                Ui.column("Account", "account", 110));
    }

    @Override
    protected boolean matches(TeacherRow row, String term) {
        return contains(row.getName(), term)
                || contains(row.getEmail(), term)
                || contains(row.getSubject(), term);
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    @Override
    protected String describe(TeacherRow row) {
        String name = row.getName() == null || row.getName().isBlank() ? "This teacher" : row.getName();
        return name + " (ID " + row.getId() + ")";
    }

    @Override
    protected void delete(TeacherRow row) throws Exception {
        api.deleteTeacher(row.getId());
    }

    @Override
    protected Long identityOf(TeacherRow row) {
        return row.getId();
    }

    @Override
    protected void openAddForm(Runnable reload) {
        FormDialog form = new FormDialog(
                view().getScene() == null ? null : view().getScene().getWindow(),
                "Add teacher",
                null,
                "Add teacher");

        Field name = form.add(new Field("Full name", Ui.input("Grace Hopper", 300)));
        Field email = form.add(new Field("Email address", Ui.input("you@school.edu", 300)));
        Field subject = form.add(new Field("Subject taught", Ui.input("Mathematics", 300)));

        // Subject is NOT NULL on the server, so a blank one comes back as a 400
        // with the message attached to nothing in particular. Asking for it here
        // puts the complaint under the field it is about.
        form.showAndSave(
                () -> FormDialog.requireAll(name, email, subject),
                () -> api.createTeacher(name.text(), email.text(), subject.text()),
                () -> {
                    Toast.ok(name.text() + " was added.");
                    reload.run();
                });
    }

    /**
     * Where an Unassigned row gets its subject.
     *
     * <p>Sign-up has nowhere to ask which subject somebody teaches, so every
     * teacher who signs up arrives here reading "Unassigned". Until now the only
     * way to change that was to remove the row and add it again, which throws
     * away the link to their sign-in account - the new row is a different id, and
     * nothing re-attaches the account to it - so the word stayed on screen with
     * nothing safe to do about it.
     */
    @Override
    protected void openEditForm(TeacherRow row, Runnable reload) {
        FormDialog form = new FormDialog(
                view().getScene() == null ? null : view().getScene().getWindow(),
                "Edit teacher",
                "ID " + row.getId() + ".",
                "Save changes");

        Field name = form.add(new Field("Full name", Ui.input("Grace Hopper", 300, row.getName())));
        Field email = form.add(new Field("Email address",
                Ui.input("you@school.edu", 300, row.getEmail())));
        Field subject = form.add(new Field("Subject taught",
                Ui.input("Mathematics", 300, row.getSubject())));

        form.showAndSave(
                () -> FormDialog.requireAll(name, email, subject),
                () -> api.updateTeacher(row.getId(), name.text(), email.text(), subject.text()),
                () -> {
                    Toast.ok(name.text() + " was saved.");
                    reload.run();
                });
    }
}
