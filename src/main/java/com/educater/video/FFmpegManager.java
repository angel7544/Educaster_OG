package com.educater.video;

import javafx.application.Platform;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FFmpegManager {

    private static final String FFMPEG_URL_WIN = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
    private static final String FFMPEG_DIR = "ffmpeg-bin";
    private static final String FFMPEG_EXE = FFMPEG_DIR + "/ffmpeg.exe";

    public interface DownloadProgressListener {
        void onProgress(double percentage);
        void onComplete(boolean success, String message);
    }

    public static boolean isFFmpegInstalled() {
        return Files.exists(Paths.get(FFMPEG_EXE));
    }

    public static String getFFmpegPath() {
        return Paths.get(FFMPEG_EXE).toAbsolutePath().toString();
    }

    public static void downloadAndInstall(DownloadProgressListener listener) {
        new Thread(() -> {
            try {
                Path zipPath = Paths.get("ffmpeg.zip");

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(FFMPEG_URL_WIN))
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                
                long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                
                try (InputStream in = response.body();
                     FileOutputStream out = new FileOutputStream(zipPath.toFile())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        
                        if (listener != null) {
                            double prog = totalBytes > 0 ? (double) totalRead / totalBytes : -1;
                            // cap at 90% for download phase
                            double finalProg = prog > 0 ? prog * 0.9 : 0.5;
                            Platform.runLater(() -> listener.onProgress(finalProg));
                        }
                    }
                }

                // Extract ZIP
                Path extractDir = Paths.get(FFMPEG_DIR);
                Files.createDirectories(extractDir);

                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().endsWith("ffmpeg.exe")) {
                            Path targetFile = Paths.get(FFMPEG_EXE);
                            Files.copy(zis, targetFile, StandardCopyOption.REPLACE_EXISTING);
                            break;
                        }
                    }
                }

                Files.deleteIfExists(zipPath);

                if (listener != null) {
                    Platform.runLater(() -> {
                        listener.onProgress(1.0);
                        listener.onComplete(true, "FFmpeg installed successfully.");
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (listener != null) {
                    Platform.runLater(() -> listener.onComplete(false, "Failed to install FFmpeg: " + e.getMessage()));
                }
            }
        }).start();
    }
}
