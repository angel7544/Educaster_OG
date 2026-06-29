package com.educater.ui;

import com.educater.auth.AuthService;
import com.educater.db.MongoService;
import com.educater.config.ConfigService;
import com.educater.net.NetUtil;
import com.educater.mux.JwtUtil;
import com.educater.mux.LiveStreamInfo;
import com.educater.mux.MuxApi;
import com.educater.model.StreamRecord;
import com.educater.model.UploadRecord;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.control.Hyperlink;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;



import java.awt.Desktop;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainApp extends Application {
    private MongoService mongo;
    private AuthService auth;
    private MuxApi mux;

    private String currentEmail = null;
    private String currentPlaybackId = null;
    private String currentLiveId = null;

    private ObservableList<StreamRecord> streamItems = FXCollections.observableArrayList();
    private TableView<StreamRecord> streamTable = new TableView<>();
    private ObservableList<UploadRecord> uploadItems = FXCollections.observableArrayList();
    private ListView<UploadRecord> uploadList = new ListView<>();

    private boolean darkMode = false;
    private Button adminSettingsBtn;

    private TabPane tabs;
    private Tab r2Tab;
    private VBox r2Container;
    private VBox r2LoginView;
    private Pane r2MainView;
    
    private Tab transcoderAppTab;
    private Tab queueAppTab;
    private VBox transcoderContainer;
    private VBox queueContainer;
    
    private BorderPane rootPane;
    
    private com.educater.r2.R2Service r2Service;

    @Override
    public void start(Stage stage) {
        try {
            mongo = new MongoService();
        } catch (Exception ex) {
            mongo = null;
        }
        auth = new com.educater.auth.AuthService(mongo);
        mux = new MuxApi();

        if (!com.educater.video.FFmpegManager.isFFmpegInstalled()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "FFmpeg is required for video encoding. Do you want to download and install it now?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    Alert progress = new Alert(Alert.AlertType.INFORMATION, "Downloading FFmpeg...");
                    progress.show();
                    com.educater.video.FFmpegManager.downloadAndInstall(new com.educater.video.FFmpegManager.DownloadProgressListener() {
                        @Override
                        public void onProgress(double percentage) {
                            Platform.runLater(() -> progress.setContentText(String.format("Downloading... %.1f%%", percentage * 100)));
                        }
                        @Override
                        public void onComplete(boolean success, String message) {
                            Platform.runLater(() -> {
                                progress.close();
                                if (success) new Alert(Alert.AlertType.INFORMATION, "FFmpeg installed successfully!").show();
                                else new Alert(Alert.AlertType.ERROR, "Install failed: " + message).show();
                            });
                        }
                    });
                }
            });
        }

        rootPane = new BorderPane();
        rootPane.setTop(buildLoginPanel());
        
        rootPane.setCenter(buildCenterPanel()); // TabPane setup happens here

        Scene scene = new Scene(rootPane, 1100, 700);
        stage.setTitle("EduCaster Live – Mux Desktop");
        UIUtils.setIcon(stage);
        stage.setScene(scene);
        stage.show();
        // Show login window on startup
        new StartupLoginWindow(auth, email -> {
            currentEmail = email;
            refreshStreams();
            refreshUploads();
            if (adminSettingsBtn != null && email.equals(ConfigService.getAdminEmail())) {
                adminSettingsBtn.setDisable(false);
            }
            updateAccessControl();
        }).showAtStartup(stage);

        if (mongo == null) {
            new Alert(Alert.AlertType.WARNING, "MongoDB not reachable. Login/Signup may fail until DB is available.").showAndWait();
        }
        if (!NetUtil.isOnline()) {
            new Alert(Alert.AlertType.WARNING, "No internet connection detected. Streaming and uploads will not work until you are online.").showAndWait();
        }
    }

    private Pane buildLoginPanel() {
        HBox top = new HBox(10);
        top.setPadding(new Insets(8));
        top.setAlignment(Pos.CENTER_LEFT);

        Button teacherLoginBtn = new Button("Teacher Login");
        teacherLoginBtn.setOnAction(e -> {
            new TeacherLoginWindow(auth, email -> {
                currentEmail = email;
                refreshStreams();
                refreshUploads();
                updateAccessControl();
            }).show();
        });

        Button adminLoginBtn = new Button("Admin Login");
        adminLoginBtn.setOnAction(e -> {
            new AdminLoginWindow(auth, email -> {
                currentEmail = email;
                refreshStreams();
                refreshUploads();
                if (adminSettingsBtn != null) adminSettingsBtn.setDisable(false);
                updateAccessControl();
            }).show();
        });

        adminSettingsBtn = new Button("Admin Settings");
        adminSettingsBtn.setDisable(true);
        adminSettingsBtn.setOnAction(e -> new AdminSettingsWindow().show());

        Button darkBtn = new Button("Toggle Dark");
        darkBtn.setOnAction(e -> toggleDark(top.getScene()));

        Button infoBtn = new Button("App Info");
        infoBtn.setOnAction(e -> showInfo());

        Button helpBtn = new Button("Help");
        helpBtn.setOnAction(e -> new HelpWindow().show());

        top.getChildren().addAll(new Label("Access:"), teacherLoginBtn, adminLoginBtn, adminSettingsBtn, darkBtn, infoBtn, helpBtn);
        return top;
    }

    private Tab streamsTab;
    private Tab uploadsTab;
    private VBox streamsContainer;
    private VBox uploadsContainer;
    private VBox streamsContent;
    private VBox uploadsContent;

    private TabPane buildCenterPanel() {
        TabPane tabs = new TabPane();

        // Streams tab
        streamsContent = new VBox(10);
        streamsContent.setPadding(new Insets(8));
        Button createBtn = new Button("Setup Live Class");
        Button copyBtn = new Button("Copy Details");
        Button urlBtn = new Button("Get Public URL");
        Button disableBtn = new Button("Disable Stream");
        Button completeBtn = new Button("Complete Stream");
        Button deleteBtn = new Button("Delete Stream");
        Button obsBtn = new Button("Launch OBS");
        // TableView with proper columns for stream details
        streamTable.setItems(streamItems);
        TableColumn<StreamRecord, String> colPlayback = new TableColumn<>("Playback ID");
        colPlayback.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().playbackId));
        colPlayback.setPrefWidth(180);

        TableColumn<StreamRecord, String> colLive = new TableColumn<>("Live ID");
        colLive.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().liveStreamId));
        colLive.setPrefWidth(180);

        TableColumn<StreamRecord, String> colKey = new TableColumn<>("Stream Key");
        colKey.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().streamKey));
        colKey.setPrefWidth(220);

        TableColumn<StreamRecord, String> colRtmp = new TableColumn<>("RTMP URL");
        colRtmp.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().rtmpUrl));
        colRtmp.setPrefWidth(260);

        TableColumn<StreamRecord, String> colCreated = new TableColumn<>("Created");
        colCreated.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().createdAtIso));
        colCreated.setPrefWidth(200);

        streamTable.getColumns().setAll(colPlayback, colLive, colKey, colRtmp, colCreated);
        streamTable.setPrefHeight(320);

        // Add Context Menu for Copying
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyRtmpItem = new MenuItem("Copy RTMP URL");
        copyRtmpItem.setOnAction(e -> {
            StreamRecord sel = streamTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.rtmpUrl);
        });
        MenuItem copyKeyItem = new MenuItem("Copy Stream Key");
        copyKeyItem.setOnAction(e -> {
            StreamRecord sel = streamTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.streamKey);
        });
        MenuItem copyPlaybackItem = new MenuItem("Copy Playback ID");
        copyPlaybackItem.setOnAction(e -> {
            StreamRecord sel = streamTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.playbackId);
        });
        contextMenu.getItems().addAll(copyRtmpItem, copyKeyItem, copyPlaybackItem);
        streamTable.setContextMenu(contextMenu);

        createBtn.setOnAction(e -> doCreateStream());
        copyBtn.setOnAction(e -> doCopyStreamDetails());
        urlBtn.setOnAction(e -> doGetPublicUrl());
        disableBtn.setOnAction(e -> doDisableStream());
        completeBtn.setOnAction(e -> doCompleteStream());
        deleteBtn.setOnAction(e -> doDeleteStream());
        obsBtn.setOnAction(e -> {
            StreamRecord sel = getSelectedStreamRecord();
            String key = sel != null ? sel.streamKey : null;
            doLaunchObs(key);
        });

        streamsContent.getChildren().addAll(
                new HBox(10, createBtn, copyBtn, urlBtn, disableBtn, completeBtn, deleteBtn, obsBtn),
                new Label("Stream History"),
                streamTable
        );
        
        streamsContainer = new VBox();
        streamsContainer.setFillWidth(true);
        streamsTab = new Tab("Streams", streamsContainer);
        streamsTab.setClosable(false);

        // Uploads tab
        uploadsContent = new VBox(10);
        uploadsContent.setPadding(new Insets(8));
        Button uploadBtn = new Button("Upload Video");
        uploadList.setItems(uploadItems);
        uploadList.setPrefHeight(320);
        uploadBtn.setOnAction(e -> doUploadVideo());
        uploadsContent.getChildren().addAll(
                new HBox(10, uploadBtn),
                new Label("Upload History"),
                uploadList
        );
        
        uploadsContainer = new VBox();
        uploadsContainer.setFillWidth(true);
        uploadsTab = new Tab("Uploads", uploadsContainer);
        uploadsTab.setClosable(false);
        
        // Transcoder Tab
        transcoderAppTab = new Tab("Transcoder & Upload");
        transcoderAppTab.setClosable(false);
        transcoderContainer = new VBox();
        transcoderContainer.setFillWidth(true);
        transcoderAppTab.setContent(transcoderContainer);
        
        // Queue Tab
        queueAppTab = new Tab("Queue");
        queueAppTab.setClosable(false);
        queueContainer = new VBox();
        queueContainer.setFillWidth(true);
        queueAppTab.setContent(queueContainer);
        
        // Cloud Storage Tab
        r2Tab = new Tab("Cloud Explorer");
        r2Tab.setClosable(false);
        
        // Create container and views
        r2Container = new VBox();
        r2Container.setFillWidth(true);
        
        r2LoginView = new VBox(20);
        r2LoginView.setAlignment(Pos.CENTER);
        r2LoginView.setPadding(new Insets(40));
        
        Label r2LockLabel = new Label("🔒 Restricted Access");
        r2LockLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label r2LockMsg = new Label("You must be logged in as an Administrator to manage Cloud Storage.");
        r2LockMsg.setStyle("-fx-font-size: 14px;");
        
        Button r2AdminLoginBtn = new Button("Admin Login");
        r2AdminLoginBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 20;");
        r2AdminLoginBtn.setOnAction(e -> {
            new AdminLoginWindow(auth, email -> {
                currentEmail = email;
                refreshStreams();
                refreshUploads();
                if (adminSettingsBtn != null) adminSettingsBtn.setDisable(false);
                updateAccessControl();
            }).show();
        });
        
        r2LoginView.getChildren().addAll(r2LockLabel, r2LockMsg, r2AdminLoginBtn);
        
        r2Tab.setContent(r2Container);

        tabs.getTabs().addAll(streamsTab, uploadsTab, r2Tab, transcoderAppTab, queueAppTab);
        
        // Initial state update
        updateAccessControl();
        
        return tabs;
    }

    private void updateAccessControl() {
        boolean isLoggedIn = currentEmail != null;
        String adminEmail = ConfigService.getAdminEmail();
        boolean isAdmin = isLoggedIn && currentEmail.equals(adminEmail);
        
        // Update Streams Tab
        if (streamsContainer != null) {
            streamsContainer.getChildren().clear();
            if (isLoggedIn) {
                streamsContainer.getChildren().add(streamsContent);
            } else {
                streamsContainer.getChildren().add(buildLoginRequiredView("Log in to manage streams"));
            }
        }
        
        // Update Uploads Tab
        if (uploadsContainer != null) {
            uploadsContainer.getChildren().clear();
            if (isLoggedIn) {
                uploadsContainer.getChildren().add(uploadsContent);
            } else {
                uploadsContainer.getChildren().add(buildLoginRequiredView("Log in to view uploads"));
            }
        }

        // Update R2 Tab
        if (r2Container != null) {
            r2Container.getChildren().clear();
            if (isAdmin) {
                initR2Service();
                if (r2Service != null) {
                    r2Container.getChildren().add(new CloudExplorerTab(r2Service));
                } else {
                    r2Container.getChildren().add(new Label("Cloud Storage not configured."));
                }
            } else {
                r2Container.getChildren().add(r2LoginView);
            }
        }
        
        boolean isLms = "LMS".equals(ConfigService.getProductType());
        boolean canAccessTranscoder = isAdmin || (isLoggedIn && isLms);

        // Update Transcoder and Queue Tabs
        if (transcoderContainer != null) {
            transcoderContainer.getChildren().clear();
            if (canAccessTranscoder) {
                initR2Service();
                if (r2Service != null) {
                    transcoderContainer.getChildren().add(new TranscoderTab(r2Service, this::refreshUploads));
                } else {
                    transcoderContainer.getChildren().add(new Label("Cloud Storage not configured."));
                }
            } else {
                transcoderContainer.getChildren().add(buildLoginRequiredView(isLms ? "Teacher access required for Transcoder" : "Admin access required for Transcoder"));
            }
        }
        
        if (queueContainer != null) {
            queueContainer.getChildren().clear();
            if (canAccessTranscoder) {
                queueContainer.getChildren().add(new QueueTab());
            } else {
                queueContainer.getChildren().add(buildLoginRequiredView(isLms ? "Teacher access required for Queue" : "Admin access required for Queue"));
            }
        }
    }
    
    private void initR2Service() {
        if (r2Service == null) {
            String endpointUrl = ConfigService.getCloudEndpointUrl();
            String accessKey = ConfigService.getR2AccessKey();
            String secretKey = ConfigService.getR2SecretKey();
            String bucket = ConfigService.getR2BucketName();
            String pubUrl = ConfigService.getR2PublicUrl();
            
            if ("R2".equals(ConfigService.getCloudProvider()) && (endpointUrl == null || endpointUrl.isEmpty())) {
                String accId = ConfigService.getR2AccountId();
                if (!accId.isEmpty()) {
                    endpointUrl = "https://" + accId + ".r2.cloudflarestorage.com";
                }
            }
            
            if (!accessKey.isEmpty() && !secretKey.isEmpty() && !bucket.isEmpty()) {
                try {
                    r2Service = new com.educater.r2.R2Service(endpointUrl, accessKey, secretKey, bucket, pubUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private VBox buildLoginRequiredView(String message) {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        
        Label lockLabel = new Label("🔒 Login Required");
        lockLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 14px;");
        
        box.getChildren().addAll(lockLabel, msgLabel);
        return box;
    }

    private void toggleDark(Scene scene) {
        darkMode = !darkMode;
        if (darkMode) {
            scene.getStylesheets().add(resourceToUrl("/style.css"));
        } else {
            scene.getStylesheets().remove(resourceToUrl("/style.css"));
        }
    }

    private void showInfo() {
        Stage stage = new Stage();
        UIUtils.setIcon(stage);
        stage.setTitle("EduCaster Live – About & Help");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setResizable(true); // Made responsive

        // Branding
        ImageView brandingLogo = new ImageView(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        brandingLogo.setFitWidth(160);
        brandingLogo.setPreserveRatio(true);

        Label appTitle = new Label("EduCaster Live Desktop");
        appTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label developer = new Label("Developer: Angel Singh • BR-31 Technologies");
        developer.setStyle("-fx-font-size: 13px; -fx-opacity: 0.9;");

        // Website link
        Hyperlink site = new Hyperlink("https://br31tech.live");
        site.setOnAction(e -> getHostServices().showDocument("https://br31tech.live"));

        // Features
        Label featuresTitle = new Label("Features");
        featuresTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox features = new VBox(6,
                new Label("• Login & Signup (MongoDB)"),
                new Label("• Create Mux Live Streams"),
                new Label("• View & Copy RTMP URL / Stream Key"),
                new Label("• Table of Previous Streams with columns"),
                new Label("• Preview Player (WebView) or Browser"),
                new Label("• Signed Playback Token Generator"),
                new Label("• Live Viewer Count"),
                new Label("• Launch OBS from the app"),
                new Label("• Dark Mode toggle")
        );

        // How it works
        Label howTitle = new Label("How It Works");
        howTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox how = new VBox(6,
                new Label("1) Login (Teacher/Admin)"),
                new Label("2) Click 'Create Stream' to generate RTMP URL & Stream Key"),
                new Label("3) Use 'Copy Details' or open the per-field dialog to copy"),
                new Label("4) Paste into OBS/Encoder and start streaming"),
                new Label("5) Use 'Preview Stream' to watch and track viewer count"),
                new Label("6) Use 'Get Public URL' to share the HLS playback link")
        );

        // Actions
        Button close = new Button("Close");
        close.setOnAction(e -> stage.close());
        Button openSite = new Button("Open Website");
        openSite.setOnAction(e -> getHostServices().showDocument("https://br31tech.live"));
        HBox actions = new HBox(10, openSite, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12,
                brandingLogo,
                appTitle,
                developer,
                site,
                new Separator(),
                featuresTitle,
                features,
                new Separator(),
                howTitle,
                how,
                new Separator(),
                actions
        );
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(0));

        stage.setScene(new Scene(scroll, 560, 600));
        stage.showAndWait();
    }

    private void refreshStreams() {
        if (currentEmail == null) return;
        List<StreamRecord> records = mongo.getStreamsByEmail(currentEmail);
        streamItems.setAll(records);
    }

    private void refreshUploads() {
        if (currentEmail == null || mongo == null) return;
        List<UploadRecord> records = mongo.getUploadsByEmail(currentEmail);
        uploadItems.setAll(records);
    }
    
    private void doUploadVideo() {
        if (transcoderAppTab != null && transcoderAppTab.getTabPane() != null) {
            transcoderAppTab.getTabPane().getSelectionModel().select(transcoderAppTab);
        }
    }

    private void doCreateStream() {
        if (currentEmail == null) { new Alert(Alert.AlertType.ERROR, "Login first").showAndWait(); return; }
        if (!NetUtil.isOnline()) { new Alert(Alert.AlertType.ERROR, "No internet connection").showAndWait(); return; }
        
        PublishLiveWindow publishWindow = new PublishLiveWindow(chapterId -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Chapter saved! Generate Live Stream credentials now?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    generateMuxStreamAndShowDetails(chapterId);
                }
            });
        });
        publishWindow.show();
    }

    private void generateMuxStreamAndShowDetails(String chapterId) {
        System.out.println("[Create Stream] Calling mux.createLiveStream()...");
        try {
            LiveStreamInfo info = mux.createLiveStream();
            System.out.println("[Create Stream] Success: playbackId=" + info.playbackId);
            currentPlaybackId = info.playbackId;
            currentLiveId = info.liveId;

            StreamRecord r = new StreamRecord();
            r.email = currentEmail;
            r.rtmpUrl = info.rtmpUrl;
            r.streamKey = info.streamKey;
            r.playbackId = info.playbackId;
            r.liveStreamId = info.liveId;
            mongo.saveStream(currentEmail, r);
            refreshStreams();

            // Fire and forget: update chapter with new video URL
            if (chapterId != null && !chapterId.isBlank()) {
                new Thread(() -> {
                    try {
                        String url = ConfigService.getLmsBackendUrl() + "/api/chapters/" + chapterId;
                        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
                        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
                        payload.addProperty("video_url", "https://stream.mux.com/" + info.playbackId + ".m3u8");
                        
                        java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url))
                            .header("Content-Type", "application/json")
                            .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString()));
                        String token = ConfigService.getLmsJwtToken();
                        if (token != null && !token.isBlank()) reqBuilder.header("Authorization", "Bearer " + token);
                        client.send(reqBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }

            showCopyDetailsDialog(info.rtmpUrl, info.streamKey, info.playbackId, info.liveId);

        } catch (Exception ex) {
            System.err.println("[Create Stream] Error: " + ex.getMessage());
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void doGetPublicUrl() {
        StreamRecord sel = getSelectedStreamRecord();
        String playbackId = sel != null ? sel.playbackId : currentPlaybackId;
        if (playbackId == null || playbackId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "No playback ID").showAndWait();
            return;
        }
        String publicUrl = "https://stream.mux.com/" + playbackId + ".m3u8";
        try {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(publicUrl);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        } catch (Throwable ignored) { }
        showPublicUrlDialog(publicUrl);
    }

    private void doCopyStreamDetails() {
        StreamRecord sel = getSelectedStreamRecord();
        if (sel == null && currentPlaybackId == null && currentLiveId == null) {
            new Alert(Alert.AlertType.ERROR, "No stream selected or details available").showAndWait();
            return;
        }
        // Prefer selected record; fallback to current in-memory values
        String rtmp = sel != null ? sel.rtmpUrl : null;
        String key = sel != null ? sel.streamKey : null;
        String playbackId = sel != null ? sel.playbackId : currentPlaybackId;
        String liveId = sel != null ? sel.liveStreamId : currentLiveId;

        showCopyDetailsDialog(rtmp, key, playbackId, liveId);
    }

    private String buildDetailsText(String rtmpUrl, String streamKey, String playbackId, String liveId) {
        StringBuilder sb = new StringBuilder();
        sb.append("RTMP URL: ").append(rtmpUrl == null ? "-" : rtmpUrl).append('\n');
        sb.append("Stream Key: ").append(streamKey == null ? "-" : streamKey).append('\n');
        sb.append("Playback ID: ").append(playbackId == null ? "-" : playbackId).append('\n');
        sb.append("Live ID: ").append(liveId == null ? "-" : liveId);
        return sb.toString();
    }

    private void copyToClipboard(String text) {
        try {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        } catch (Throwable ignored) { }
    }

    private void doDisableStream() {
        StreamRecord sel = getSelectedStreamRecord();
        String liveId = sel != null ? sel.liveStreamId : currentLiveId;
        if (liveId == null || liveId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "No live stream ID").showAndWait();
            return;
        }
        try {
            mux.disableLiveStream(liveId);
            new Alert(Alert.AlertType.INFORMATION, "Live stream disabled on Mux").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void doCompleteStream() {
        StreamRecord sel = getSelectedStreamRecord();
        String liveId = sel != null ? sel.liveStreamId : currentLiveId;
        if (liveId == null || liveId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "No live stream ID").showAndWait();
            return;
        }
        try {
            mux.completeLiveStream(liveId);
            new Alert(Alert.AlertType.INFORMATION, "Live stream marked as complete on Mux").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void doDeleteStream() {
        StreamRecord sel = getSelectedStreamRecord();
        if (sel == null) {
            new Alert(Alert.AlertType.ERROR, "Select a stream to delete").showAndWait();
            return;
        }
        String liveId = sel.liveStreamId;
        if (liveId == null || liveId.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "Selected stream has no live ID").showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete live stream on Mux and remove from history?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.OK) return;
        try {
            mux.deleteLiveStream(liveId);
            if (mongo != null && sel.id != null) {
                mongo.deleteStreamById(sel.id);
            }
            refreshStreams();
            new Alert(Alert.AlertType.INFORMATION, "Live stream deleted").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }



    private StreamRecord getSelectedStreamRecord() {
        return streamTable.getSelectionModel().getSelectedItem();
    }

    private void showCopyDetailsDialog(String rtmpUrl, String streamKey, String playbackId, String liveId) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Copy Stream Details");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        // Helper to add a labeled field with a copy button
        java.util.function.BiConsumer<Integer, String[]> addRow = (row, data) -> {
            String label = data[0];
            String value = data[1];
            Label lbl = new Label(label);
            TextField tf = new TextField(value == null ? "" : value);
            tf.setEditable(false);
            tf.setPrefWidth(420);
            Button copy = new Button("Copy");
            copy.setOnAction(e -> {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(tf.getText());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            });
            grid.add(lbl, 0, row);
            grid.add(tf, 1, row);
            grid.add(copy, 2, row);
        };

        addRow.accept(0, new String[]{"RTMP URL", rtmpUrl});
        addRow.accept(1, new String[]{"Stream Key", streamKey});
        addRow.accept(2, new String[]{"Playback ID", playbackId});
        addRow.accept(3, new String[]{"Live ID", liveId});

        // Copy All button
        Button copyAll = new Button("Copy All");
        copyAll.setOnAction(e -> {
            String msg = buildDetailsText(rtmpUrl, streamKey, playbackId, liveId);
            copyToClipboard(msg);
        });
        
        Button launchObsBtn = new Button("Launch OBS");
        launchObsBtn.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: bold;");
        launchObsBtn.setOnAction(e -> doLaunchObs(streamKey));

        HBox actions = new HBox(10, copyAll, launchObsBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, grid, actions);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showPublicUrlDialog(String publicUrl) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Public URL");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        Label lbl = new Label("HLS URL");
        TextField tf = new TextField(publicUrl);
        tf.setEditable(false);
        tf.setPrefWidth(520);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(tf.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });

        grid.add(lbl, 0, 0);
        grid.add(tf, 1, 0);
        grid.add(copy, 2, 0);

        VBox content = new VBox(12, grid);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void doLaunchObs(String streamKey) {
        if (streamKey != null && !streamKey.isBlank()) {
            copyToClipboard(streamKey);
            new Alert(Alert.AlertType.INFORMATION, "Stream key copied to clipboard! Paste it into OBS settings (Settings -> Stream).").showAndWait();
        }

        // 1. Try configured path first
        String customPath = ConfigService.getObsPath();
        if (customPath != null && !customPath.isBlank()) {
            java.io.File file = new java.io.File(customPath);
            if (file.exists()) {
                try {
                    new ProcessBuilder(customPath).directory(file.getParentFile()).start();
                    return;
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.WARNING, "Failed to launch custom OBS path: " + ex.getMessage() + "\nTrying standard paths...").showAndWait();
                }
            }
        }

        // 2. Try common OBS paths
        String[] paths = {
            "C:/Program Files/obs-studio/bin/64bit/obs64.exe",
            "C:/Program Files (x86)/obs-studio/bin/64bit/obs64.exe",
            "C:/Program Files/obs-studio/bin/32bit/obs32.exe",
            System.getenv("ProgramFiles") + "/obs-studio/bin/64bit/obs64.exe"
        };

        for (String path : paths) {
            java.io.File file = new java.io.File(path);
            if (file.exists()) {
                try {
                    // Set working directory to the bin folder so OBS can find its DLLs
                    new ProcessBuilder(path).directory(file.getParentFile()).start();
                    return;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        // Fallback: try just "obs" command
        try {
            new ProcessBuilder("obs").start();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "OBS Studio not found in standard locations.\nPlease install OBS or add it to your PATH.").showAndWait();
        }
    }



    private String resourceToUrl(String path) {
        return getClass().getResource(path).toExternalForm();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (mongo != null) mongo.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}