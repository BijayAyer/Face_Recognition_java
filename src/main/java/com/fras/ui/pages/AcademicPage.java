package com.fras.ui.pages;

import com.fras.Refreshable;
import com.fras.ui.Async;
import com.fras.ui.Page;
import com.fras.ui.Ui;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Departments, semesters, subjects, classrooms, timetable.
 *
 * <p>These five screens already existed as FXML with their own controllers, and
 * rewriting them would have thrown away working code to gain nothing. They are
 * embedded as tabs, each told to reload when it becomes visible, and a tab that
 * fails to load says so in place instead of taking the whole page down with it.
 */
public final class AcademicPage extends Page {

    @Override
    public String title() {
        return "Academic setup";
    }

    @Override
    protected Node build() {
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                tab("Departments", "/department.fxml"),
                tab("Semesters", "/semester.fxml"),
                tab("Subjects", "/subject.fxml"),
                tab("Classrooms", "/classroom.fxml"),
                tab("Timetable", "/timetable.fxml"));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox page = Ui.column(Ui.GAP,
                Ui.pageHeader("Academic setup",
                        "The structure everything else refers to. A session needs a classroom "
                                + "and a subject to exist before it can be opened."),
                tabs);
        page.getStyleClass().add("page");
        return page;
    }

    /**
     * Loaded eagerly, refreshed on selection. Eager because a tab that has
     * never been loaded cannot report that it failed, and a blank tab is the
     * worst of the three outcomes.
     */
    private Tab tab(String label, String resource) {
        Tab tab = new Tab(label);
        tab.setClosable(false);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
            Parent content = loader.load();
            Object controller = loader.getController();
            tab.setContent(content);

            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected() && controller instanceof Refreshable refreshable) {
                    refreshable.refreshData();
                }
            });

        } catch (Exception failure) {
            tab.setContent(Ui.emptyState(
                    label + " could not be opened",
                    Async.describe(failure),
                    null));
        }

        return tab;
    }
}
