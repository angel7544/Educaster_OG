package com.educater.video;

import java.util.Map;

public class VideoEncodingOptions {
    public boolean useNvenc = false;
    public boolean parallel = true;
    public boolean liveMode = false;
    public String codec = "h264";
    public boolean useCrf = false;
    public int crfValue = 28;
    public int segmentTime = 6;
    public boolean singleFolder = true;
    public boolean generateMp4 = false;
    public boolean keepAllAudio = true;
    
    // Map of quality name (e.g. "1080p") to Bitrate (e.g. "5000k")
    public Map<String, String> selectedQualities;
    
    // Hardcoded heights for the qualities to keep it simple, mirroring convert.py
    public static int getHeightForQuality(String quality) {
        return switch (quality) {
            case "1080p" -> 1080;
            case "720p" -> 720;
            case "480p" -> 480;
            case "360p" -> 360;
            case "240p" -> 240;
            default -> 720;
        };
    }
}
