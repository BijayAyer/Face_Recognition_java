package com.fras.ui.pages;

import com.fras.service.ApiService;
import com.fras.ui.Async;
import com.fras.ui.Page;
import com.fras.ui.Toast;
import com.fras.ui.Ui;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The shape both people lists share: fetch a list, show it, filter it, add to
 * it, edit a row of it, delete from it.
 *
 * <p>Students and teachers were two near-identical hundred-line methods, which
 * meant every fix had to be made twice and one of them was always missed - both
 * copies caught only {@link ApiService.ApiException}, so a malformed payload
 * killed the loading thread and left the button disabled forever. One base
 * class, two small subclasses, one place to fix.
 *
 * @param <R> the row type this page lists
 */
public abstract class RosterPage<R> extends Page {

    protected final ApiService api;

    private final ObservableList<R> master = FXCollections.observableArrayList();
    private final FilteredList<R> visible = new FilteredList<>(master, row -> true);

    private final TableView<R> table = new TableView<>();
    private final TextField searchField;
    private final Button refresh = Ui.secondary("Refresh");
    private final Button add;
    private final Button edit = Ui.secondary("Edit");
    private final Button remove = Ui.danger("Remove");
    private final Label count = Ui.hint("");

    protected RosterPage(ApiService api) {
        this.api = api;
        // Not "by name or email": the two pages match on different fields -
        // students on section too, teachers on subject - and a prompt that lists
        // the wrong ones is worse than one that lists none.
        this.searchField = Ui.search("Search " + noun() + "s", 260);
        this.add = Ui.primary("Add " + capitalised(noun()));
    }

    // =========================================================
    // WHAT A SUBCLASS HAS TO SAY
    // =========================================================

    /** Singular, lower case: "student". Used in every label on the page. */
    protected abstract String noun();

    /** One line under the title explaining what the list is for. */
    protected abstract String purpose();

    /** Fetches the list. Runs off the FX thread. */
    protected abstract List<R> fetch() throws Exception;

    /** The columns, in order. */
    protected abstract List<TableColumn<R, ?>> columns();

    /** True when the row matches a lower-cased search term. */
    protected abstract boolean matches(R row, String term);

    /** How a row names itself in a confirmation dialog. */
    protected abstract String describe(R row);

    /**
     * The server id of a row, or null for one that has none. Used to find the
     * same record again in a freshly fetched list.
     */
    protected abstract Long identityOf(R row);

    /** Deletes the row on the server. Runs off the FX thread. */
    protected abstract void delete(R row) throws Exception;

    /** Opens the create form. Calls {@code reload} once something was created. */
    protected abstract void openAddForm(Runnable reload);

    /**
     * Opens the edit form for one row. Calls {@code reload} once the server has
     * accepted the change.
     *
     * <p>Until now there was no way to change a row that was already there: a
     * mistyped email or a student who moved section meant deleting the row and
     * adding it again, which is not available for a student with attendance on
     * file - the server refuses that delete - and would lose the id a face is
     * enrolled under. The update endpoints existed the whole time and nothing
     * called them.
     */
    protected abstract void openEditForm(R row, Runnable reload);

    /** Extra buttons for the header, right of Add. Empty by default. */
    protected Node[] extraActions() {
        return new Node[0];
    }

    /**
     * What the confirmation dialog says will happen.
     *
     * <p>Overridable because the two rosters are not equally safe to delete
     * from. This used to be one sentence for both, and it read "Attendance
     * already recorded against this student is not deleted" - which was true,
     * and was the bug: the rows survived pointing at a student id that no longer
     * existed, so they counted towards totals while belonging to nobody. The
     * server refuses that delete now, and {@link StudentsPage} says so here
     * rather than letting the user find out by pressing the button.
     */
    protected String removalConsequence(R row) {
        return describe(row) + " will be removed from the roster.";
    }

    // =========================================================
    // THE PAGE
    // =========================================================

    @Override
    public String title() {
        return capitalised(noun()) + "s";
    }

    @Override
    protected Node build() {
        table.getColumns().setAll(columns());
        table.setPlaceholder(Ui.emptyState(
                "No " + noun() + "s yet",
                "Add the first " + noun() + " to start building the roster.",
                null));
        Ui.stretch(table);

        SortedList<R> sorted = new SortedList<>(visible);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        searchField.textProperty().addListener((observable, before, after) -> applyFilter(after));

        refresh.setOnAction(event -> reload());
        add.setOnAction(event -> openAddForm(this::reload));

        // Editing and removing are only offered once there is something
        // selected, so neither button is ever a question about which row it
        // means.
        edit.setDisable(true);
        remove.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, before, after) -> {
                    edit.setDisable(after == null);
                    remove.setDisable(after == null);
                });
        edit.setOnAction(event -> editSelected());
        remove.setOnAction(event -> confirmRemove());

        // Double-clicking a row opens it, which is what a list of records
        // usually does. The selection is checked rather than assumed because a
        // double-click can land on the empty space under the last row.
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                editSelected();
            }
        });

        Node[] extras = extraActions();
        Node[] actions = new Node[3 + extras.length];
        actions[0] = searchField;
        actions[1] = add;
        actions[2] = refresh;
        System.arraycopy(extras, 0, actions, 3, extras.length);

        HBox footer = Ui.row(count, Ui.growH(), edit, remove);

        VBox body = Ui.column(Ui.GAP, table, footer);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = Ui.column(Ui.GAP,
                Ui.pageHeader(capitalised(noun()) + "s", purpose(), actions),
                Ui.panel(body));
        page.getStyleClass().add("page");
        VBox.setVgrow(page.getChildren().get(1), Priority.ALWAYS);
        return page;
    }

    @Override
    public void onShow() {
        // A list that was loaded ten minutes ago is not the list. Reloading on
        // every visit is why the old "Refresh List" button existed at all.
        reload();
    }

    private void applyFilter(String query) {
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Predicate<R> keep = term.isEmpty() ? row -> true : row -> matches(row, term);
        visible.setPredicate(keep);
        showCount();
    }

    private void showCount() {
        int shown = visible.size();
        int total = master.size();
        if (total == 0) {
            count.setText("");
        } else if (shown == total) {
            count.setText(total + " " + plural(total));
        } else {
            count.setText(shown + " of " + total + " " + plural(total));
        }
    }

    private String plural(int howMany) {
        return howMany == 1 ? noun() : noun() + "s";
    }

    public void reload() {
        reload(null);
    }

    /**
     * Fetches the list again and, when an id is given, selects the row carrying
     * it once the new rows are in.
     */
    public void reload(Long keepSelected) {
        Ui.busy(true, refresh, add);
        count.setText("Loading...");
        Async.run(this::fetch,
                rows -> {
                    Ui.busy(false, refresh, add);
                    master.setAll(rows);
                    applyFilter(searchField.getText());
                    if (keepSelected != null) {
                        reselect(keepSelected);
                    }
                },
                message -> {
                    Ui.busy(false, refresh, add);
                    count.setText("Could not load the list.");
                    Toast.error(message);
                });
    }

    /** Selects the row with this id, if the new list still has it. */
    private void reselect(Long id) {
        for (R row : table.getItems()) {
            if (id.equals(identityOf(row))) {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
                return;
            }
        }
    }

    /**
     * Opens the selected row for editing, keeping the selection afterwards.
     *
     * <p>{@link #reload} replaces every row with a freshly fetched one, so the
     * object that was selected is gone by the time the table redraws. The id is
     * remembered instead and the matching new row is selected again, or the
     * person loses their place in a list they were part-way through.
     */
    private void editSelected() {
        R row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        openEditForm(row, () -> reload(identityOf(row)));
    }

    private void confirmRemove() {
        R row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        boolean go = Ui.confirm(
                table.getScene() == null ? null : table.getScene().getWindow(),
                "Remove " + noun(),
                removalConsequence(row),
                "Remove");
        if (!go) {
            return;
        }

        Ui.busy(true, remove);
        Async.run(() -> delete(row),
                () -> {
                    Ui.busy(false, remove);
                    Toast.ok(describe(row) + " was removed.");
                    reload();
                },
                message -> {
                    Ui.busy(false, remove);
                    Toast.error(message);
                });
    }

    protected static String capitalised(String word) {
        return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }
}
