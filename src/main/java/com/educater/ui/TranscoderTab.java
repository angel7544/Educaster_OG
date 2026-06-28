package com.educater.ui;

import com.educater.r2.R2Service;
import com.educater.video.VideoProcessor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import software.amazon.awssdk.transfer.s3.model.FileUpload;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.Map;
import com.educater.video.VideoEncodingOptions;

import com.educater.config.ConfigService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TranscoderTab extends VBox {

    private static class CourseItem {
        String id;
        String title;
        java.util.List<ChapterItem> chapters;
        @Override public String toString() { return title; }
    }
    private static class LessonItem {
        String id;
        String title;
        @Override public String toString() { return title; }
    }
    private static class ChapterItem {
        String id;
        String title;
        java.util.List<LessonItem> lessons;
        @Override public String toString() { return title; }
    }

    private final R2Service r2Service;
    private final Runnable onComplete;
    
    private TextField inputPathField;
    private TextField outputPathField;
    private TextField prefixField;
    private CheckBox chkDeleteLocal;
    
    // UI components for Encoding Options
    private Map<String, CheckBox> qualityChecks = new HashMap<>();
    private Map<String, TextField> bitrateFields = new HashMap<>();
    private CheckBox chkNvenc;
    private CheckBox chkParallel;
    private CheckBox chkLiveMode;
    private ComboBox<String> cmbCodec;
    private CheckBox chkCrf;
    private TextField txtCrf;
    private TextField txtSegment;
    private CheckBox chkSingleFolder;
    private CheckBox chkKeepAllAudio;
    private CheckBox chkGenerateMp4;
    
    private RadioButton radCloud;
    private RadioButton radLocal;
    
    private ComboBox<CourseItem> cmbCourse;
    private ComboBox<ChapterItem> cmbChapter;
    private ComboBox<LessonItem> cmbLesson;
    private CreateLessonWindow.LessonData pendingLessonData;
    
    private TextArea consoleOutput;
    
    private Button startBtn;
    private Button cancelBtn;
    
    private AtomicBoolean isProcessing = new AtomicBoolean(false);

    public TranscoderTab(R2Service r2Service, Runnable onComplete) {
        this.r2Service = r2Service;
        this.onComplete = onComplete;
        
        this.setPadding(new Insets(15));
        this.setSpacing(15);
        
        Label title = new Label("EduCaster Transcoder & Uploader");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        // --- Input & Output Settings (Mimicking LabelFrame) ---
        VBox ioBox = new VBox(10);
        ioBox.setPadding(new Insets(15));
        ioBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 15;");
        
        Label ioTitle = new Label("Input & Output Settings");
        ioTitle.setStyle("-fx-font-weight: bold; -fx-translate-y: -22; -fx-background-color: #f4f4f4; -fx-padding: 0 5;");
        
        GridPane ioGrid = new GridPane();
        ioGrid.setHgap(10);
        ioGrid.setVgap(10);
        
        inputPathField = new TextField();
        inputPathField.setPrefWidth(400);
        inputPathField.setEditable(false);
        Button browseInBtn = new Button("Browse File");
        
        outputPathField = new TextField();
        outputPathField.setPrefWidth(400);
        outputPathField.setEditable(false);
        Button browseOutBtn = new Button("Browse Folder");
        
        ioGrid.add(new Label("Input Video:"), 0, 0);
        ioGrid.add(inputPathField, 1, 0);
        ioGrid.add(browseInBtn, 2, 0);
        
        ioGrid.add(new Label("Output Folder (Local):"), 0, 1);
        ioGrid.add(outputPathField, 1, 1);
        ioGrid.add(browseOutBtn, 2, 1);
        
        ioBox.getChildren().addAll(ioGrid);
        
        // Wrapper for title overlay
        StackPane ioWrapper = new StackPane(ioBox, ioTitle);
        StackPane.setAlignment(ioTitle, Pos.TOP_LEFT);
        
        
        // --- Encoding & Destination Options ---
        VBox optBox = new VBox(10);
        optBox.setPadding(new Insets(15));
        optBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 15;");
        
        Label optTitle = new Label("Encoding & Destination Options");
        optTitle.setStyle("-fx-font-weight: bold; -fx-translate-y: -22; -fx-background-color: #f4f4f4; -fx-padding: 0 5;");
        
        HBox splitOpts = new HBox(20);
        
        // Left side: Transcoding (Resolutions & Bitrates)
        VBox leftOpts = new VBox(8);
        leftOpts.getChildren().add(new Label("Resolutions & Bitrates:"));
        
        String[] qualities = {"1080p", "720p", "480p", "360p", "240p"};
        String[] bitrates = {"5000k", "2800k", "1400k", "800k", "400k"};
        
        GridPane resGrid = new GridPane();
        resGrid.setHgap(5); resGrid.setVgap(5);
        for (int i = 0; i < qualities.length; i++) {
            CheckBox cb = new CheckBox(qualities[i]);
            cb.setSelected(true);
            TextField txtBitrate = new TextField(bitrates[i]);
            txtBitrate.setPrefWidth(60);
            
            qualityChecks.put(qualities[i], cb);
            bitrateFields.put(qualities[i], txtBitrate);
            
            resGrid.add(cb, 0, i);
            resGrid.add(txtBitrate, 1, i);
        }
        leftOpts.getChildren().add(resGrid);
        
        // Middle side: Advanced Settings
        VBox midOpts = new VBox(8);
        midOpts.getChildren().add(new Label("Advanced Settings:"));
        
        chkNvenc = new CheckBox("Enable Hardware Acceleration (NVENC)");
        chkParallel = new CheckBox("Run Encodings in Parallel (Faster CPU/GPU processing)");
        chkParallel.setSelected(true);
        chkLiveMode = new CheckBox("Live Streaming Mode (Delete segments, shorter chunks)");
        
        HBox codecBox = new HBox(5, new Label("Codec Selection:"), cmbCodec = new ComboBox<>());
        cmbCodec.getItems().addAll("h264", "h265");
        cmbCodec.setValue("h264");
        
        HBox crfBox = new HBox(5);
        chkCrf = new CheckBox("Compress File Size (CRF):");
        txtCrf = new TextField("28");
        txtCrf.setPrefWidth(40);
        Button btnInfo = new Button("i");
        btnInfo.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "CRF controls the video quality/size tradeoff.\nLower value = Higher Quality/Larger Size.\n28 is recommended for web.").show());
        crfBox.getChildren().addAll(chkCrf, txtCrf, btnInfo);
        
        HBox segBox = new HBox(5, new Label("Segment Duration (s):"), txtSegment = new TextField("6"));
        txtSegment.setPrefWidth(40);
        
        chkSingleFolder = new CheckBox("Single Folder HLS Structure (Recommended)");
        chkSingleFolder.setSelected(true);
        
        chkKeepAllAudio = new CheckBox("Keep all audio tracks (Multi-audio)");
        chkKeepAllAudio.setSelected(true);
        
        chkGenerateMp4 = new CheckBox("Generate MP4 Output (Standalone)");
        
        midOpts.getChildren().addAll(chkNvenc, chkParallel, chkLiveMode, codecBox, crfBox, segBox, chkSingleFolder, chkKeepAllAudio, chkGenerateMp4);
        
        // Right side: Destination
        VBox rightOpts = new VBox(8);
        ToggleGroup destGroup = new ToggleGroup();
        radCloud = new RadioButton("Upload to Cloud (R2/S3)");
        radCloud.setToggleGroup(destGroup);
        radCloud.setSelected(true);
        
        radLocal = new RadioButton("Save Locally Only");
        radLocal.setToggleGroup(destGroup);
        
        prefixField = new TextField();
        prefixField.setPromptText("Target Prefix (e.g. videos/)");
        
        Button browseCloudBtn = new Button("Browse Cloud");
        HBox prefixBox = new HBox(5, prefixField, browseCloudBtn);
        
        radLocal.selectedProperty().addListener((obs, old, isLocal) -> {
            prefixField.setDisable(isLocal);
            browseCloudBtn.setDisable(isLocal);
        });
        
        browseCloudBtn.setOnAction(e -> {
            if (r2Service == null) {
                new Alert(Alert.AlertType.ERROR, "Cloud service not configured.").show();
                return;
            }
            TaskManager.submitIoTask(() -> {
                try {
                    java.util.List<R2Service.R2File> folders = r2Service.listFiles("");
                    java.util.List<String> choices = new java.util.ArrayList<>();
                    choices.add(""); // Root
                    for (R2Service.R2File f : folders) {
                        if (f.isFolder()) choices.add(f.getKey());
                    }
                    Platform.runLater(() -> {
                        ChoiceDialog<String> dialog = new ChoiceDialog<>("", choices);
                        dialog.setTitle("Select Cloud Folder");
                        dialog.setHeaderText("Select a destination folder/prefix:");
                        dialog.setContentText("Folder:");
                        dialog.showAndWait().ifPresent(res -> prefixField.setText(res));
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Failed to load folders: " + ex.getMessage()).show());
                }
            });
        });
        
        cmbCourse = new ComboBox<>();
        cmbChapter = new ComboBox<>();
        cmbLesson = new ComboBox<>();
        Button btnNewChapter = new Button("+ Chapter");
        btnNewChapter.setOnAction(e -> {
            CourseItem crs = cmbCourse.getValue();
            if (crs == null) {
                new Alert(Alert.AlertType.WARNING, "Select a course first").show();
                return;
            }
            new CreateChapterWindow(crs.id, null, false, this::loadLmsCourses).show();
        });

        Button btnNewLesson = new Button("+ Lesson");
        btnNewLesson.setOnAction(e -> {
            new CreateLessonWindow(data -> {
                pendingLessonData = data;
                LessonItem fake = new LessonItem();
                fake.id = "";
                fake.title = "NEW: " + data.title;
                cmbLesson.getItems().add(fake);
                cmbLesson.getSelectionModel().select(fake);
            }).show();
        });

        HBox chpBox = new HBox(5, cmbChapter, btnNewChapter);
        HBox lesBox = new HBox(5, cmbLesson, btnNewLesson);

        VBox lmsBox = new VBox(5, 
            new Label("LMS Course:"), cmbCourse, 
            new Label("LMS Chapter:"), chpBox,
            new Label("LMS Lesson (Optional):"), lesBox
        );
        lmsBox.setVisible(false);
        lmsBox.setManaged(false);
        
        if ("LMS".equals(ConfigService.getProductType())) {
            lmsBox.setVisible(true);
            lmsBox.setManaged(true);
            radCloud.setSelected(true);
            
            loadLmsCourses();
            
            cmbCourse.setOnAction(e -> {
                cmbChapter.getItems().clear();
                cmbLesson.getItems().clear();
                CourseItem selected = cmbCourse.getValue();
                if (selected != null && selected.chapters != null) {
                    cmbChapter.getItems().addAll(selected.chapters);
                }
            });
            
            cmbChapter.setOnAction(e -> {
                cmbLesson.getItems().clear();
                ChapterItem chp = cmbChapter.getValue();
                if (chp != null && chp.lessons != null) {
                    LessonItem createNew = new LessonItem();
                    createNew.id = "";
                    createNew.title = "[ Create New Lesson ]";
                    cmbLesson.getItems().add(createNew);
                    cmbLesson.getItems().addAll(chp.lessons);
                    cmbLesson.getSelectionModel().selectFirst();
                }

                CourseItem crs = cmbCourse.getValue();
                if (crs != null && chp != null) {
                    String safeCrs = crs.title.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
                    String safeChp = chp.title.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
                    prefixField.setText("courses/" + safeCrs + "/" + safeChp);
                }
            });
        }
        
        if ("LMS".equals(ConfigService.getProductType())) {
            rightOpts.getChildren().addAll(new Label("LMS Course Destination:"), lmsBox);
        } else {
            rightOpts.getChildren().addAll(new Label("Destination:"), radCloud, radLocal, new Label("Cloud Prefix:"), prefixBox);
        }
        
        chkDeleteLocal = new CheckBox("Delete local files after successful upload");
        rightOpts.getChildren().addAll(new Region(), chkDeleteLocal);
        
        splitOpts.getChildren().addAll(leftOpts, new Separator(javafx.geometry.Orientation.VERTICAL), midOpts, new Separator(javafx.geometry.Orientation.VERTICAL), rightOpts);
        optBox.getChildren().add(splitOpts);
        
        StackPane optWrapper = new StackPane(optBox, optTitle);
        StackPane.setAlignment(optTitle, Pos.TOP_LEFT);
        
        
        // --- Actions ---
        HBox actions = new HBox(10);
        startBtn = new Button("Start Process");
        startBtn.setStyle("-fx-font-weight: bold; -fx-base: #4CAF50;");
        
        cancelBtn = new Button("Cancel");
        cancelBtn.setDisable(true);
        
        actions.getChildren().addAll(startBtn, cancelBtn);
        
        // --- Console Output ---
        VBox consoleBox = new VBox(5);
        Label consTitle = new Label("Console Output");
        consTitle.setStyle("-fx-font-weight: bold;");
        
        consoleOutput = new TextArea();
        consoleOutput.setEditable(false);
        consoleOutput.setWrapText(true);
        consoleOutput.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4; -fx-font-family: 'Consolas', monospace;");
        consoleOutput.setPrefRowCount(10);
        VBox.setVgrow(consoleOutput, Priority.ALWAYS);
        
        Button clearLogBtn = new Button("Clear Logs");
        clearLogBtn.setOnAction(e -> consoleOutput.clear());
        HBox logHeader = new HBox(10, consTitle, new Region(), clearLogBtn);
        HBox.setHgrow(logHeader.getChildren().get(1), Priority.ALWAYS);
        
        consoleBox.getChildren().addAll(logHeader, consoleOutput);
        VBox.setVgrow(consoleBox, Priority.ALWAYS);

        this.getChildren().addAll(title, ioWrapper, optWrapper, actions, consoleBox);
        
        // Event Handlers
        browseInBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Video");
            File f = fc.showOpenDialog(this.getScene().getWindow());
            if (f != null) {
                inputPathField.setText(f.getAbsolutePath());
                if (outputPathField.getText().isEmpty()) {
                    outputPathField.setText(f.getParent());
                }
            }
        });
        
        browseOutBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Output Directory");
            File d = dc.showDialog(this.getScene().getWindow());
            if (d != null) {
                outputPathField.setText(d.getAbsolutePath());
            }
        });
        
        startBtn.setOnAction(e -> startProcess());
    }
    
    private void log(String msg) {
        Platform.runLater(() -> {
            consoleOutput.appendText(msg + "\n");
        });
    }

    private void startProcess() {
        if (isProcessing.get()) return;

        String inPath = inputPathField.getText();
        String outPath = outputPathField.getText();
        
        if (inPath.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Select input video").show();
            return;
        }
        if (outPath.isEmpty()) {
            outPath = new File(inPath).getParent();
        }
        
        if ("LMS".equals(ConfigService.getProductType())) {
            if (cmbCourse.getValue() == null || cmbChapter.getValue() == null) {
                new Alert(Alert.AlertType.WARNING, "Please select an LMS Course and Chapter before starting.").show();
                return;
            }
        }

        File inFile = new File(inPath);
        
        // Auto create subdirectory based on file name
        String videoName = inFile.getName();
        int dotIdx = videoName.lastIndexOf('.');
        if (dotIdx > 0) {
            videoName = videoName.substring(0, dotIdx);
        }
        Path outDir = Paths.get(outPath, videoName);
        
        boolean upload = radCloud.isSelected();
        String prefix = prefixField.getText().trim();
        
        // Gather options
        VideoEncodingOptions options = new VideoEncodingOptions();
        options.selectedQualities = new HashMap<>();
        for (String q : qualityChecks.keySet()) {
            if (qualityChecks.get(q).isSelected()) {
                options.selectedQualities.put(q, bitrateFields.get(q).getText());
            }
        }
        
        options.useNvenc = chkNvenc.isSelected();
        options.parallel = chkParallel.isSelected();
        options.liveMode = chkLiveMode.isSelected();
        options.codec = cmbCodec.getValue();
        options.useCrf = chkCrf.isSelected();
        try { options.crfValue = Integer.parseInt(txtCrf.getText()); } catch(Exception e){}
        try { options.segmentTime = Integer.parseInt(txtSegment.getText()); } catch(Exception e){}
        options.singleFolder = chkSingleFolder.isSelected();
        options.generateMp4 = chkGenerateMp4.isSelected();
        options.keepAllAudio = chkKeepAllAudio.isSelected();
        
        boolean doTranscode = !options.selectedQualities.isEmpty() || options.generateMp4;

        isProcessing.set(true);
        startBtn.setDisable(true);
        cancelBtn.setDisable(false);
        log("Starting process for: " + inFile.getName());
        
        cancelBtn.setOnAction(evt -> {
            log("Cancelling process...");
            com.educater.video.VideoProcessor.cancel();
            finishProcess();
        });
        
        if (!Files.exists(outDir)) {
            try { Files.createDirectories(outDir); } catch(Exception ignored){}
        }

        TaskManager.submitHeavyTask(() -> {
            try {
                if (doTranscode) {
                    log("Initiating Transcoding to " + outDir.toAbsolutePath());
                    VideoProcessor.encodeVideo(inFile.toPath(), outDir, options, new VideoProcessor.EncodingProgressListener() {
                        @Override
                        public void onProgress(String quality, double percentage) {
                        }

                        @Override
                        public void onComplete(String quality, boolean success, String message, Path outputPath) {
                            if (success) {
                                log("Transcoding Complete for " + quality);
                                if (outputPath == null) { // Main finish call after parallel/seq ends
                                    if (upload) {
                                        doUploadFolder(outDir, prefix);
                                    } else {
                                        finishProcess();
                                    }
                                }
                            } else {
                                log("Transcoding Failed for " + quality + ": " + message);
                                if (outputPath == null) finishProcess();
                            }
                        }
                    });
                } else {
                    if (upload) {
                        doUploadFolder(outDir, prefix);
                    } else {
                        log("Nothing to do (No transcode, no upload).");
                        finishProcess();
                    }
                }
            } catch (Exception ex) {
                log("Error: " + ex.getMessage());
                finishProcess();
            }
        });
    }

    private void doUploadFolder(Path folder, String prefix) {
        log("Queueing folder upload for " + folder.getFileName() + " to cloud...");
        if (r2Service == null) {
            log("Error: Cloud storage not configured.");
            finishProcess();
            return;
        }
        
        String targetPrefix = prefix;
        if (!targetPrefix.endsWith("/") && !targetPrefix.isEmpty()) targetPrefix += "/";
        targetPrefix += folder.getFileName().toString();
        
        final String finalTargetPrefix = targetPrefix;
        final String taskId = "Folder: " + folder.getFileName().toString();
        UploadTask task = new UploadTask(taskId);
        QueueTab.addUploadTask(task);
        
        final java.util.concurrent.atomic.AtomicBoolean isPaused = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean isCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        task.setOnPause(() -> isPaused.set(true));
        task.setOnResume(() -> isPaused.set(false));
        task.setOnCancel(() -> isCancelled.set(true));
        
        com.educater.r2.UploadTaskController controller = new com.educater.r2.UploadTaskController() {
            @Override public boolean isCancelled() { return isCancelled.get(); }
            @Override public boolean isPaused() { return isPaused.get(); }
            @Override public void waitIfPaused() throws InterruptedException {
                while (isPaused.get() && !isCancelled.get()) {
                    Thread.sleep(500);
                }
            }
        };
        
        Runnable uploadAction = new Runnable() {
            int attempts = 0;
            final int MAX_ATTEMPTS = 5;

            @Override
            public void run() {
                try {
                    attempts++;
                    if (attempts > 1) {
                        Platform.runLater(() -> task.setStatusText("Retrying... (" + attempts + "/" + MAX_ATTEMPTS + ")"));
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                    if (isCancelled.get()) return;

                    r2Service.uploadFolder(folder, finalTargetPrefix, (bytesTransferred, totalBytes, currentFile, speedMBps, timeRemainingSeconds) -> {
                        double pct = totalBytes > 0 ? (double) bytesTransferred / totalBytes : 0;
                        String status = String.format("%.1f MB/s (%ds left)", speedMBps, timeRemainingSeconds);
                        if (attempts > 1) status = "[Retry " + attempts + "] " + status;
                        QueueTab.updateTaskProgress(taskId, pct, status);
                    }, controller);
                    
                    log("Upload Successful for " + folder.getFileName());
                    Platform.runLater(() -> {
                        task.setCompleted(true);
                        task.setFailed(false);
                        task.setStatusText("Completed");
                        task.setProgress(1.0);
                    });
                    
                    if ("LMS".equals(ConfigService.getProductType())) {
                        CourseItem crs = cmbCourse.getValue();
                        ChapterItem chp = cmbChapter.getValue();
                        if (crs != null && chp != null) {
                            try {
                                String publicUrl = ConfigService.getR2PublicUrl();
                                if (publicUrl == null || publicUrl.isEmpty()) {
                                    publicUrl = "https://cdn.example.com";
                                }
                                String masterUrl = publicUrl;
                                if (!masterUrl.endsWith("/")) masterUrl += "/";
                                masterUrl += finalTargetPrefix + "/master.m3u8";
                                
                                JsonObject payload = new JsonObject();
                                payload.addProperty("courseId", crs.id);
                                payload.addProperty("chapterId", chp.id);
                                
                                LessonItem selLesson = cmbLesson.getValue();
                                if (selLesson != null && !selLesson.id.isEmpty()) {
                                    payload.addProperty("lessonId", selLesson.id);
                                    payload.addProperty("title", selLesson.title);
                                } else if (selLesson != null && pendingLessonData != null) {
                                    payload.addProperty("title", pendingLessonData.title);
                                    payload.addProperty("description", pendingLessonData.description);
                                    payload.addProperty("is_free", pendingLessonData.isFree);
                                    pendingLessonData = null;
                                } else {
                                    payload.addProperty("title", folder.getFileName().toString());
                                }
                                
                                payload.addProperty("videoUrl", masterUrl);
                                if (!payload.has("is_free")) payload.addProperty("is_free", false);
                                
                                String url = ConfigService.getLmsBackendUrl() + "/api/lessons";
                                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
                                String token = ConfigService.getLmsJwtToken();
                                if (token != null && !token.isBlank()) {
                                    reqBuilder.header("Authorization", "Bearer " + token);
                                }
                                HttpRequest req = reqBuilder.build();
                                
                                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                                log("LMS Lesson creation response: " + res.statusCode() + " " + res.body());
                            } catch (Exception ex) {
                                log("Failed to post lesson to LMS: " + ex.getMessage());
                            }
                        }
                    }
                    if (chkDeleteLocal.isSelected()) {
                        log("Deleting local encoded files from: " + folder.toAbsolutePath());
                        try {
                            Files.walk(folder)
                                .sorted(java.util.Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                            log("Deleted local encoded files successfully.");
                        } catch (Exception ex) {
                            log("Failed to delete local files: " + ex.getMessage());
                        }
                    }
                    if (onComplete != null) Platform.runLater(onComplete);
                } catch (Exception e) {
                    log("Upload failed (Attempt " + attempts + "): " + e.getMessage());
                    if (attempts < MAX_ATTEMPTS && !isCancelled.get()) {
                        run(); // Automatic retry
                    } else {
                        Platform.runLater(() -> {
                            task.setFailed(true);
                            task.setStatusText("Failed after " + attempts + " attempts: " + e.getMessage());
                        });
                        if (onComplete != null) Platform.runLater(onComplete);
                    }
                }
            }
        };

        task.setOnRetry(() -> TaskManager.submitIoTask(uploadAction));
        
        TaskManager.submitIoTask(uploadAction);
        
        log("=> TRANSCODING FINISHED! Folder upload has been queued in the background (See Queue Tab).");
        
        // Don't block the transcoder tab, let the user start another one
        finishProcess();
    }
    
    private void finishProcess() {
        Platform.runLater(() -> {
            isProcessing.set(false);
            startBtn.setDisable(false);
            cancelBtn.setDisable(true);
            log("Process finished.");
        });
    }

    private void loadLmsCourses() {
        TaskManager.submitHeavyTask(() -> {
            try {
                String url = ConfigService.getLmsBackendUrl() + "/api/courses";
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
                String token = ConfigService.getLmsJwtToken();
                if (token != null && !token.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + token);
                }
                HttpRequest req = reqBuilder.build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    log("LMS Backend returned status " + res.statusCode() + ": " + res.body());
                    return;
                }
                
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(res.body());
                if (!parsed.isJsonObject()) {
                    log("Expected JSON object from LMS Backend but got: " + res.body());
                    return;
                }
                
                JsonObject root = parsed.getAsJsonObject();
                if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                    JsonArray data = root.getAsJsonArray("data");
                    java.util.List<CourseItem> courses = new java.util.ArrayList<>();
                    for (JsonElement e : data) {
                        JsonObject obj = e.getAsJsonObject();
                        CourseItem c = new CourseItem();
                        c.id = obj.has("id") ? obj.get("id").getAsString() : "";
                        c.title = obj.has("title") ? obj.get("title").getAsString() : "";
                        c.chapters = new java.util.ArrayList<>();
                        if (obj.has("chapters") && obj.get("chapters").isJsonArray()) {
                            for (JsonElement ce : obj.getAsJsonArray("chapters")) {
                                JsonObject cObj = ce.getAsJsonObject();
                                ChapterItem chap = new ChapterItem();
                                chap.id = cObj.has("id") ? cObj.get("id").getAsString() : "";
                                chap.title = cObj.has("title") ? cObj.get("title").getAsString() : "";
                                chap.lessons = new java.util.ArrayList<>();
                                if (cObj.has("lessons") && cObj.get("lessons").isJsonArray()) {
                                    for (JsonElement le : cObj.getAsJsonArray("lessons")) {
                                        JsonObject lObj = le.getAsJsonObject();
                                        LessonItem lesson = new LessonItem();
                                        lesson.id = lObj.has("id") ? lObj.get("id").getAsString() : "";
                                        lesson.title = lObj.has("title") ? lObj.get("title").getAsString() : "";
                                        chap.lessons.add(lesson);
                                    }
                                }
                                c.chapters.add(chap);
                            }
                        }
                        courses.add(c);
                    }
                    Platform.runLater(() -> {
                        cmbCourse.getItems().clear();
                        cmbCourse.getItems().addAll(courses);
                    });
                }
            } catch (Exception ex) {
                log("Failed to fetch LMS courses: " + ex.getMessage());
            }
        });
    }
}
