package com.educater.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CreateLessonWindow {

    public static class LessonData {
        public String title;
        public String description;
        public boolean isFree;
    }

    public interface OnSuccessListener {
        void onSuccess(LessonData data);
    }

    private final OnSuccessListener listener;

    public CreateLessonWindow(OnSuccessListener listener) {
        this.listener = listener;
    }

    private VBox createToggleSection(String title, String subtitle, CheckBox checkBox) {
        VBox vbox = new VBox(2);
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label lblSub = new Label(subtitle);
        lblSub.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        vbox.getChildren().addAll(lblTitle, lblSub);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox hbox = new HBox(10, vbox, spacer, checkBox);
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setPadding(new Insets(10));
        hbox.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 5; -fx-border-width: 1; -fx-background-color: white;");
        return new VBox(hbox);
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("New Lesson Details");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f8fafc;");

        // Title
        Label lblTitle = new Label("Title");
        lblTitle.setStyle("-fx-font-weight: bold;");
        TextField txtTitle = new TextField();
        txtTitle.setPromptText("Lesson Title");
        txtTitle.setStyle("-fx-padding: 8; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        VBox boxTitle = new VBox(5, lblTitle, txtTitle);
        
        // Description
        Label lblDesc = new Label("Description");
        lblDesc.setStyle("-fx-font-weight: bold;");
        TextArea txtDesc = new TextArea();
        txtDesc.setPromptText("Description (optional)");
        txtDesc.setPrefRowCount(3);
        txtDesc.setStyle("-fx-control-inner-background: white; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0;");
        VBox boxDesc = new VBox(5, lblDesc, txtDesc);

        // Toggle
        CheckBox chkFree = new CheckBox();
        VBox boxFree = createToggleSection("Demo Video (Free Preview)", "Allow users to watch without purchasing", chkFree);

        Button btnSave = new Button("Prepare Lesson");
        btnSave.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        
        HBox boxBtnCreate = new HBox(btnSave);
        boxBtnCreate.setAlignment(Pos.CENTER_RIGHT);
        boxBtnCreate.setPadding(new Insets(10, 0, 0, 0));

        content.getChildren().addAll(
                new Label("Enter Lesson Details (Will be created after upload):"),
                boxTitle, boxDesc, boxFree, boxBtnCreate
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #f8fafc;");

        stage.setScene(new Scene(scrollPane, 450, 450));

        btnSave.setOnAction(e -> {
            String title = txtTitle.getText().trim();
            if (title.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Title is required").show();
                return;
            }
            LessonData d = new LessonData();
            d.title = title;
            d.description = txtDesc.getText().trim();
            d.isFree = chkFree.isSelected();
            
            if (listener != null) listener.onSuccess(d);
            stage.close();
        });

        stage.showAndWait();
    }
}
