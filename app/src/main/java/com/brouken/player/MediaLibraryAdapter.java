package com.brouken.player;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MediaLibraryAdapter extends RecyclerView.Adapter<MediaLibraryAdapter.VideoViewHolder> {

    private final Context context;
    private List<VideoItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(VideoItem item);
        void onItemLongClick(VideoItem item);
    }

    public MediaLibraryAdapter(Context context) {
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<VideoItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public List<VideoItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoItem item = items.get(position);

        holder.title.setText(item.title);
        holder.info.setText(buildInfo(item));

        // Duration badge on thumbnail
        String duration = item.getFormattedDuration();
        if (!"--:--".equals(duration)) {
            holder.durationBadge.setText(duration);
            holder.durationBadge.setVisibility(View.VISIBLE);
        } else {
            holder.durationBadge.setVisibility(View.GONE);
        }

        // Load thumbnail: content URI for MediaStore, File for hidden files
        Object thumbnailSource;
        if (item.id > 0) {
            thumbnailSource = item.getThumbnailUri();
        } else {
            thumbnailSource = new File(item.path);
        }

        Glide.with(context)
                .load(thumbnailSource)
                .transform(new CenterCrop(), new RoundedCorners(dpToPx(8)))
                .placeholder(R.drawable.ic_video_placeholder)
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onItemLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String buildInfo(VideoItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getFormattedDuration());

        String res = item.getResolution();
        if (res != null) {
            sb.append(" · ").append(res);
        }

        String size = item.getFormattedSize();
        if (size != null) {
            sb.append(" · ").append(size);
        }

        return sb.toString();
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title;
        TextView info;
        TextView durationBadge;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            title = itemView.findViewById(R.id.video_title);
            info = itemView.findViewById(R.id.video_info);
            durationBadge = itemView.findViewById(R.id.video_duration_badge);
        }
    }
}
