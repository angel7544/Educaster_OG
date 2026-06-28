package com.educater.video;

import javafx.application.Platform;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Map;
import java.util.Collections;

public class VideoProcessor {
    private static final List<Process> activeProcesses = Collections.synchronizedList(new ArrayList<>());

    public static void cancel() {
        synchronized(activeProcesses) {
            for (Process p : activeProcesses) {
                if (p != null && p.isAlive()) {
                    p.destroy();
                }
            }
            activeProcesses.clear();
        }
    }

    public interface EncodingProgressListener {
        void onProgress(String quality, double percentage);
        void onComplete(String quality, boolean success, String message, Path outputPath);
    }

    public static void encodeVideo(Path inputFile, Path outputDir, VideoEncodingOptions options, EncodingProgressListener listener) {
        new Thread(() -> {
            try {
                String ffmpegPath = FFmpegManager.getFFmpegPath();
                if (!FFmpegManager.isFFmpegInstalled()) {
                    Platform.runLater(() -> listener.onComplete("All", false, "FFmpeg is not installed", null));
                    return;
                }

                int threads = options.parallel ? Math.max(1, options.selectedQualities.size() + (options.generateMp4 ? 1 : 0)) : 1;
                ExecutorService executor = Executors.newFixedThreadPool(threads);
                
                boolean[] hasError = {false};

                for (Map.Entry<String, String> entry : options.selectedQualities.entrySet()) {
                    String qualityName = entry.getKey();
                    String bitrate = entry.getValue();
                    int height = VideoEncodingOptions.getHeightForQuality(qualityName);
                    
                    executor.submit(() -> {
                        Process process = null;
                        try {
                            Path targetDir = outputDir;
                            if (!options.singleFolder) {
                                targetDir = outputDir.resolve(qualityName);
                                if (!Files.exists(targetDir)) Files.createDirectories(targetDir);
                            }
                            
                            String m3u8Name = options.singleFolder ? qualityName + ".m3u8" : "index.m3u8";
                            String tsName = options.singleFolder ? qualityName + "_%03d.ts" : "index_%03d.ts";
                            Path m3u8Path = targetDir.resolve(m3u8Name);
                            Path tsPathPattern = targetDir.resolve(tsName);

                            List<String> command = new ArrayList<>();
                            command.add(ffmpegPath);
                            command.add("-y");
                            command.add("-i");
                            command.add(inputFile.toAbsolutePath().toString());
                            
                            if (options.keepAllAudio) {
                                command.add("-map");
                                command.add("0:v:0");
                                command.add("-map");
                                command.add("0:a?");
                            }
                            
                            command.add("-vf");
                            command.add("scale=-2:" + height);
                            
                            command.add("-c:v");
                            String vCodec = options.codec.equals("h265") ? "libx265" : "libx264";
                            if (options.useNvenc) {
                                vCodec = options.codec.equals("h265") ? "hevc_nvenc" : "h264_nvenc";
                            }
                            command.add(vCodec);
                            
                            if (options.useCrf) {
                                command.add("-crf");
                                command.add(String.valueOf(options.crfValue));
                            } else {
                                command.add("-b:v");
                                command.add(bitrate);
                            }
                            
                            command.add("-preset");
                            command.add("fast");
                            command.add("-c:a");
                            command.add("aac");
                            command.add("-b:a");
                            command.add("128k");
                            
                            // HLS specifics
                            command.add("-f");
                            command.add("hls");
                            command.add("-hls_time");
                            command.add(String.valueOf(options.segmentTime));
                            if (!options.liveMode) {
                                command.add("-hls_playlist_type");
                                command.add("vod");
                            } else {
                                command.add("-hls_list_size");
                                command.add("5"); // Keep only last 5 segments for live
                                command.add("-hls_flags");
                                command.add("delete_segments");
                            }
                            command.add("-hls_segment_filename");
                            command.add(tsPathPattern.toAbsolutePath().toString());
                            command.add(m3u8Path.toAbsolutePath().toString());

                            ProcessBuilder pb = new ProcessBuilder(command);
                            pb.redirectErrorStream(true);
                            process = pb.start();
                            activeProcesses.add(process);

                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    // Parse progress if needed
                                }
                            }

                            int exitCode = process.waitFor();
                            if (exitCode == 0) {
                                listener.onComplete(qualityName, true, "Encoding successful", m3u8Path);
                            } else {
                                hasError[0] = true;
                                listener.onComplete(qualityName, false, "FFmpeg exited with code " + exitCode, null);
                            }
                        } catch (Exception e) {
                            hasError[0] = true;
                            listener.onComplete(qualityName, false, "Error: " + e.getMessage(), null);
                        } finally {
                            if (process != null) {
                                activeProcesses.remove(process);
                            }
                        }
                    });
                }
                
                if (options.generateMp4) {
                    executor.submit(() -> {
                        Process process = null;
                        try {
                            Path mp4Path = outputDir.resolve("output.mp4");
                            List<String> cmd = new ArrayList<>();
                            cmd.add(ffmpegPath);
                            cmd.add("-y");
                            cmd.add("-i");
                            cmd.add(inputFile.toAbsolutePath().toString());
                            if (options.keepAllAudio) {
                                cmd.add("-map"); cmd.add("0:v:0");
                                cmd.add("-map"); cmd.add("0:a?");
                            }
                            cmd.add("-c:v");
                            String vCodec = options.codec.equals("h265") ? "libx265" : "libx264";
                            if (options.useNvenc) {
                                vCodec = options.codec.equals("h265") ? "hevc_nvenc" : "h264_nvenc";
                            }
                            cmd.add(vCodec);
                            if (options.useCrf) {
                                cmd.add("-crf"); cmd.add(String.valueOf(options.crfValue));
                            } else {
                                cmd.add("-b:v"); cmd.add("5000k");
                            }
                            cmd.add("-preset"); cmd.add("fast");
                            cmd.add("-c:a"); cmd.add("aac");
                            cmd.add("-b:a"); cmd.add("192k");
                            cmd.add(mp4Path.toAbsolutePath().toString());
                            
                            ProcessBuilder pb = new ProcessBuilder(cmd);
                            pb.redirectErrorStream(true);
                            process = pb.start();
                            activeProcesses.add(process);
                            
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {}
                            }
                            int exitCode = process.waitFor();
                            if (exitCode == 0) {
                                listener.onComplete("MP4", true, "MP4 Encoding successful", mp4Path);
                            } else {
                                hasError[0] = true;
                                listener.onComplete("MP4", false, "MP4 FFmpeg exited with code " + exitCode, null);
                            }
                        } catch (Exception e) {
                            hasError[0] = true;
                            listener.onComplete("MP4", false, "Error: " + e.getMessage(), null);
                        } finally {
                            if (process != null) {
                                activeProcesses.remove(process);
                            }
                        }
                    });
                }
                
                executor.shutdown();
                executor.awaitTermination(24, TimeUnit.HOURS);
                
                if (!hasError[0] && !options.selectedQualities.isEmpty()) {
                    generateMasterPlaylist(outputDir, options);
                }
                
                // Final completion callback
                Platform.runLater(() -> listener.onComplete("All", !hasError[0], hasError[0] ? "Some encodes failed" : "All encodes finished", null));

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> listener.onComplete("All", false, "Error: " + e.getMessage(), null));
            }
        }).start();
    }

    private static void generateMasterPlaylist(Path outputDir, VideoEncodingOptions options) {
        try {
            Path masterPath = outputDir.resolve("master.m3u8");
            StringBuilder sb = new StringBuilder();
            sb.append("#EXTM3U\n");
            sb.append("#EXT-X-VERSION:3\n");
            
            for (String quality : options.selectedQualities.keySet()) {
                int bw = 800000;
                String res = "1280x720";
                switch (quality) {
                    case "1080p": bw = 5000000; res = "1920x1080"; break;
                    case "720p": bw = 2800000; res = "1280x720"; break;
                    case "480p": bw = 1400000; res = "854x480"; break;
                    case "360p": bw = 800000; res = "640x360"; break;
                    case "240p": bw = 400000; res = "426x240"; break;
                }
                sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bw).append(",RESOLUTION=").append(res).append("\n");
                if (options.singleFolder) {
                    sb.append(quality).append(".m3u8\n");
                } else {
                    sb.append(quality).append("/index.m3u8\n");
                }
            }
            Files.writeString(masterPath, sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
