package com.brouken.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private final Context context;
    private List<FolderItem> items = new ArrayList<>();
    private OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(FolderItem folder);
    }

    public FolderAdapter(Context context) {
        this.context = context;
    }

    public void setOnFolderClickListener(OnFolderClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<FolderItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        FolderItem item = items.get(position);

        holder.name.setText(item.name);
        holder.count.setText(item.getCountText(context));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFolderClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        TextView count;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.folder_icon);
            name = itemView.findViewById(R.id.folder_name);
            count = itemView.findViewById(R.id.folder_count);
        }
    }
}
