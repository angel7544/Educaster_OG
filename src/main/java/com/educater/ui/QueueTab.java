package com.educater.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class QueueTab extends VBox {
    
    private static final ObservableList<UploadTask> activeTasks = FXCollections.observableArrayList();
    private ListView<UploadTask> listView;

    public QueueTab() {
        this.setPadding(new Insets(15));
        this.setSpacing(10);
        
        Label title = new Label("Background Tasks Queue");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        listView = new ListView<>(activeTasks);
        listView.setCellFactory(param -> new TaskCell());
        VBox.setVgrow(listView, Priority.ALWAYS);
        
        this.getChildren().addAll(title, listView);
    }
    
    public static void addUploadTask(UploadTask task) {
        Platform.runLater(() -> activeTasks.add(task));
    }
    
    public static UploadTask getTask(String id) {
        for (UploadTask t : activeTasks) {
            if (t.getId().equals(id)) return t;
        }
        return null;
    }
    
    public static void removeTask(String id) {
        Platform.runLater(() -> activeTasks.removeIf(t -> t.getId().equals(id)));
    }
    
    public static void updateTaskProgress(String id, double progress, String statusText) {
        Platform.runLater(() -> {
            UploadTask task = getTask(id);
            if (task != null && !task.isCompleted()) {
                task.setProgress(progress);
                task.setStatusText(statusText);
            }
        });
    }

    public static void updateTaskProgress(String regex, String statusText) {
        // legacy method for strings, try to map to new UploadTask
        Platform.runLater(() -> {
            for (UploadTask t : activeTasks) {
                if (t.getId().matches(regex) || (t.getId() + ".*").matches(regex)) {
                    t.setStatusText(statusText);
                    // extract percentage from statusText like [45.5%]
                    try {
                        int start = statusText.indexOf('[');
                        int end = statusText.indexOf('%');
                        if (start >= 0 && end > start) {
                            double pct = Double.parseDouble(statusText.substring(start + 1, end));
                            t.setProgress(pct / 100.0);
                        }
                    } catch (Exception ignored) {}
                    break;
                }
            }
        });
    }
    
    // For legacy compat, replace standard add with a simple task
    public static void addTask(String taskDesc) {
        Platform.runLater(() -> {
            UploadTask t = new UploadTask(taskDesc.replaceAll(" \\[.*\\]", ""));
            t.setStatusText(taskDesc);
            activeTasks.add(t);
        });
    }

    private static class TaskCell extends ListCell<UploadTask> {
        private final HBox root;
        private final Label nameLabel;
        private final Label statusLabel;
        private final ProgressBar progressBar;
        private final Button pauseBtn;
        private final Button cancelBtn;
        private final Button retryBtn;

        public TaskCell() {
            super();
            nameLabel = new Label();
            nameLabel.setStyle("-fx-font-weight: bold;");
            
            statusLabel = new Label();
            statusLabel.setStyle("-fx-text-fill: #555;");
            
            progressBar = new ProgressBar(0);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(progressBar, Priority.ALWAYS);
            
            pauseBtn = new Button("Pause");
            cancelBtn = new Button("Cancel");
            retryBtn = new Button("Retry");
            retryBtn.setVisible(false);
            retryBtn.setManaged(false);
            
            VBox infoBox = new VBox(5, nameLabel, statusLabel, progressBar);
            HBox.setHgrow(infoBox, Priority.ALWAYS);
            
            root = new HBox(15, infoBox, pauseBtn, cancelBtn, retryBtn);
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(10));
        }

        @Override
        protected void updateItem(UploadTask task, boolean empty) {
            super.updateItem(task, empty);
            if (empty || task == null) {
                setGraphic(null);
            } else {
                nameLabel.setText(task.getId());
                statusLabel.textProperty().bind(task.statusTextProperty());
                progressBar.progressProperty().bind(task.progressProperty());
                
                pauseBtn.setText(task.isPaused() ? "Resume" : "Pause");
                pauseBtn.setDisable(task.isCompleted());
                cancelBtn.setDisable(task.isCompleted());
                
                if (task.isFailed()) {
                    retryBtn.setVisible(true);
                    retryBtn.setManaged(true);
                    pauseBtn.setVisible(false);
                    pauseBtn.setManaged(false);
                } else {
                    retryBtn.setVisible(false);
                    retryBtn.setManaged(false);
                    pauseBtn.setVisible(true);
                    pauseBtn.setManaged(true);
                }
                
                pauseBtn.setOnAction(e -> {
                    if (task.isPaused()) {
                        task.resume();
                        pauseBtn.setText("Pause");
                    } else {
                        task.pause();
                        pauseBtn.setText("Resume");
                    }
                });
                
                cancelBtn.setOnAction(e -> {
                    task.cancel();
                    pauseBtn.setDisable(true);
                    cancelBtn.setDisable(true);
                    retryBtn.setVisible(false);
                    retryBtn.setManaged(false);
                });
                
                retryBtn.setOnAction(e -> {
                    task.retry();
                    retryBtn.setVisible(false);
                    retryBtn.setManaged(false);
                    pauseBtn.setVisible(true);
                    pauseBtn.setManaged(true);
                    pauseBtn.setDisable(false);
                    cancelBtn.setDisable(false);
                    pauseBtn.setText("Pause");
                });
                
                setGraphic(root);
            }
        }
    }
}
