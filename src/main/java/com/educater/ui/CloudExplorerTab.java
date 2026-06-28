package com.educater.ui;

import com.educater.r2.R2Service;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Path;

import java.util.List;
import java.util.Optional;
import java.io.File;
import javafx.stage.FileChooser;
import software.amazon.awssdk.transfer.s3.model.FileUpload;

public class CloudExplorerTab extends VBox {

    private final R2Service r2Service;
    private final TableView<R2Service.R2File> table;
    private final TextField currentPathField;
    
    private final VBox detailsPane;
    private final Label detailNameLbl;
    private final Label detailSizeLbl;
    private final Label detailDateLbl;
    private final TextField detailUrlField;

    public CloudExplorerTab(R2Service r2Service) {
        this.r2Service = r2Service;
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        
        table = new TableView<>();
        
        // --- DETAILS PANE ---
        detailsPane = new VBox(15);
        detailsPane.setPadding(new Insets(15));
        detailsPane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");
        detailsPane.setPrefWidth(300);
        
        Label detTitle = new Label("File Details");
        detTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        
        detailNameLbl = new Label("Select a file...");
        detailNameLbl.setWrapText(true);
        detailSizeLbl = new Label("");
        detailDateLbl = new Label("");
        
        Label urlLbl = new Label("Public URL:");
        detailUrlField = new TextField();
        detailUrlField.setEditable(false);
        Button copyLinkBtn = new Button("Copy Link 📋");
        Button downloadBtn = new Button("Download ⬇️");
        downloadBtn.setDisable(true);
        
        copyLinkBtn.setOnAction(e -> {
            if (!detailUrlField.getText().isEmpty()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(detailUrlField.getText());
                Clipboard.getSystemClipboard().setContent(content);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Link copied to clipboard!");
                alert.show();
            }
        });
        
        downloadBtn.setOnAction(e -> {
            R2Service.R2File selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isFolder()) {
                FileChooser fc = new FileChooser();
                fc.setTitle("Save Download");
                fc.setInitialFileName(selected.getKey().substring(selected.getKey().lastIndexOf('/') + 1));
                File dest = fc.showSaveDialog(getScene().getWindow());
                if (dest != null) {
                    downloadBtn.setDisable(true);
                    downloadBtn.setText("Downloading...");
                    software.amazon.awssdk.transfer.s3.model.FileDownload download = r2Service.downloadFileAsync(selected.getKey(), dest.toPath(), (bytesTransferred, totalBytes, currentFile, speedMBps, timeRemainingSeconds) -> {
                        javafx.application.Platform.runLater(() -> {
                            if (totalBytes > 0) {
                                double pct = (double) bytesTransferred / totalBytes * 100.0;
                                downloadBtn.setText(String.format("Downloading... %.1f%%", pct));
                            }
                        });
                    });
                    
                    download.completionFuture().whenComplete((res, err) -> {
                        javafx.application.Platform.runLater(() -> {
                            downloadBtn.setText("Download ⬇️");
                            downloadBtn.setDisable(false);
                            if (err != null) {
                                new Alert(Alert.AlertType.ERROR, "Download failed: " + err.getMessage()).show();
                            } else {
                                new Alert(Alert.AlertType.INFORMATION, "Download successful!").show();
                            }
                        });
                    });
                }
            }
        });
        
        HBox btnBox = new HBox(10, copyLinkBtn, downloadBtn);
        
        detailsPane.getChildren().addAll(detTitle, new Separator(), detailNameLbl, detailSizeLbl, detailDateLbl, new Separator(), urlLbl, detailUrlField, btnBox);
        // -------------------

        HBox topBar = new HBox(10);
        Button backBtn = new Button("🔙 Back");
        Button refreshBtn = new Button("Refresh (🔄)");
        Button uploadBtn = new Button("Upload Here (☁️)");
        Button deleteBtn = new Button("Delete");
        Button moveBtn = new Button("Move");
        
        currentPathField = new TextField("");
        currentPathField.setPromptText("Enter prefix (e.g., folder/) or leave empty for root");
        currentPathField.setPrefWidth(250);

        topBar.getChildren().addAll(backBtn, new Label("Path:"), currentPathField, refreshBtn, uploadBtn, deleteBtn, moveBtn);

        TableColumn<R2Service.R2File, String> nameCol = new TableColumn<>("Name / Key");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getKey()));
        nameCol.setPrefWidth(300);

        TableColumn<R2Service.R2File, Number> sizeCol = new TableColumn<>("Size (Bytes)");
        sizeCol.setCellValueFactory(data -> new SimpleLongProperty(data.getValue().getSize()));
        sizeCol.setPrefWidth(120);

        TableColumn<R2Service.R2File, String> dateCol = new TableColumn<>("Last Modified");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLastModified()));
        dateCol.setPrefWidth(200);

        table.getColumns().addAll(nameCol, sizeCol, dateCol);
        
        VBox tableBox = new VBox(10, topBar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        
        SplitPane split = new SplitPane(tableBox, detailsPane);
        split.setDividerPositions(0.75);
        VBox.setVgrow(split, Priority.ALWAYS);
        
        this.getChildren().add(split);

        backBtn.setOnAction(e -> {
            String path = currentPathField.getText();
            if (path != null && !path.isEmpty()) {
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0) {
                    currentPathField.setText(path.substring(0, lastSlash + 1));
                } else {
                    currentPathField.setText("");
                }
                refreshData();
            }
        });

        refreshBtn.setOnAction(e -> refreshData());
        currentPathField.setOnAction(e -> refreshData());
        
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                detailNameLbl.setText("Name: " + newSel.getKey());
                detailSizeLbl.setText("Size: " + (newSel.isFolder() ? "-" : newSel.getSize() + " bytes"));
                detailDateLbl.setText("Modified: " + newSel.getLastModified());
                if (!newSel.isFolder()) {
                    detailUrlField.setText(r2Service.getPublicUrl(newSel.getKey()));
                    downloadBtn.setDisable(false);
                } else {
                    detailUrlField.setText("");
                    downloadBtn.setDisable(true);
                }
            } else {
                detailNameLbl.setText("Select a file...");
                detailSizeLbl.setText("");
                detailDateLbl.setText("");
                detailUrlField.setText("");
                downloadBtn.setDisable(true);
            }
        });
        
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                R2Service.R2File selected = table.getSelectionModel().getSelectedItem();
                if (selected != null && selected.isFolder()) {
                    currentPathField.setText(selected.getKey());
                    refreshData();
                }
            }
        });
        
        uploadBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select File to Upload");
            File file = fc.showOpenDialog(this.getScene().getWindow());
            if (file != null) {
                String prefix = currentPathField.getText().trim();
                String key = prefix;
                if (!key.endsWith("/") && !key.isEmpty()) key += "/";
                key += file.getName();
                
                String contentType = "application/octet-stream";
                try {
                    contentType = java.nio.file.Files.probeContentType(file.toPath());
                    if (contentType == null) contentType = "application/octet-stream";
                } catch (Exception ignored) {}
                
                String finalContentType = contentType;
                String finalKey = key;
                
                final String taskId = "File: " + file.getName();
                UploadTask task = new UploadTask(taskId);
                QueueTab.addUploadTask(task);
                
                final java.util.concurrent.atomic.AtomicReference<software.amazon.awssdk.transfer.s3.model.FileUpload> uploadRef = new java.util.concurrent.atomic.AtomicReference<>();
                final java.util.concurrent.atomic.AtomicReference<software.amazon.awssdk.transfer.s3.model.ResumableFileUpload> resumableRef = new java.util.concurrent.atomic.AtomicReference<>();
                
                task.setOnPause(() -> {
                    software.amazon.awssdk.transfer.s3.model.FileUpload currentUpload = uploadRef.get();
                    if (currentUpload != null) {
                        resumableRef.set(currentUpload.pause());
                    }
                });
                
                task.setOnResume(() -> {
                    software.amazon.awssdk.transfer.s3.model.ResumableFileUpload resumable = resumableRef.get();
                    if (resumable != null) {
                        uploadRef.set(r2Service.resumeUploadFile(resumable));
                        TaskManager.submitIoTask(() -> {
                            try {
                                uploadRef.get().completionFuture().get();
                                Platform.runLater(() -> {
                                    task.setCompleted(true);
                                    task.setStatusText("Completed");
                                    task.setProgress(1.0);
                                    refreshData();
                                });
                            } catch (Exception ex) {}
                        });
                    }
                });
                
                task.setOnCancel(() -> {
                    software.amazon.awssdk.transfer.s3.model.FileUpload currentUpload = uploadRef.get();
                    if (currentUpload != null) {
                        currentUpload.completionFuture().cancel(true);
                    }
                });
                
                TaskManager.submitIoTask(() -> {
                    try {
                        R2Service.R2ProgressListener listener = (bytesTransferred, totalBytes, currentFile, speedMBps, timeRemainingSeconds) -> {
                            double pct = totalBytes > 0 ? (double) bytesTransferred / totalBytes : 0;
                            String status = String.format("%.1f MB/s (%ds left)", speedMBps, timeRemainingSeconds);
                            QueueTab.updateTaskProgress(taskId, pct, status);
                        };
                        
                        software.amazon.awssdk.transfer.s3.model.FileUpload upload = r2Service.uploadFileAsync(finalKey, file.toPath(), finalContentType, listener);
                        uploadRef.set(upload);
                        upload.completionFuture().get();
                        
                        Platform.runLater(() -> {
                            if (!task.isCompleted()) { // might have been cancelled
                                task.setCompleted(true);
                                task.setStatusText("Completed");
                                task.setProgress(1.0);
                                refreshData();
                            }
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            if (!task.isCompleted()) {
                                task.setCompleted(true);
                                task.setStatusText("Failed or Cancelled");
                            }
                        });
                    }
                });
            }
        });

        deleteBtn.setOnAction(e -> {
            R2Service.R2File selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + selected.getKey() + "?");
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        TaskManager.submitIoTask(() -> {
                            r2Service.deleteFile(selected.getKey());
                            Platform.runLater(this::refreshData);
                        });
                    }
                });
            }
        });

        moveBtn.setOnAction(e -> {
            R2Service.R2File selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isFolder()) {
                TextInputDialog dialog = new TextInputDialog(selected.getKey());
                dialog.setTitle("Move File");
                dialog.setHeaderText("Move or Rename File");
                dialog.setContentText("New Key:");
                Optional<String> result = dialog.showAndWait();
                result.ifPresent(newKey -> {
                    TaskManager.submitIoTask(() -> {
                        r2Service.moveFile(selected.getKey(), newKey);
                        Platform.runLater(this::refreshData);
                    });
                });
            }
        });

        refreshData();
    }

    private void refreshData() {
        if (r2Service == null) return;
        TaskManager.submitIoTask(() -> {
            try {
                String prefix = currentPathField.getText();
                List<R2Service.R2File> files = r2Service.listFiles(prefix);
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList(files));
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }
}
