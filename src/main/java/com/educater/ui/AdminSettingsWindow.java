package com.educater.ui;

import com.educater.auth.AuthService;
import com.educater.config.ConfigService;
import com.educater.db.MongoService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;

public class AdminSettingsWindow {

    private AuthService auth;

    public AdminSettingsWindow() {
        // We need an instance of AuthService. 
        // Ideally this should be passed in, but for now we can create a temporary one if needed
        // or rely on static methods if we refactored. 
        // Since MainApp creates one, we should probably modify the constructor to accept it.
        // However, MainApp calls new AdminSettingsWindow().show().
        // Let's assume we can create a new MongoService/AuthService here or modify MainApp.
        // For simplicity in this session, let's instantiate a fresh connection since this is a modal.
        // Ideally, dependency injection should be used.
        try {
            MongoService mongo = new MongoService();
            this.auth = new AuthService(mongo);
        } catch (Exception e) {
            // If DB fails, auth will be null
        }
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Admin Settings");
        stage.initModality(Modality.APPLICATION_MODAL);
        try {
            stage.getIcons().add(new Image(getClass().getResource("/images/educaster.png").toExternalForm()));
        } catch (Exception ignored) { }

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabs.getTabs().addAll(
            new Tab("Configuration", buildConfigTab(stage)),
            new Tab("Teacher Management", buildTeacherTab())
        );

        VBox root = new VBox(tabs);
        Scene scene = new Scene(root, 650, 450);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private VBox buildConfigTab(Stage stage) {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);

        // Product Settings
        ComboBox<String> productCombo = new ComboBox<>();
        productCombo.getItems().addAll("General", "LMS");
        productCombo.setValue(ConfigService.getProductType());
        
        TextField lmsUrl = new TextField(ConfigService.getLmsBackendUrl());
        lmsUrl.setPromptText("LMS API Base URL (e.g. http://localhost:3000)");
        lmsUrl.setPrefWidth(300);

        TextField supabaseUrl = new TextField(ConfigService.getSupabaseUrl());
        supabaseUrl.setPromptText("Supabase URL (e.g. https://xxx.supabase.co)");
        supabaseUrl.setPrefWidth(300);

        SecretField supabaseAnonKey = new SecretField(ConfigService.getSupabaseAnonKey());
        
        productCombo.setOnAction(e -> {
            boolean isLms = "LMS".equals(productCombo.getValue());
            lmsUrl.setDisable(!isLms);
            supabaseUrl.setDisable(!isLms);
            supabaseAnonKey.getNode().setDisable(!isLms);
        });
        boolean isLmsInitial = "LMS".equals(productCombo.getValue());
        lmsUrl.setDisable(!isLmsInitial);
        supabaseUrl.setDisable(!isLmsInitial);
        supabaseAnonKey.getNode().setDisable(!isLmsInitial);
        
        // Mux Settings
        TextField tokenId = new TextField(ConfigService.getMuxTokenId());
        tokenId.setPromptText("Mux Token ID");
        SecretField tokenSecret = new SecretField(ConfigService.getMuxTokenSecret());
        SecretField signingSecret = new SecretField(ConfigService.getMuxSigningSecret());
        TextField signingKeyId = new TextField(ConfigService.getMuxSigningKeyId());
        signingKeyId.setPromptText("Mux Signing Key ID (Optional)");

        // OBS Settings
        TextField obsPathField = new TextField(ConfigService.getObsPath());
        obsPathField.setPromptText("Path to OBS executable (obs64.exe)");
        obsPathField.setPrefWidth(300);
        Button browseObsBtn = new Button("Browse...");
        browseObsBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select OBS Executable");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executables", "*.exe"));
            File f = fc.showOpenDialog(stage);
            if (f != null) {
                obsPathField.setText(f.getAbsolutePath());
            }
        });
        HBox obsBox = new HBox(5, obsPathField, browseObsBtn);

        // Cloud Storage Settings
        ComboBox<String> providerCombo = new ComboBox<>();
        providerCombo.getItems().addAll("Cloudflare R2", "AWS S3");
        providerCombo.setValue("S3".equals(ConfigService.getCloudProvider()) ? "AWS S3" : "Cloudflare R2");
        
        TextField endpointUrl = new TextField(ConfigService.getCloudEndpointUrl());
        endpointUrl.setPromptText("Endpoint URL (Leave empty for standard AWS S3)");
        
        TextField r2AccountId = new TextField(ConfigService.getR2AccountId());
        r2AccountId.setPromptText("Account ID (R2 only)");
        TextField r2AccessKey = new TextField(ConfigService.getR2AccessKey());
        r2AccessKey.setPromptText("Access Key ID");
        SecretField r2SecretKey = new SecretField(ConfigService.getR2SecretKey());
        TextField r2BucketName = new TextField(ConfigService.getR2BucketName());
        r2BucketName.setPromptText("Bucket Name");
        TextField r2PublicUrl = new TextField(ConfigService.getR2PublicUrl());
        r2PublicUrl.setPromptText("Public URL (e.g. https://cdn.example.com)");

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        int row = 0;
        
        // Section: Product
        Label productHeader = new Label("Product Options");
        productHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        grid.add(productHeader, 0, row++, 2, 1);
        
        grid.add(new Label("Product Type:"), 0, row);
        grid.add(productCombo, 1, row++);
        grid.add(new Label("LMS API URL:"), 0, row);
        grid.add(lmsUrl, 1, row++);
        grid.add(new Label("Supabase URL:"), 0, row);
        grid.add(supabaseUrl, 1, row++);
        grid.add(new Label("Supabase Anon Key:"), 0, row);
        grid.add(supabaseAnonKey.getNode(), 1, row++);
        
        grid.add(new Separator(), 0, row++, 2, 1);
        
        // Section: Mux
        Label muxHeader = new Label("Mux Video Streaming");
        muxHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        grid.add(muxHeader, 0, row++, 2, 1);
        
        grid.add(new Label("Mux Token ID:"), 0, row);
        grid.add(tokenId, 1, row++);
        grid.add(new Label("Mux Token Secret:"), 0, row);
        grid.add(tokenSecret.getNode(), 1, row++);
        grid.add(new Label("Mux Signing Secret:"), 0, row);
        grid.add(signingSecret.getNode(), 1, row++);
        grid.add(new Label("Mux Signing Key ID:"), 0, row);
        grid.add(signingKeyId, 1, row++);

        // Section: OBS
        grid.add(new Separator(), 0, row++, 2, 1);
        Label obsHeader = new Label("OBS Studio Integration");
        obsHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        grid.add(obsHeader, 0, row++, 2, 1);
        
        grid.add(new Label("OBS Executable Path:"), 0, row);
        grid.add(obsBox, 1, row++);

        // Section: Cloud Storage
        grid.add(new Separator(), 0, row++, 2, 1);
        Label r2Header = new Label("Cloud Storage (S3 API)");
        r2Header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        grid.add(r2Header, 0, row++, 2, 1);

        grid.add(new Label("Provider:"), 0, row);
        grid.add(providerCombo, 1, row++);
        grid.add(new Label("Endpoint URL:"), 0, row);
        grid.add(endpointUrl, 1, row++);
        grid.add(new Label("Account ID (R2):"), 0, row);
        grid.add(r2AccountId, 1, row++);
        grid.add(new Label("Access Key ID:"), 0, row);
        grid.add(r2AccessKey, 1, row++);
        grid.add(new Label("Secret Access Key:"), 0, row);
        grid.add(r2SecretKey.getNode(), 1, row++);
        grid.add(new Label("Bucket Name:"), 0, row);
        grid.add(r2BucketName, 1, row++);
        grid.add(new Label("Public/Custom URL:"), 0, row);
        grid.add(r2PublicUrl, 1, row++);
        
        providerCombo.setOnAction(e -> {
            if ("Cloudflare R2".equals(providerCombo.getValue())) {
                endpointUrl.setPromptText("Leave empty to auto-generate from Account ID");
                r2AccountId.setDisable(false);
            } else {
                endpointUrl.setPromptText("Leave empty for standard AWS S3");
                r2AccountId.setDisable(true);
            }
        });

        // Actions
        Button saveBtn = new Button("Save Settings");
        Button saveRestartBtn = new Button("Save & Restart");
        Button cancelBtn = new Button("Cancel");
        HBox actions = new HBox(10, saveBtn, saveRestartBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);
        grid.add(actions, 1, row);

        saveBtn.setOnAction(e -> {
            ConfigService.setProductType(productCombo.getValue());
            ConfigService.setLmsBackendUrl(lmsUrl.getText().trim());
            ConfigService.setSupabaseUrl(supabaseUrl.getText().trim());
            ConfigService.setSupabaseAnonKey(supabaseAnonKey.getText().trim());
            ConfigService.setMuxTokenId(tokenId.getText().trim());
            ConfigService.setMuxTokenSecret(tokenSecret.getText());
            ConfigService.setMuxSigningSecret(signingSecret.getText());
            ConfigService.setMuxSigningKeyId(signingKeyId.getText().trim());
            ConfigService.setObsPath(obsPathField.getText().trim());
            
            // Save Cloud Storage
            ConfigService.setCloudProvider(providerCombo.getValue().equals("AWS S3") ? "S3" : "R2");
            ConfigService.setCloudEndpointUrl(endpointUrl.getText().trim());
            ConfigService.setR2AccountId(r2AccountId.getText().trim());
            ConfigService.setR2AccessKey(r2AccessKey.getText().trim());
            ConfigService.setR2SecretKey(r2SecretKey.getText());
            ConfigService.setR2BucketName(r2BucketName.getText().trim());
            ConfigService.setR2PublicUrl(r2PublicUrl.getText().trim());
            
            new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully.").showAndWait();
            stage.close();
        });

        saveRestartBtn.setOnAction(e -> {
            ConfigService.setProductType(productCombo.getValue());
            ConfigService.setLmsBackendUrl(lmsUrl.getText().trim());
            ConfigService.setSupabaseUrl(supabaseUrl.getText().trim());
            ConfigService.setSupabaseAnonKey(supabaseAnonKey.getText().trim());
            ConfigService.setMuxTokenId(tokenId.getText().trim());
            ConfigService.setMuxTokenSecret(tokenSecret.getText());
            ConfigService.setMuxSigningSecret(signingSecret.getText());
            ConfigService.setMuxSigningKeyId(signingKeyId.getText().trim());
            ConfigService.setObsPath(obsPathField.getText().trim());
            
            // Save Cloud Storage
            ConfigService.setCloudProvider(providerCombo.getValue().equals("AWS S3") ? "S3" : "R2");
            ConfigService.setCloudEndpointUrl(endpointUrl.getText().trim());
            ConfigService.setR2AccountId(r2AccountId.getText().trim());
            ConfigService.setR2AccessKey(r2AccessKey.getText().trim());
            ConfigService.setR2SecretKey(r2SecretKey.getText());
            ConfigService.setR2BucketName(r2BucketName.getText().trim());
            ConfigService.setR2PublicUrl(r2PublicUrl.getText().trim());
            
            new Alert(Alert.AlertType.INFORMATION, "Settings saved! The application will now close to apply the new configuration. Please start it again.").showAndWait();
            System.exit(0);
        });

        cancelBtn.setOnAction(e -> stage.close());
        
        scroll.setContent(grid);
        
        VBox box = new VBox(scroll);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private VBox buildTeacherTab() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);

        // --- Create Teacher Section ---
        VBox createBox = new VBox(10);
        createBox.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-padding: 15;");
        Label createTitle = new Label("Create New Teacher");
        createTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        TextField createEmail = new TextField();
        createEmail.setPromptText("Teacher Email");
        PasswordField createPass = new PasswordField();
        createPass.setPromptText("Password");
        Button createBtn = new Button("Create Account");
        
        createBtn.setOnAction(e -> {
            if (auth == null) {
                new Alert(Alert.AlertType.ERROR, "Database connection not available").showAndWait();
                return;
            }
            try {
                String email = createEmail.getText().trim();
                char[] pass = createPass.getText().toCharArray();
                if (auth.signupTeacher(email, pass)) {
                    new Alert(Alert.AlertType.INFORMATION, "Teacher account created successfully.").showAndWait();
                    createEmail.clear();
                    createPass.clear();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Failed to create account. Email may already exist.").showAndWait();
                }
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
            }
        });
        
        GridPane createGrid = new GridPane();
        createGrid.setHgap(10);
        createGrid.setVgap(10);
        createGrid.add(new Label("Email:"), 0, 0);
        createGrid.add(createEmail, 1, 0);
        createGrid.add(new Label("Password:"), 0, 1);
        createGrid.add(createPass, 1, 1);
        createGrid.add(createBtn, 1, 2);
        
        createBox.getChildren().addAll(createTitle, createGrid);

        // --- Reset Password Section ---
        VBox resetBox = new VBox(10);
        resetBox.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-padding: 15;");
        Label resetTitle = new Label("Reset Teacher Password");
        resetTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        TextField resetEmail = new TextField();
        resetEmail.setPromptText("Teacher Email");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("New Password");
        Button resetBtn = new Button("Reset Password");
        
        resetBtn.setOnAction(e -> {
            if (auth == null) {
                new Alert(Alert.AlertType.ERROR, "Database connection not available").showAndWait();
                return;
            }
            try {
                String email = resetEmail.getText().trim();
                char[] pass = newPass.getText().toCharArray();
                if (auth.resetTeacherPassword(email, pass)) {
                    new Alert(Alert.AlertType.INFORMATION, "Password reset successfully.").showAndWait();
                    resetEmail.clear();
                    newPass.clear();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Failed to reset password. User may not exist.").showAndWait();
                }
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
            }
        });
        
        GridPane resetGrid = new GridPane();
        resetGrid.setHgap(10);
        resetGrid.setVgap(10);
        resetGrid.add(new Label("Email:"), 0, 0);
        resetGrid.add(resetEmail, 1, 0);
        resetGrid.add(new Label("New Password:"), 0, 1);
        resetGrid.add(newPass, 1, 1);
        resetGrid.add(resetBtn, 1, 2);
        
        resetBox.getChildren().addAll(resetTitle, resetGrid);

        root.getChildren().addAll(createBox, resetBox);
        return root;
    }

    /**
     * Helper class to switch between PasswordField and TextField
     */
    private static class SecretField {
        private final PasswordField pf = new PasswordField();
        private final TextField tf = new TextField();
        private final CheckBox toggle = new CheckBox("Show");
        private final HBox container;

        public SecretField(String initialValue) {
            pf.setText(initialValue);
            tf.setText(initialValue);
            
            // Sync changes
            pf.textProperty().addListener((obs, old, val) -> {
                if (!tf.getText().equals(val)) tf.setText(val);
            });
            tf.textProperty().addListener((obs, old, val) -> {
                if (!pf.getText().equals(val)) pf.setText(val);
            });

            // Visibility toggle
            tf.setVisible(false);
            tf.setManaged(false);
            
            toggle.selectedProperty().addListener((obs, old, val) -> {
                if (val) {
                    tf.setVisible(true);
                    tf.setManaged(true);
                    pf.setVisible(false);
                    pf.setManaged(false);
                } else {
                    tf.setVisible(false);
                    tf.setManaged(false);
                    pf.setVisible(true);
                    pf.setManaged(true);
                }
            });

            StackPane stack = new StackPane(pf, tf);
            container = new HBox(5, stack, toggle);
            container.setAlignment(Pos.CENTER_LEFT);
            
            // Layout fix: ensure stack takes available width
            HBox.setHgrow(stack, javafx.scene.layout.Priority.ALWAYS);
            pf.prefWidthProperty().bind(stack.widthProperty());
            tf.prefWidthProperty().bind(stack.widthProperty());
        }

        public javafx.scene.Node getNode() {
            return container;
        }

        public String getText() {
            return pf.getText();
        }
    }
}
