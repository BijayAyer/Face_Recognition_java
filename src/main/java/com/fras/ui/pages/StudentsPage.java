package com.fras.ui.pages;

import com.fras.app.dto.StudentRow;
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
 * The student roster.
 *
 * <p>The id column matters more here than it looks: it is also the name of the
 * folder a student's face template is enrolled into, which is the only reason a
 * recognised face can be turned into an attendance record. Enrolment says so
 * too, but this is where the number is read off.
 *
 * <p>The Account column is the one that answers a question people were
 * previously left to guess at. A roster row and a sign-in account are two
 * different records, and a student without an account cannot sign in to see
 * their own attendance. Rows added here start with no account; rows that came
 * from a sign-up have one. Saying which is which is cheaper than explaining it
 * afterwards.
 *
 * <p>Section is recorded here and read on the register. Nothing computes from
 * it - it is a label for a group, so a school can call one "A" and another
 * "BSc CSIT 3rd Sem" - which is why it is optional and why nothing here tries
 * to validate its shape beyond a length the column can hold.
 */
public final class StudentsPage extends RosterPage<StudentRow> {

    /** Matches the {@code @Size(max = 40)} on the server's Student.section. */
    private static final int SECTION_MAX = 40;

    /** Matches Student.AGE_MAX, the upper bound the entity validates. */
    private static final int AGE_MAX = 120;

    private static final String SECTION_HELP = "Optional. The group this student is in, as it "
            + "should read on the register.";

    public StudentsPage(ApiService api) {
        super(api);
    }

    @Override
    protected String noun() {
        return "student";
    }

    @Override
    protected String purpose() {
        return "The roster attendance is recorded against. The ID is the number to enrol a face under.";
    }

    @Override
    protected List<StudentRow> fetch() throws Exception {
        return JsonTableUtil.parseList(api.getStudents(), StudentRow.class);
    }

    /**
     * Age, Section and Account are text columns rather than numeric ones on
     * purpose. Each carries a value the raw data cannot say: an age of 0 means
     * nobody has recorded one, a missing section means nobody has put this
     * student in a group, and a missing account link means nobody can sign in as
     * this student. Words also survive the black-and-white palette, which a
     * coloured badge would not.
     *
     * <p>Section reads the same "-" here as it does on the register, because the
     * two screens are looking at one field and should not disagree about how an
     * absent value looks.
     */
    @Override
    protected List<TableColumn<StudentRow, ?>> columns() {
        return List.of(
                Ui.numberColumn("ID", "id", 70),
                Ui.column("Name", "name", 200),
                Ui.column("Email", "email", 230),
                Ui.column("Section", "sectionLabel", 100),
                Ui.column("Age", "ageLabel", 100),
                Ui.column("Account", "account", 100));
    }

    @Override
    protected boolean matches(StudentRow row, String term) {
        return contains(row.getName(), term) || contains(row.getEmail(), term)
                || contains(row.getSection(), term)
                || String.valueOf(row.getId()).equals(term);
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    @Override
    protected String describe(StudentRow row) {
        String name = row.getName() == null || row.getName().isBlank() ? "This student" : row.getName();
        return name + " (ID " + row.getId() + ")";
    }

    @Override
    protected void delete(StudentRow row) throws Exception {
        api.deleteStudent(row.getId());
    }

    /**
     * Says both rules before the button is pressed rather than after.
     *
     * <p>A student with attendance on file cannot be deleted, because the records
     * keep the student id as a plain number and would be left attributable to
     * nobody. And the face samples are not the server's to delete: they sit in
     * {@code data/faces/<id>} beside this client, so removing the roster row
     * leaves them there. Nobody should have to guess that.
     */
    @Override
    protected String removalConsequence(StudentRow row) {
        return describe(row) + " will be removed from the roster. A student who "
                + "already has attendance on file cannot be deleted. Face samples "
                + "enrolled under this ID are left on disk.";
    }

    @Override
    protected Long identityOf(StudentRow row) {
        return row.getId();
    }

    @Override
    protected void openAddForm(Runnable reload) {
        FormDialog form = new FormDialog(
                view().getScene() == null ? null : view().getScene().getWindow(),
                "Add student",
                "The ID the server assigns is what a face is enrolled under.",
                "Add student");

        Field name = form.add(new Field("Full name", Ui.input("Ada Lovelace", 300)));
        Field email = form.add(new Field("Email address", Ui.input("you@school.edu", 300)));
        Field section = form.add(new Field("Section", Ui.input("A", 120), SECTION_HELP));
        Field age = form.add(new Field("Age", Ui.input("18", 120)));

        form.showAndSave(
                () -> valid(name, email, section, age),
                () -> api.createStudent(name.text(), email.text(),
                        Math.max(0, parseAge(age.text())), section.text()),
                () -> {
                    Toast.ok(name.text() + " was added.");
                    reload.run();
                });
    }

    /**
     * Editing a student rather than deleting and re-adding one.
     *
     * <p>Which matters more here than on most rosters: the id is the folder a
     * face is enrolled under, so a re-added student is a student whose enrolled
     * face now belongs to a number nobody uses. And a student with attendance on
     * file cannot be deleted at all, so before this the only way to correct a
     * name was to leave it wrong.
     */
    @Override
    protected void openEditForm(StudentRow row, Runnable reload) {
        FormDialog form = new FormDialog(
                view().getScene() == null ? null : view().getScene().getWindow(),
                "Edit student",
                "ID " + row.getId() + ". The ID never changes, so enrolled faces "
                        + "and recorded attendance stay with this student.",
                "Save changes");

        Field name = form.add(new Field("Full name", Ui.input("Ada Lovelace", 300, row.getName())));
        Field email = form.add(new Field("Email address",
                Ui.input("you@school.edu", 300, row.getEmail())));
        Field section = form.add(new Field("Section",
                Ui.input("A", 120, row.getSection()), SECTION_HELP));
        // 0 means "not recorded", and printing it would turn a blank into a
        // claim the moment somebody saved an unrelated change.
        Field age = form.add(new Field("Age",
                Ui.input("18", 120, row.getAge() > 0 ? String.valueOf(row.getAge()) : "")));

        form.showAndSave(
                () -> valid(name, email, section, age),
                () -> api.updateStudent(row.getId(), name.text(), email.text(),
                        Math.max(0, parseAge(age.text())), section.text()),
                () -> {
                    Toast.ok(name.text() + " was saved.");
                    reload.run();
                });
    }

    /**
     * The rules both forms share. Name and email are required; section and age
     * are optional but cannot be nonsense when filled in.
     *
     * <p>The two bounds are the server's own, checked here as well so a rejected
     * value lands under the field it belongs to instead of arriving as a toast
     * about a constraint name.
     */
    private static boolean valid(Field name, Field email, Field section, Field age) {
        if (!FormDialog.requireAll(name, email)) {
            return false;
        }
        if (section.text().length() > SECTION_MAX) {
            section.reject("A section can be at most " + SECTION_MAX + " characters.");
            section.focus();
            return false;
        }
        int years = parseAge(age.text());
        if (!age.isEmpty() && (years < 0 || years > AGE_MAX)) {
            age.reject("Age has to be a whole number up to " + AGE_MAX + ", or left blank.");
            age.focus();
            return false;
        }
        return true;
    }

    /** -1 for "that is not a number", so the caller can tell it apart from 0. */
    private static int parseAge(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int age = Integer.parseInt(value.trim());
            return age < 0 ? -1 : age;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}
