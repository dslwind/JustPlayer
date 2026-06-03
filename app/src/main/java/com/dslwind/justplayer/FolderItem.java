package com.dslwind.justplayer;

import android.content.Context;

import java.util.List;

public class FolderItem {
    public String name;
    public List<VideoItem> videos;

    public FolderItem(String name, List<VideoItem> videos) {
        this.name = name;
        this.videos = videos;
    }

    public int getCount() {
        return videos.size();
    }

    public long getTotalSize() {
        long total = 0;
        for (VideoItem v : videos) {
            total += v.size;
        }
        return total;
    }

    public String getCountText(Context context) {
        int count = getCount();
        long size = getTotalSize();
        String sizeText;
        if (size < 1024 * 1024 * 1024) {
            sizeText = String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            sizeText = String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
        return context.getString(R.string.media_library_folder_count, count, sizeText);
    }
}
