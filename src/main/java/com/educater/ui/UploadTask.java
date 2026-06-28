package com.educater.ui;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class UploadTask {
    private String id;
    private StringProperty statusText;
    private DoubleProperty progress;
    private Runnable onPause;
    private Runnable onResume;
    private Runnable onCancel;
    private Runnable onRetry;
    
    private boolean isPaused = false;
    private boolean isCompleted = false;
    private boolean isFailed = false;

    public UploadTask(String id) {
        this.id = id;
        this.statusText = new SimpleStringProperty("Pending");
        this.progress = new SimpleDoubleProperty(0.0);
    }

    public String getId() { return id; }
    
    public StringProperty statusTextProperty() { return statusText; }
    public void setStatusText(String text) { this.statusText.set(text); }
    public String getStatusText() { return statusText.get(); }
    
    public DoubleProperty progressProperty() { return progress; }
    public void setProgress(double p) { this.progress.set(p); }
    public double getProgress() { return progress.get(); }
    
    public void setOnPause(Runnable onPause) { this.onPause = onPause; }
    public void setOnResume(Runnable onResume) { this.onResume = onResume; }
    public void setOnCancel(Runnable onCancel) { this.onCancel = onCancel; }
    public void setOnRetry(Runnable onRetry) { this.onRetry = onRetry; }
    
    public void pause() {
        if (!isCompleted && onPause != null) {
            isPaused = true;
            onPause.run();
        }
    }
    
    public void resume() {
        if (!isCompleted && onResume != null) {
            isPaused = false;
            onResume.run();
        }
    }
    
    public void cancel() {
        if (!isCompleted && onCancel != null) {
            isCompleted = true;
            onCancel.run();
        }
    }
    
    public void retry() {
        if (isFailed && onRetry != null) {
            isFailed = false;
            isCompleted = false;
            this.statusText.set("Retrying...");
            this.progress.set(0.0);
            onRetry.run();
        }
    }
    
    public boolean isPaused() { return isPaused; }
    public void setCompleted(boolean completed) { this.isCompleted = completed; }
    public boolean isCompleted() { return isCompleted; }
    public void setFailed(boolean failed) { this.isFailed = failed; }
    public boolean isFailed() { return isFailed; }
}
