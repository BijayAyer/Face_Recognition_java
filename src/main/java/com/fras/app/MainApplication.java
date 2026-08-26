package com.fras.app;

import com.fras.config.CameraService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainApplication extends Application {

    private final CameraService cameraService =
            new CameraService();

    @Override
    public void start(Stage stage) {

        ImageView imageView =
                new ImageView();

        imageView.setFitWidth(900);

        imageView.setFitHeight(550);

        imageView.setPreserveRatio(true);

        TextField studentIdField =
                new TextField();

        studentIdField.setPromptText(
                "Enter Student ID"
        );

        studentIdField.setPrefWidth(
                250
        );

        Label statusLabel =
                new Label(
                        "Camera starting..."
                );

        Button captureButton =
                new Button(
                        "Capture Face Sample"
                );

        captureButton.setOnAction(
                event -> {

                    String studentId =
                            studentIdField
                                    .getText()
                                    .trim();

                    if (
                            studentId.isEmpty()
                    ) {

                        statusLabel.setText(
                                "Enter a Student ID first."
                        );

                        return;
                    }

                    /*
                     * FIX: run capture + retrain on a
                     * background thread instead of
                     * blocking the JavaFX UI thread.
                     */
                    captureButton.setDisable(true);

                    statusLabel.setText(
                            "Capturing..."
                    );

                    cameraService.captureSampleAsync(
                            studentId,
                            saved -> {

                                captureButton.setDisable(false);

                                if (saved) {

                                    statusLabel.setText(
                                            "Face sample saved for "
                                                    + studentId
                                    );

                                } else {

                                    statusLabel.setText(
                                            "Capture failed. "
                                                    + "Keep exactly one clear face visible."
                                    );
                                }
                            }
                    );
                }
        );

        HBox controls =
                new HBox(
                        10,
                        studentIdField,
                        captureButton
                );

        controls.setAlignment(
                Pos.CENTER
        );

        controls.setPadding(
                new Insets(10)
        );

        VBox root =
                new VBox(
                        10,
                        imageView,
                        controls,
                        statusLabel
                );

        root.setAlignment(
                Pos.CENTER
        );

        root.setPadding(
                new Insets(10)
        );

        Scene scene =
                new Scene(
                        root,
                        900,
                        700
                );

        stage.setTitle(
                "Face Recognition Attendance System"
        );

        stage.setScene(
                scene
        );

        stage.show();

        cameraService.start(
                imageView
        );

        stage.setOnCloseRequest(
                event -> {

                    cameraService.stop();

                    Platform.exit();
                }
        );
    }
}