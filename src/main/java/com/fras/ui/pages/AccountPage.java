package com.fras.ui.pages;

import com.fras.app.dto.UserRow;
import com.fras.app.util.JsonTableUtil;
import com.fras.service.ApiService;
import com.fras.ui.Async;
import com.fras.ui.Field;
import com.fras.ui.FormDialog;
import com.fras.ui.Page;
import com.fras.ui.PasswordBox;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Your own password, and - for an admin - who else can sign in.
 *
 * <p>Neither of these had a screen before. Changing a password meant asking
 * whoever ran the server to do it in the database, and the only way to create an
 * account was the public registration endpoint, which cannot hand out anything
 * but a {@code STUDENT} - so there was no supported way to make a second
 * teacher. Changing an existing account's role had no route at all except an
 * {@code UPDATE} statement, which is why the Role button is here too.
 *
 * <p>Creating an account here also creates the roster or staff row that goes
 * with it, so a new teacher appears on the Teachers page and a new student on
 * the roster. The dialog says so, because the previous behaviour - a login that
 * existed and a person who did not - is the thing being corrected.
 */
public final class AccountPage extends Page {

    private static final String[] ROLES = {"ADMIN", "TEACHER", "STUDENT"};

    private final ApiService api;

    private final Field current = new Field("Current password",
            new PasswordBox("The password you sign in with now", 300));
    private final Field fresh = new Field("New password",
            new PasswordBox("At least 8 characters", 300),
            "Eight characters or more. Longer beats complicated.");
    private final Field again = new Field("New password again",
            new PasswordBox("Type it a second time", 300));
    private final Button change = Ui.primary("Change password");
    private final Label changeNote = Ui.hint("");

    private final TableView<UserRow> users = new TableView<>();
    private final Button reloadUsers = Ui.secondary("Refresh");
    private final Button addUser = Ui.primary("Add account");
    private final Button changeRole = Ui.secondary("Change role");
    private final Button toggleUser = Ui.secondary("Disable");
    private final Label usersNote = Ui.hint("");

    public AccountPage(ApiService api) {
        this.api = api;
    }

    @Override
    public String title() {
        return "Account";
    }

    @Override
    protected Node build() {
        VBox body = Ui.column(Ui.GAP, identity(), passwordPanel());
        if (api.isAdmin()) {
            body.getChildren().add(usersPanel());
        }

        VBox page = Ui.column(Ui.GAP,
                Ui.pageHeader("Account",
                        api.isAdmin()
                                ? "Your password, and who else can sign in."
                                : "Your password."),
                body);
        VBox.setVgrow(body, Priority.ALWAYS);
        page.getStyleClass().add("page");
        return page;
    }

    /** Who you are, stated plainly, because the rail only has room for a name. */
    private Node identity() {
        String role = api.getRole();
        HBox chips = Ui.row(
                Ui.chip(role == null || role.isBlank() ? "NO ROLE" : role),
                Ui.chip(api.getEmail() == null ? "no email" : api.getEmail()));

        return Ui.panel(Ui.column(8,
                Ui.eyebrow("Signed in"),
                Ui.heading(api.getDisplayName()),
                chips));
    }

    private Node passwordPanel() {
        change.setOnAction(event -> submitPassword());
        changeNote.setWrapText(true);

        VBox fields = Ui.column(Ui.GAP, current, fresh, again);
        fields.setMaxWidth(320);

        return Ui.panel("Change your password",
                "The new password takes effect immediately. Your current session stays "
                        + "signed in, so nothing has to be restarted.",
                Ui.column(Ui.GAP, fields, Ui.row(change, changeNote)));
    }

    /**
     * Checked here as well as on the server. Not because the client is trusted -
     * it is not - but because a round trip to be told "those do not match" is a
     * round trip that did not have to happen.
     */
    private void submitPassword() {
        boolean ok = current.require("Enter the password you sign in with now.")
                & fresh.require("Enter a new password.")
                & again.require("Type the new password a second time.");
        if (!ok) {
            return;
        }

        String next = fresh.raw();
        if (next.length() < 8) {
            fresh.reject("Eight characters or more.");
            fresh.focus();
            return;
        }
        if (!next.equals(again.raw())) {
            again.reject("This does not match the new password.");
            again.focus();
            return;
        }
        if (next.equals(current.raw())) {
            fresh.reject("This is the password you already have.");
            fresh.focus();
            return;
        }
        again.accept();

        changeNote.setText("");
        Ui.busy(true, change, current, fresh, again);
        String existing = current.raw();
        Async.run(() -> api.changePassword(existing, next),
                () -> {
                    Ui.busy(false, change, current, fresh, again);
                    current.clear();
                    fresh.clear();
                    again.clear();
                    changeNote.setText("Changed.");
                    Toast.ok("Your password has been changed.");
                },
                message -> {
                    Ui.busy(false, change, current, fresh, again);
                    changeNote.setText("");
                    current.reject(message);
                    current.focus();
                });
    }

    // =========================================================
    // ACCOUNTS (ADMIN ONLY)
    // =========================================================

    private Node usersPanel() {
        users.getColumns().setAll(userColumns());
        users.setPlaceholder(Ui.emptyState("No accounts listed",
                "Only an admin can see this list. If it is empty, the request did not "
                        + "reach the server.", null));
        Ui.stretch(users);
        users.setPrefHeight(260);

        reloadUsers.setOnAction(event -> loadUsers());
        addUser.setOnAction(event -> openAddUser());

        toggleUser.setDisable(true);
        toggleUser.setOnAction(event -> flipEnabled());
        changeRole.setDisable(true);
        changeRole.setOnAction(event -> openRoleChange());
        users.getSelectionModel().selectedItemProperty().addListener(
                (observable, before, after) -> {
                    toggleUser.setDisable(after == null);
                    changeRole.setDisable(after == null);
                    toggleUser.setText(after != null && !after.isEnabled() ? "Enable" : "Disable");
                });

        VBox body = Ui.column(Ui.GAP,
                Ui.row(addUser, reloadUsers, Ui.growH(), changeRole, toggleUser),
                users,
                usersNote);
        VBox.setVgrow(users, Priority.ALWAYS);

        VBox panel = Ui.panel("Who can sign in",
                "Select an account to change what it may do, or to stop it signing in. "
                        + "Disabling keeps the attendance history. There is no delete, on purpose.",
                body);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private List<TableColumn<UserRow, ?>> userColumns() {
        List<TableColumn<UserRow, ?>> columns = new ArrayList<>();
        columns.add(Ui.<UserRow, Long>numberColumn("ID", "id", 55));
        columns.add(Ui.<UserRow, String>column("Name", "fullName", 190));
        columns.add(Ui.<UserRow, String>column("Email", "email", 230));
        columns.add(Ui.<UserRow, String>column("Role", "role", 100));
        columns.add(Ui.<UserRow, String>column("State", "stateLabel", 90));
        columns.add(Ui.<UserRow, String>column("Last signed in", "lastSeen", 120));
        return columns;
    }

    @Override
    public void onShow() {
        if (api.isAdmin()) {
            loadUsers();
        }
    }

    private void loadUsers() {
        Ui.busy(true, reloadUsers, addUser);
        usersNote.setText("Loading...");
        Async.run(() -> JsonTableUtil.parseList(api.getUsers(), UserRow.class),
                rows -> {
                    Ui.busy(false, reloadUsers, addUser);
                    users.setItems(FXCollections.observableArrayList(rows));
                    long disabled = rows.stream().filter(row -> !row.isEnabled()).count();
                    usersNote.setText(rows.size() + " accounts"
                            + (disabled == 0 ? "." : ", " + disabled + " disabled."));
                },
                message -> {
                    Ui.busy(false, reloadUsers, addUser);
                    usersNote.setText("");
                    Toast.error(message);
                });
    }

    /**
     * Promote or demote the selected account.
     *
     * <p>The consequence is spelled out rather than implied. Changing a role
     * creates the row the new role needs and leaves the old one alone, because
     * a student row carries attendance history and the id a face was enrolled
     * under - throwing it away to keep the tables tidy would delete records the
     * role change has nothing to do with.
     */
    private void openRoleChange() {
        UserRow row = users.getSelectionModel().getSelectedItem();
        if (row == null || row.getId() == null) {
            return;
        }

        ComboBox<String> role = new ComboBox<>();
        role.getItems().setAll(ROLES);
        role.setValue(row.getRole() == null ? "STUDENT" : row.getRole().toUpperCase(Locale.ROOT));
        role.setPrefWidth(300);

        FormDialog dialog = new FormDialog(
                changeRole.getScene() == null ? null : changeRole.getScene().getWindow(),
                "Change role",
                row.label() + " is currently " + row.getRole() + ". The new role takes effect the "
                        + "next time they sign in.",
                "Change role");

        dialog.add(new Field("Role", role,
                "STUDENT can see their own attendance. TEACHER can record it. ADMIN can also "
                        + "manage accounts. A record for the new role is created if there is not "
                        + "one; the old one is kept, along with its attendance."));

        long id = row.getId();
        dialog.showAndSave(
                () -> {
                    if (role.getValue() == null || role.getValue().isBlank()) {
                        Toast.error("Choose a role.");
                        return false;
                    }
                    return true;
                },
                () -> api.setUserRole(id, role.getValue()),
                () -> {
                    Toast.ok(row.label() + " is now " + role.getValue() + ".");
                    loadUsers();
                });
    }

    /**
     * One button for both directions, because an account is either usable or it
     * is not and there is no third state to choose between.
     */
    private void flipEnabled() {
        UserRow row = users.getSelectionModel().getSelectedItem();
        if (row == null || row.getId() == null) {
            return;
        }
        boolean enable = !row.isEnabled();

        if (!enable) {
            boolean go = Ui.confirm(
                    toggleUser.getScene() == null ? null : toggleUser.getScene().getWindow(),
                    "Disable this account",
                    row.label() + " will not be able to sign in. Their attendance history is "
                            + "kept, and the account can be enabled again.",
                    "Disable");
            if (!go) {
                return;
            }
        }

        long id = row.getId();
        Ui.busy(true, toggleUser);
        Async.run(() -> api.setUserEnabled(id, enable),
                ignored -> {
                    Ui.busy(false, toggleUser);
                    Toast.ok(row.label() + (enable ? " can sign in again." : " is disabled."));
                    loadUsers();
                },
                message -> {
                    Ui.busy(false, toggleUser);
                    Toast.error(message);
                });
    }

    /**
     * The only way to create anything other than a student without handing out
     * a registration code. Public sign-up refuses a teacher or admin request,
     * which is correct for a public endpoint and no help at all when what you
     * need is a second teacher.
     */
    private void openAddUser() {
        ComboBox<String> role = new ComboBox<>();
        role.getItems().setAll(ROLES);
        role.setValue("TEACHER");
        role.setPrefWidth(300);

        FormDialog dialog = new FormDialog(
                addUser.getScene() == null ? null : addUser.getScene().getWindow(),
                "Add an account",
                "The person can change this password once they have signed in.",
                "Create account");

        Field name = dialog.add(new Field("Full name", Ui.input("Their name", 300)));
        Field email = dialog.add(new Field("Email address",
                Ui.input("what.they@sign.in", 300)));
        Field secret = dialog.add(new Field("Temporary password",
                new PasswordBox("At least 8 characters", 300),
                "They will need this once, to sign in and change it."));
        dialog.add(new Field("Role", role,
                "TEACHER can record attendance and appears on the Teachers page. "
                        + "STUDENT is added to the roster, ready for a face to be enrolled. "
                        + "ADMIN can also manage accounts, and is on neither list."));

        dialog.showAndSave(
                () -> {
                    boolean ok = FormDialog.requireAll(name, email, secret);
                    if (!ok) {
                        return false;
                    }
                    if (!email.text().contains("@") || email.text().startsWith("@")
                            || email.text().endsWith("@")) {
                        email.reject("That does not look like an email address.");
                        email.focus();
                        return false;
                    }
                    if (secret.raw().length() < 8) {
                        secret.reject("Eight characters or more.");
                        secret.focus();
                        return false;
                    }
                    return true;
                },
                () -> api.createUser(name.text(), email.text(), secret.raw(),
                        role.getValue().toUpperCase(Locale.ROOT)),
                () -> {
                    Toast.ok(name.text() + " can now sign in.");
                    loadUsers();
                });
    }
}
