package com.brouken.player;

import android.net.Uri;

public class VideoItem {
    public long id;
    public String title;
    public String path;
    public long duration;   // ms
    public long size;       // bytes
    public long dateAdded;  // epoch seconds
    public int width;
    public int height;
    public String mimeType;
    public String bucketName;

    public Uri getUri() {
        return Uri.parse("content://media/external/video/media/" + id);
    }

    public Uri getThumbnailUri() {
        return Uri.parse("content://media/external/video/media/" + id);
    }

    public String getFormattedDuration() {
        if (duration <= 0) return "--:--";
        long totalSec = duration / 1000;
        long hours = totalSec / 3600;
        long mins = (totalSec % 3600) / 60;
        long secs = totalSec % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, mins, secs);
        }
        return String.format("%02d:%02d", mins, secs);
    }

    public String getFormattedSize() {
        if (size <= 0) return "--";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public String getResolution() {
        if (width > 0 && height > 0) {
            return width + "×" + height;
        }
        return null;
    }
}
