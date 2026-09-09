package com.fras.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The vocabulary the whole client is built from.
 *
 * <p>Every screen used to assemble its own labels, toolbars and tables by
 * hand, which is why no two pages had the same spacing, the same heading
 * size, or the same idea of where the primary action belonged. These are the
 * shared parts: build a page out of them and it matches everything else by
 * construction.
 */
public final class Ui {

    /** The one gap value the layouts are tuned to; multiples of it read as deliberate. */
    public static final double GAP = 14;

    private Ui() {
    }

    // =========================================================
    // TEXT
    // =========================================================

    public static Label title(String text) {
        return styled(new Label(text), "page-title");
    }

    public static Label heading(String text) {
        return styled(new Label(text), "panel-title");
    }

    /** Small bold label above a group of controls. */
    public static Label eyebrow(String text) {
        return styled(new Label(text), "section-label");
    }

    public static Label hint(String text) {
        Label label = styled(new Label(text), "hint-text");
        label.setWrapText(true);
        return label;
    }

    public static Label fieldLabel(String text) {
        return styled(new Label(text), "field-label");
    }

    public static Label mono(String text) {
        return styled(new Label(text), "mono");
    }

    public static Label chip(String text) {
        return styled(new Label(text), "chip");
    }

    /** A label wearing style classes this file does not need to know about. */
    public static Label styledLabel(String text, String... classes) {
        return styled(new Label(text), classes);
    }

    private static <T extends Node> T styled(T node, String... classes) {
        node.getStyleClass().addAll(classes);
        return node;
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    public static Button primary(String text) {
        return styled(new Button(text), "button-primary");
    }

    public static Button secondary(String text) {
        return new Button(text);
    }

    public static Button danger(String text) {
        return styled(new Button(text), "button-danger");
    }

    public static Button ghost(String text) {
        return styled(new Button(text), "button-ghost");
    }

    public static Button link(String text) {
        return styled(new Button(text), "button-link");
    }

    /** An icon-only button; the tooltip carries the name of the action. */
    public static Button iconButton(String glyph, String describe) {
        Button button = styled(new Button(glyph), "button-ghost");
        Tooltip tooltip = new Tooltip(describe);
        tooltip.setShowDelay(Duration.millis(350));
        button.setTooltip(tooltip);
        button.setAccessibleText(describe);
        return button;
    }

    // =========================================================
    // INPUTS
    // =========================================================

    public static TextField input(String prompt, double width) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(width);
        return field;
    }

    /**
     * An input that already holds a value, for an edit form.
     *
     * <p>Null becomes "" rather than the literal "null", which is what
     * {@code setText(null)} would leave a person to delete by hand.
     */
    public static TextField input(String prompt, double width, String value) {
        TextField field = input(prompt, width);
        field.setText(value == null ? "" : value);
        return field;
    }

    public static TextField search(String prompt, double width) {
        TextField field = input(prompt, width);
        field.getStyleClass().add("search-field");
        return field;
    }

    public static PasswordField password(String prompt, double width) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setPrefWidth(width);
        return field;
    }

    /** Label above a control, the shape every form in the app uses. */
    public static VBox labelled(String label, Control control) {
        VBox box = new VBox(5, fieldLabel(label), control);
        VBox.setVgrow(control, Priority.NEVER);
        return box;
    }

    // =========================================================
    // LAYOUT
    // =========================================================

    public static Region growH() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static Region growV() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static Region divider() {
        Region line = new Region();
        line.getStyleClass().add("divider");
        line.setMaxWidth(Double.MAX_VALUE);
        return line;
    }

    public static HBox row(Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    public static VBox panel(Node... children) {
        VBox box = new VBox(GAP, children);
        box.getStyleClass().add("panel");
        return box;
    }

    /** A panel with a heading, an optional one-line purpose, and a body. */
    public static VBox panel(String heading, String purpose, Node body) {
        VBox header = new VBox(2, heading(heading));
        if (purpose != null && !purpose.isBlank()) {
            header.getChildren().add(hint(purpose));
        }
        VBox box = new VBox(GAP, header, body);
        box.getStyleClass().add("panel");
        VBox.setVgrow(body, Priority.ALWAYS);
        return box;
    }

    /**
     * The band at the top of every page: what this page is, one line on what
     * it is for, and the page's own actions pushed to the right. Actions live
     * here rather than scattered through the body so that "what can I do on
     * this screen" is answered in one place.
     */
    public static HBox pageHeader(String pageTitle, String purpose, Node... actions) {
        VBox text = new VBox(3, title(pageTitle));
        if (purpose != null && !purpose.isBlank()) {
            text.getChildren().add(hint(purpose));
        }
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().addAll(text, growH());
        bar.getChildren().addAll(actions);
        return bar;
    }

    public static VBox metricTile(String label, String value, String note) {
        Label valueLabel = styled(new Label(value), "metric-value");
        VBox tile = new VBox(2, styled(new Label(label), "metric-label"), valueLabel);
        if (note != null && !note.isBlank()) {
            tile.getChildren().add(hint(note));
        }
        tile.getStyleClass().addAll("metric-tile", "tile-accent");
        tile.setMinWidth(168);
        return tile;
    }

    /**
     * What a table or list shows when it has nothing to show. An empty screen
     * is a place to say what to do next, not a blank rectangle.
     */
    public static VBox emptyState(String headline, String body, Node action) {
        VBox box = new VBox(6, styled(new Label(headline), "empty-state-title"));
        Label bodyLabel = styled(new Label(body), "empty-state-body");
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxWidth(320);
        bodyLabel.setAlignment(Pos.CENTER);
        box.getChildren().add(bodyLabel);
        if (action != null) {
            box.getChildren().add(action);
        }
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** Disables a set of controls while something is in flight. */
    public static void busy(boolean busy, Node... nodes) {
        for (Node node : nodes) {
            if (node != null) {
                node.setDisable(busy);
            }
        }
    }

    // =========================================================
    // TABLES
    // =========================================================

    /**
     * A column bound to a bean property. Style classes are applied to the
     * cells rather than to the column, because a column's own style class
     * reaches its header only - which is what made an earlier attempt at
     * right-aligned numeric columns quietly do nothing.
     */
    public static <S, T> TableColumn<S, T> column(String heading,
                                                  String property,
                                                  double width,
                                                  String... cellClasses) {
        TableColumn<S, T> column = new TableColumn<>(heading);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        if (cellClasses.length > 0) {
            column.setCellFactory(ignored -> {
                TableCell<S, T> cell = new TableCell<>() {
                    @Override
                    protected void updateItem(T value, boolean empty) {
                        super.updateItem(value, empty);
                        setText(empty || value == null ? null : String.valueOf(value));
                    }
                };
                cell.getStyleClass().addAll(cellClasses);
                return cell;
            });
        }
        return column;
    }

    /** Numbers: monospace and right-aligned so the digits line up. */
    public static <S, T> TableColumn<S, T> numberColumn(String heading, String property, double width) {
        return column(heading, property, width, "cell-numeric");
    }

    /** A 0..1 score shown as a whole percentage. */
    public static <S> TableColumn<S, Double> percentColumn(String heading, String property, double width) {
        TableColumn<S, Double> column = new TableColumn<>(heading);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        column.setCellFactory(ignored -> {
            TableCell<S, Double> cell = new TableCell<>() {
                @Override
                protected void updateItem(Double value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty || value == null ? null : Math.round(value * 100) + "%");
                }
            };
            cell.getStyleClass().add("cell-numeric");
            return cell;
        });
        return column;
    }

    /** PRESENT / LATE / ABSENT as a badge, since the word alone reads as data. */
    public static <S> TableColumn<S, String> statusColumn(String heading, String property, double width) {
        TableColumn<S, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(null);
                setGraphic(empty || status == null ? null : statusBadge(status));
            }
        });
        return column;
    }

    public static Label statusBadge(String status) {
        Label badge = new Label(status.toUpperCase(java.util.Locale.ROOT));
        badge.getStyleClass().add("badge");
        switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "PRESENT" -> badge.getStyleClass().add("badge-present");
            case "ABSENT" -> badge.getStyleClass().add("badge-absent");
            case "LATE" -> badge.getStyleClass().add("badge-late");
            default -> badge.getStyleClass().add("badge-neutral");
        }
        return badge;
    }

    /**
     * Tables size to their content and take the space that is left, so a
     * window that is taller shows more rows instead of more grey.
     */
    public static <S> void stretch(TableView<S> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(220);
    }

    // =========================================================
    // MATCH-STRENGTH METER
    // =========================================================

    /**
     * A thin bar for a 0..1 score. The bar darkens as the score rises and
     * crosses to full-strength ink at the point the recogniser itself calls a
     * match, so the bar and the decision always agree.
     *
     * <p>It used to run amber to green. There is no hue in the stylesheet any
     * more, so strength is carried by weight of ink instead - which is the one
     * encoding that still works when the same figure is read off a projector at
     * the back of a room.
     */
    public static StackPane meter(double fraction, double width) {
        double clamped = Math.max(0, Math.min(1, fraction));

        Region fill = new Region();
        fill.getStyleClass().add("meter-fill");
        fill.getStyleClass().add(clamped >= 0.55 ? "meter-fill-strong"
                : clamped >= 0.363 ? "meter-fill" : "meter-fill-weak");
        fill.setPrefWidth(Math.max(2, width * clamped));
        fill.setMaxWidth(Math.max(2, width * clamped));

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("meter");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setPrefWidth(width);
        track.setMaxWidth(width);
        return track;
    }

    // =========================================================
    // VIEWFINDER
    // =========================================================

    /**
     * The camera housing: a recessed dark panel with corner ticks around the
     * feed. This is the one place the design spends any boldness - it is the
     * part of the product that is actually about looking at a face, so it is
     * built to read as an instrument rather than as a picture in a box.
     */
    public static StackPane viewfinder(ImageView feed) {
        StackPane housing = new StackPane(feed);
        housing.getStyleClass().add("viewfinder");
        housing.setAlignment(Pos.CENTER);
        addCorner(housing, Pos.TOP_LEFT, true);
        addCorner(housing, Pos.TOP_RIGHT, true);
        addCorner(housing, Pos.BOTTOM_LEFT, false);
        addCorner(housing, Pos.BOTTOM_RIGHT, false);
        return housing;
    }

    private static void addCorner(StackPane housing, Pos corner, boolean tickFirst) {
        Region horizontal = new Region();
        horizontal.getStyleClass().add("viewfinder-tick");
        horizontal.setPrefSize(15, 2);
        horizontal.setMaxSize(15, 2);

        Region vertical = new Region();
        vertical.getStyleClass().add("viewfinder-tick-v");
        vertical.setPrefSize(2, 15);
        vertical.setMaxSize(2, 15);

        VBox mark = tickFirst ? new VBox(horizontal, vertical) : new VBox(vertical, horizontal);
        mark.setAlignment(corner);
        mark.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        mark.setMouseTransparent(true);
        StackPane.setAlignment(mark, corner);
        StackPane.setMargin(mark, new Insets(6));
        housing.getChildren().add(mark);
    }

    /** A status lamp: dark when idle, lit while a session is running. */
    public static Region lamp() {
        Region lamp = new Region();
        lamp.getStyleClass().add("lamp");
        lamp.setPrefSize(9, 9);
        lamp.setMaxSize(9, 9);
        return lamp;
    }

    public static void lampState(Region lamp, String state) {
        lamp.getStyleClass().removeAll("lamp-live", "lamp-error");
        if ("live".equals(state) || "error".equals(state)) {
            lamp.getStyleClass().add("lamp-" + state);
        }
    }

    // =========================================================
    // CONFIRMATION
    // =========================================================

    /**
     * A yes/no question before something irreversible. Deletes used to happen
     * on a single click with no way back, which is the sort of thing a person
     * only notices once.
     */
    public static boolean confirm(Window owner,
                                  String heading,
                                  String question,
                                  String confirmLabel) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(heading);
        dialog.setHeaderText(heading);

        Label body = new Label(question);
        body.setWrapText(true);
        body.setMaxWidth(360);
        dialog.getDialogPane().setContent(new VBox(body));

        ButtonType go = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, go);
        Theme.applyTo(dialog.getDialogPane());

        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(go);
        confirmButton.getStyleClass().add("button-danger");

        return dialog.showAndWait().filter(choice -> choice == go).isPresent();
    }
}
