package com.educater.ui;

import com.educater.config.ConfigService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PublishLiveWindow {

    private final Consumer<String> onChapterSaved;
    private String existingChapterId = null;
    private final Map<String, JsonObject> currentChaptersMap = new HashMap<>();

    public PublishLiveWindow(Consumer<String> onChapterSaved) {
        this.onChapterSaved = onChapterSaved;
    }

    private static class CourseItem {
        String id;
        String title;
        JsonArray rawChapters;
        @Override
        public String toString() { return title; }
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
        stage.setTitle("Add New Chapter");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f8fafc;");

        // Course Selection
        Label lblCourse = new Label("Select Course");
        lblCourse.setStyle("-fx-font-weight: bold;");
        ComboBox<CourseItem> cmbCourse = new ComboBox<>();
        cmbCourse.setPromptText("Loading Courses...");
        cmbCourse.setMaxWidth(Double.MAX_VALUE);
        VBox boxCourse = new VBox(5, lblCourse, cmbCourse);

        // Title
        Label lblTitle = new Label("Title");
        lblTitle.setStyle("-fx-font-weight: bold;");
        ComboBox<String> cmbTitle = new ComboBox<>();
        cmbTitle.setEditable(true);
        cmbTitle.setPromptText("Type or select a chapter...");
        cmbTitle.setMaxWidth(Double.MAX_VALUE);
        cmbTitle.setStyle("-fx-padding: 3; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #e2e8f0; -fx-background-color: white;");
        
        cmbTitle.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    JsonObject chap = currentChaptersMap.get(item);
                    if (chap != null && chap.has("is_live") && !chap.get("is_live").isJsonNull() && chap.get("is_live").getAsBoolean()) {
                        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4, javafx.scene.paint.Color.RED);
                        setGraphic(dot);
                        setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        
        VBox boxTitle = new VBox(5, lblTitle, cmbTitle);

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
 HBox boxTiming = new HBox(15, boxStart, boxEnd);
 
        HBox boxVidBtns = new HBox(btnUseUrl, btnUpload);
        boxVidBtns.setAlignment(Pos.CENTER);
        boxVidBtns.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 20; -fx-padding: 2;");
        btnUseUrl.setMaxWidth(Double.MAX_VALUE);
        btnUpload.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnUseUrl, Priority.ALWAYS);
        HBox.setHgrow(btnUpload, Priority.ALWAYS);

        TextField txtVidUrl = new TextField();
        txtVidUrl.setPromptText("Will be auto-generated for Live Streams");
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
        chkLive.setSelected(true);
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

        Button btnCreate = new Button("Create Chapter");
        btnCreate.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        btnCreate.setDisable(true);
        
        HBox boxBtnCreate = new HBox(btnCreate);
        boxBtnCreate.setAlignment(Pos.CENTER_RIGHT);
        boxBtnCreate.setPadding(new Insets(10, 0, 0, 0));

        VBox leftCol = new VBox(15);
        leftCol.setPrefWidth(400);
        leftCol.getChildren().addAll(boxCourse, boxTitle, boxDesc, boxPosDur, boxTiming, boxVidGroup);

        VBox rightCol = new VBox(15);
        rightCol.setPrefWidth(400);
        rightCol.getChildren().addAll(boxDemo, boxDownload, boxLive, boxPublished, boxAttGroup, boxBtnCreate);

        HBox splitContent = new HBox(30, leftCol, rightCol);
        splitContent.setAlignment(Pos.TOP_LEFT);

        content.getChildren().add(splitContent);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #f8fafc;");

        Scene scene = new Scene(scrollPane, 900, 600);
        stage.setScene(scene);

        // Load courses logic
        TaskManager.submitHeavyTask(() -> {
            try {
                String url = ConfigService.getLmsBackendUrl() + "/api/courses";
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
                String token = ConfigService.getLmsJwtToken();
                if (token != null && !token.isBlank()) reqBuilder.header("Authorization", "Bearer " + token);
                
                HttpResponse<String> res = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonObject parsed = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (parsed.has("data") && parsed.get("data").isJsonArray()) {
                        JsonArray data = parsed.getAsJsonArray("data");
                        List<CourseItem> courses = new ArrayList<>();
                        for (JsonElement el : data) {
                            JsonObject obj = el.getAsJsonObject();
                            CourseItem c = new CourseItem();
                            c.id = obj.has("id") ? obj.get("id").getAsString() : "";
                            c.title = obj.has("title") ? obj.get("title").getAsString() : "";
                            c.rawChapters = obj.has("chapters") && obj.get("chapters").isJsonArray() ? obj.getAsJsonArray("chapters") : new JsonArray();
                            courses.add(c);
                        }
                        Platform.runLater(() -> {
                            cmbCourse.getItems().addAll(courses);
                            cmbCourse.setPromptText("Select a Course");
                            btnCreate.setDisable(false);
                        });
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        cmbCourse.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                TaskManager.submitHeavyTask(() -> {
                    try {
                        JsonArray data = newVal.rawChapters != null ? newVal.rawChapters : new JsonArray();
                        JsonObject existingLive = null;
                        int maxPosition = 0;
                        
                        List<String> chapterTitles = new ArrayList<>();
                        currentChaptersMap.clear();

                        for (JsonElement el : data) {
                            JsonObject chap = el.getAsJsonObject();
                            String chapTitle = chap.has("title") && !chap.get("title").isJsonNull() ? chap.get("title").getAsString() : "";
                            if (!chapTitle.isEmpty()) {
                                chapterTitles.add(chapTitle);
                                currentChaptersMap.put(chapTitle, chap);
                            }
                            if (chap.has("position") && !chap.get("position").isJsonNull()) {
                                try {
                                    int p = chap.get("position").getAsInt();
                                    if (p > maxPosition) maxPosition = p;
                                } catch (Exception ignored) {}
                            }
                            if (existingLive == null && chap.has("is_live") && !chap.get("is_live").isJsonNull() && chap.get("is_live").getAsBoolean()) {
                                existingLive = chap;
                            }
                        }
                        
                        if (existingLive != null) {
                            final JsonObject existing = existingLive;
                            Platform.runLater(() -> {
                                cmbTitle.getItems().setAll(chapterTitles);
                                existingChapterId = existing.has("id") ? existing.get("id").getAsString() : null;
                                cmbTitle.getEditor().setText(existing.has("title") && !existing.get("title").isJsonNull() ? existing.get("title").getAsString() : "");
                                txtDesc.setText(existing.has("description") && !existing.get("description").isJsonNull() ? existing.get("description").getAsString() : "");
                                txtPos.setText(existing.has("position") && !existing.get("position").isJsonNull() ? existing.get("position").getAsString() : "1");
                                txtStart.setText(defaultStart);
                                txtEnd.setText(defaultEnd);
                                chkDemo.setSelected(existing.has("is_demo") && !existing.get("is_demo").isJsonNull() && existing.get("is_demo").getAsBoolean());
                                chkDownload.setSelected(existing.has("allow_download") && !existing.get("allow_download").isJsonNull() && existing.get("allow_download").getAsBoolean());
                                chkPublished.setSelected(existing.has("is_published") && !existing.get("is_published").isJsonNull() && existing.get("is_published").getAsBoolean());
                                chkLive.setSelected(true);
                                btnCreate.setText("Update Chapter");
                            });
                            return; 
                        } else {
                            final int nextPos = maxPosition + 1;
                            Platform.runLater(() -> {
                                cmbTitle.getItems().setAll(chapterTitles);
                                existingChapterId = null;
                                cmbTitle.getEditor().setText("Chapter " + nextPos + ": Live Session");
                                txtDesc.setText("");
                                txtPos.setText(String.valueOf(nextPos));
                                txtStart.setText(defaultStart);
                                txtEnd.setText(defaultEnd);
                                chkDemo.setSelected(false);
                                chkDownload.setSelected(false);
                                chkPublished.setSelected(true);
                                chkLive.setSelected(true);
                                btnCreate.setText("Create Chapter");
                            });
                            return;
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        // Default fallback
                        Platform.runLater(() -> {
                            currentChaptersMap.clear();
                            cmbTitle.getItems().clear();
                            existingChapterId = null;
                            cmbTitle.getEditor().setText("Live Session: " + newVal.title);
                            txtDesc.setText("");
                            txtPos.setText("1");
                            txtStart.setText(defaultStart);
                            txtEnd.setText(defaultEnd);
                            chkDemo.setSelected(false);
                            chkDownload.setSelected(false);
                            chkPublished.setSelected(true);
                            chkLive.setSelected(true);
                            btnCreate.setText("Create Chapter");
                        });
                    }
                });
            }
        });

        // Listener for when user selects an existing title from the drop-down
        cmbTitle.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && currentChaptersMap.containsKey(newVal)) {
                JsonObject existing = currentChaptersMap.get(newVal);
                existingChapterId = existing.has("id") ? existing.get("id").getAsString() : null;
                txtDesc.setText(existing.has("description") && !existing.get("description").isJsonNull() ? existing.get("description").getAsString() : "");
                txtPos.setText(existing.has("position") && !existing.get("position").isJsonNull() ? existing.get("position").getAsString() : "1");
                chkDemo.setSelected(existing.has("is_demo") && !existing.get("is_demo").isJsonNull() && existing.get("is_demo").getAsBoolean());
                chkDownload.setSelected(existing.has("allow_download") && !existing.get("allow_download").isJsonNull() && existing.get("allow_download").getAsBoolean());
                chkPublished.setSelected(existing.has("is_published") && !existing.get("is_published").isJsonNull() && existing.get("is_published").getAsBoolean());
                chkLive.setSelected(existing.has("is_live") && !existing.get("is_live").isJsonNull() && existing.get("is_live").getAsBoolean());
                btnCreate.setText("Update Chapter");
            } else {
                existingChapterId = null;
                btnCreate.setText("Create Chapter");
            }
        });

        btnCreate.setOnAction(e -> {
            CourseItem crs = cmbCourse.getValue();
            if (crs == null) {
                new Alert(Alert.AlertType.WARNING, "Select a course").show();
                return;
            }
            String title = cmbTitle.getEditor().getText().trim();
            if (title.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Title is required").show();
                return;
            }
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("courseId", crs.id);
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
                
                if (!txtStart.getText().trim().isEmpty()) payload.addProperty("live_starts_at", txtStart.getText().trim());
                if (!txtEnd.getText().trim().isEmpty()) payload.addProperty("live_ends_at", txtEnd.getText().trim());

                String url = ConfigService.getLmsBackendUrl() + "/api/chapters" + (existingChapterId != null ? "/" + existingChapterId : "");
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json");
                        
                if (existingChapterId != null) {
                    reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(payload.toString()));
                } else {
                    reqBuilder.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
                }
                
                String token = ConfigService.getLmsJwtToken();
                if (token != null && !token.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + token);
                }

                HttpResponse<String> res = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    String savedChapterId = existingChapterId;
                    if (savedChapterId == null) {
                        try {
                            JsonObject resJson = JsonParser.parseString(res.body()).getAsJsonObject();
                            if (resJson.has("data")) {
                                JsonObject dataObj = resJson.getAsJsonObject("data");
                                if (dataObj.has("id")) {
                                    savedChapterId = dataObj.get("id").getAsString();
                                }
                            } else if (resJson.has("id")) {
                                savedChapterId = resJson.get("id").getAsString();
                            }
                        } catch (Exception parseEx) {
                            parseEx.printStackTrace();
                        }
                    }
                    new Alert(Alert.AlertType.INFORMATION, existingChapterId != null ? "Chapter updated successfully!" : "Chapter created successfully!").showAndWait();
                    stage.close();
                    if (savedChapterId != null && onChapterSaved != null) {
                        onChapterSaved.accept(savedChapterId);
                    }
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
