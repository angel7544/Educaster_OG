package com.educater.ui;

import com.educater.config.ConfigService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonObject;

public class CreateChapterWindow {

    public interface OnSuccessListener {
        void onSuccess();
    }

    private final String courseId;
    private final String presetVideoUrl;
    private final boolean isLive;
    private final OnSuccessListener listener;

    public CreateChapterWindow(String courseId, String presetVideoUrl, boolean isLive, OnSuccessListener listener) {
        this.courseId = courseId;
        this.presetVideoUrl = presetVideoUrl;
        this.isLive = isLive;
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
        UIUtils.setIcon(stage);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(isLive ? "Publish Live Chapter" : "Create New Chapter");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f8fafc;");

        // Title
        Label lblTitle = new Label("Title");
        lblTitle.setStyle("-fx-font-weight: bold;");
        TextField txtTitle = new TextField();
        txtTitle.setPromptText("Chapter 1: Introduction");
        txtTitle.setStyle("-fx-padding: 8; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        VBox boxTitle = new VBox(5, lblTitle, txtTitle);

        // Description
        Label lblDesc = new Label("Description");
        lblDesc.setStyle("-fx-font-weight: bold;");
        TextArea txtDesc = new TextArea();
        txtDesc.setPromptText("Brief summary...");
        txtDesc.setPrefRowCount(3);
        txtDesc.setStyle("-fx-control-inner-background: white; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0;");
        VBox boxDesc = new VBox(5, lblDesc, txtDesc);

        // Position & Duration
        Label lblPos = new Label("Position");
        lblPos.setStyle("-fx-font-weight: bold;");
        TextField txtPos = new TextField("1");
        txtPos.setStyle("-fx-padding: 8; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        VBox boxPos = new VBox(5, lblPos, txtPos);
        HBox.setHgrow(boxPos, Priority.ALWAYS);

        Label lblDur = new Label("Duration (Seconds)");
        lblDur.setStyle("-fx-font-weight: bold;");
        TextField txtDur = new TextField("0");
        txtDur.setStyle("-fx-padding: 8; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        VBox boxDur = new VBox(5, lblDur, txtDur);
        HBox.setHgrow(boxDur, Priority.ALWAYS);
        
        HBox boxPosDur = new HBox(15, boxPos, boxDur);
        
        // Live Timing
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIST = ZonedDateTime.now(istZone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String defaultStart = nowIST.format(formatter);
        String defaultEnd = nowIST.plusMinutes(5).format(formatter);

        Label lblStart = new Label("Live Starts At (IST)");
        lblStart.setStyle("-fx-font-weight: bold;");
        TextField txtStart = new TextField(defaultStart);
        txtStart.setPromptText("YYYY-MM-DD HH:mm");
        txtStart.setStyle("-fx-padding: 8; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        VBox boxStart = new VBox(5, lblStart, txtStart);
        HBox.setHgrow(boxStart, Priority.ALWAYS);
        
        Label lblEnd = new Label("Live Ends At (IST)");
        lblEnd.setStyle("-fx-font-weight: bold;");
        TextField txtEnd = new TextField(defaultEnd);
        txtEnd.setPromptText("YYYY-MM-DD HH:mm");
        txtEnd.setStyle("-fx-padding: 8; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        VBox boxEnd = new VBox(5, lblEnd, txtEnd);
        HBox.setHgrow(boxEnd, Priority.ALWAYS);
        
        HBox boxTiming = new HBox(15, boxStart, boxEnd);

        // Video Source
        Label lblVid = new Label("Video Source (Optional if chapter has multiple lessons)");
        lblVid.setStyle("-fx-font-weight: bold;");
        
        ToggleButton btnUseUrl = new ToggleButton("Use URL");
        btnUseUrl.setSelected(true);
        btnUseUrl.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-base: #e2e8f0;");
        
        ToggleButton btnUpload = new ToggleButton("Upload File");
        btnUpload.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-background-color: transparent;");
        
        ToggleGroup groupVid = new ToggleGroup();
        btnUseUrl.setToggleGroup(groupVid);
        btnUpload.setToggleGroup(groupVid);
        
        groupVid.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == btnUseUrl) {
                btnUseUrl.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-base: #e2e8f0;");
                btnUpload.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-background-color: transparent;");
            } else if (newVal == btnUpload) {
                btnUpload.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-base: #e2e8f0;");
                btnUseUrl.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-background-color: transparent;");
            }
        });

        HBox boxVidBtns = new HBox(btnUseUrl, btnUpload);
        boxVidBtns.setAlignment(Pos.CENTER);
        boxVidBtns.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 20; -fx-padding: 2;");
        btnUseUrl.setMaxWidth(Double.MAX_VALUE);
        btnUpload.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnUseUrl, Priority.ALWAYS);
        HBox.setHgrow(btnUpload, Priority.ALWAYS);

        TextField txtVidUrl = new TextField(presetVideoUrl != null ? presetVideoUrl : "");
        txtVidUrl.setPromptText("https://vimeo.com/... or .m3u8");
        txtVidUrl.setStyle("-fx-padding: 8; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        
        VBox boxVidGroup = new VBox(10, lblVid, boxVidBtns, txtVidUrl);
        boxVidGroup.setPadding(new Insets(15));
        boxVidGroup.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-border-width: 1; -fx-background-color: white;");

        // Toggles
        CheckBox chkDemo = new CheckBox();
        VBox boxDemo = createToggleSection("Demo Video (Free Preview)", "Allow users to watch without purchasing", chkDemo);

        CheckBox chkDownload = new CheckBox();
        VBox boxDownload = createToggleSection("Allow Download", "Allow users to download this chapter", chkDownload);

        CheckBox chkLive = new CheckBox();
        chkLive.setSelected(isLive);
        VBox boxLive = createToggleSection("Live Session", "Mark this chapter as a live session", chkLive);

        // Attachments
        Label lblAtt = new Label("Attachments");
        lblAtt.setStyle("-fx-font-weight: bold;");
        ToggleButton btnAttUpload = new ToggleButton("Upload File");
        btnAttUpload.setSelected(true);
        btnAttUpload.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-base: #e2e8f0;");
        ToggleButton btnAttLink = new ToggleButton("Add Link");
        btnAttLink.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-background-color: transparent;");
        
        ToggleGroup groupAtt = new ToggleGroup();
        btnAttUpload.setToggleGroup(groupAtt);
        btnAttLink.setToggleGroup(groupAtt);
        
        groupAtt.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == btnAttUpload) {
                btnAttUpload.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-base: #e2e8f0;");
                btnAttLink.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-background-color: transparent;");
            } else if (newVal == btnAttLink) {
                btnAttLink.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-base: #e2e8f0;");
                btnAttUpload.setStyle("-fx-background-radius: 20; -fx-padding: 5 15; -fx-background-color: transparent;");
            }
        });

        HBox boxAttBtns = new HBox(btnAttUpload, btnAttLink);
        boxAttBtns.setAlignment(Pos.CENTER);
        boxAttBtns.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 20; -fx-padding: 2;");
        btnAttUpload.setMaxWidth(Double.MAX_VALUE);
        btnAttLink.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnAttUpload, Priority.ALWAYS);
        HBox.setHgrow(btnAttLink, Priority.ALWAYS);
        
        Button btnChooseFile = new Button("Choose File");
        btnChooseFile.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 3; -fx-background-radius: 3;");
        Label lblNoFile = new Label("No file chosen");
        lblNoFile.setStyle("-fx-text-fill: gray;");
        HBox boxChoose = new HBox(10, btnChooseFile, lblNoFile);
        boxChoose.setAlignment(Pos.CENTER_LEFT);
        boxChoose.setPadding(new Insets(5));
        boxChoose.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 5; -fx-border-width: 1; -fx-background-color: white;");
        
        VBox boxAttGroup = new VBox(10, lblAtt, boxAttBtns, boxChoose);
        boxAttGroup.setPadding(new Insets(15));
        boxAttGroup.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-border-width: 1; -fx-background-color: white;");

        CheckBox chkPublished = new CheckBox();
        chkPublished.setSelected(true);
        VBox boxPublished = createToggleSection("Published", "Visible to students", chkPublished);

        Button btnCreate = new Button(isLive ? "Publish Live Chapter" : "Create Chapter");
        btnCreate.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        
        HBox boxBtnCreate = new HBox(btnCreate);
        boxBtnCreate.setAlignment(Pos.CENTER_RIGHT);
        boxBtnCreate.setPadding(new Insets(10, 0, 0, 0));

        content.getChildren().addAll(
                new Label("Course ID: " + courseId),
                boxTitle, boxDesc, boxPosDur,
                isLive ? boxTiming : new VBox(), // Only add timing if it is a live chapter
                boxVidGroup, boxDemo, boxDownload, boxLive, boxAttGroup, boxPublished,
                boxBtnCreate
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #f8fafc;");

        stage.setScene(new Scene(scrollPane, 450, 750));

        btnCreate.setOnAction(e -> {
            String title = txtTitle.getText().trim();
            if (title.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Title is required").show();
                return;
            }
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("courseId", courseId);
                payload.addProperty("title", title);
                payload.addProperty("description", txtDesc.getText());
                
                try {
                    payload.addProperty("position", Integer.parseInt(txtPos.getText().trim()));
                } catch(Exception ignored){}
                
                payload.addProperty("is_demo", chkDemo.isSelected());
                payload.addProperty("allow_download", chkDownload.isSelected());
                payload.addProperty("is_live", chkLive.isSelected());
                payload.addProperty("video_url", txtVidUrl.getText().trim());
                payload.addProperty("is_published", chkPublished.isSelected());
                
                if (isLive) {
                    if (!txtStart.getText().trim().isEmpty()) payload.addProperty("live_starts_at", txtStart.getText().trim());
                    if (!txtEnd.getText().trim().isEmpty()) payload.addProperty("live_ends_at", txtEnd.getText().trim());
                }

                String url = ConfigService.getLmsBackendUrl() + "/api/chapters";
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
                
                String token = ConfigService.getLmsJwtToken();
                if (token != null && !token.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + token);
                }

                HttpResponse<String> res = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    new Alert(Alert.AlertType.INFORMATION, "Chapter created successfully!").showAndWait();
                    if (listener != null) listener.onSuccess();
                    stage.close();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Failed: " + res.statusCode() + " " + res.body()).showAndWait();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
            }
        });

        stage.showAndWait();
    }
}
