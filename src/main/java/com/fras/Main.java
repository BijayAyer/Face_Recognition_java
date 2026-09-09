package com.fras;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
                createTab("Departments", "/department.fxml"),
                createTab("Semesters", "/semester.fxml"),
                createTab("Subjects", "/subject.fxml"),
                createTab("Classrooms", "/classroom.fxml"),
                createTab("Timetable", "/timetable.fxml")
        );

        Scene scene = new Scene(tabPane, 1000, 650);

        stage.setTitle("Face Recognition Attendance System");
        stage.setScene(scene);
        stage.show();
    }

    private Tab createTab(String title, String resource) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
        Parent content = loader.load();
        Object controller = loader.getController();

        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        tab.setOnSelectionChanged(event -> {
            if (tab.isSelected() && controller instanceof Refreshable refreshable) {
                refreshable.refreshData();
            }
        });
        return tab;
    }

    public static void main(String[] args) {
        launch(args);
    }

    public interface Refreshable {
        void refreshData();
    }
}
