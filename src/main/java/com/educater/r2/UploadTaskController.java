package com.educater.r2;

public interface UploadTaskController {
    boolean isCancelled();
    boolean isPaused();
    void waitIfPaused() throws InterruptedException;
}
