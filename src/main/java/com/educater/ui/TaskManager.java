package com.educater.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskManager {

    // Single thread executor to process one heavy video task at a time, or fixed thread pool.
    // For heavy CPU tasks like FFmpeg, 1 or 2 threads max is recommended.
    private static final ExecutorService executor = Executors.newFixedThreadPool(1);
    
    // For lightweight tasks like metadata syncing
    private static final ExecutorService ioExecutor = Executors.newCachedThreadPool();

    public static void submitHeavyTask(Runnable task) {
        executor.submit(task);
    }

    public static void submitIoTask(Runnable task) {
        ioExecutor.submit(task);
    }

    public static void checkHighCpuWarning(int numTasks, Runnable onProceed, Runnable onCancel) {
        if (numTasks > 1) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("High CPU Warning");
                alert.setHeaderText("Batch Processing Detected");
                alert.setContentText("Warning: High CPU Usage expected. Minimum requirement: 4-Core CPU / 8GB RAM. Do you want to continue?");
                
                ButtonType yesBtn = new ButtonType("Yes");
                ButtonType noBtn = new ButtonType("No");
                alert.getButtonTypes().setAll(yesBtn, noBtn);
                
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == yesBtn) {
                    if (onProceed != null) onProceed.run();
                } else {
                    if (onCancel != null) onCancel.run();
                }
            });
        } else {
            if (onProceed != null) onProceed.run();
        }
    }

    public static void shutdown() {
        executor.shutdownNow();
        ioExecutor.shutdownNow();
    }
}
