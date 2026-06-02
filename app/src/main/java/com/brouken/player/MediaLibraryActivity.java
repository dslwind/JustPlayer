package com.brouken.player;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MediaLibraryActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;

    private RecyclerView recyclerView;
    private MediaLibraryAdapter adapter;
    private TextView emptyView;
    private Toolbar toolbar;
    private ChipGroup chipGroup;
    private View folderFilterBar;

    private List<VideoItem> allVideos = new ArrayList<>();
    private List<VideoItem> filteredVideos = new ArrayList<>();
    private Map<String, List<VideoItem>> folderMap = new LinkedHashMap<>();

    private String currentFolder = null;
    private String currentQuery = "";
    private SortOrder sortOrder = SortOrder.DATE_DESC;
    private boolean loaded = false;

    enum SortOrder {
        DATE_DESC("Date (newest)", MediaStore.MediaColumns.DATE_ADDED + " DESC"),
        DATE_ASC("Date (oldest)", MediaStore.MediaColumns.DATE_ADDED + " ASC"),
        NAME_ASC("Name (A-Z)", MediaStore.MediaColumns.DISPLAY_NAME + " ASC"),
        NAME_DESC("Name (Z-A)", MediaStore.MediaColumns.DISPLAY_NAME + " DESC"),
        SIZE_DESC("Size (largest)", MediaStore.MediaColumns.SIZE + " DESC"),
        SIZE_ASC("Size (smallest)", MediaStore.MediaColumns.SIZE + " ASC"),
        DURATION_DESC("Duration (longest)", MediaStore.MediaColumns.DURATION + " DESC"),
        DURATION_ASC("Duration (shortest)", MediaStore.MediaColumns.DURATION + " ASC");

        final String label;
        final String sortOrder;

        SortOrder(String label, String sortOrder) {
            this.label = label;
            this.sortOrder = sortOrder;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_library);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        recyclerView = findViewById(R.id.recycler_videos);
        emptyView = findViewById(R.id.empty_view);
        chipGroup = findViewById(R.id.chip_group);
        folderFilterBar = findViewById(R.id.folder_filter_bar);

        adapter = new MediaLibraryAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(new MediaLibraryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(VideoItem item) {
                playVideo(item);
            }

            @Override
            public void onItemLongClick(VideoItem item) {
                showVideoInfo(item);
            }
        });

        checkPermissionAndLoad();
    }

    private void checkPermissionAndLoad() {
        String permission;
        if (Build.VERSION.SDK_INT >= 33) {
            permission = Manifest.permission.READ_MEDIA_VIDEO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVideos();
            } else {
                emptyView.setText("Permission denied. Cannot show media library.");
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadVideos() {
        allVideos.clear();
        folderMap.clear();

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        };

        String selection = MediaStore.MediaColumns.DURATION + " > 0";

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, sortOrder.sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                int pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
                int widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH);
                int heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT);
                int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
                int bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    VideoItem item = new VideoItem();
                    item.id = cursor.getLong(idCol);
                    item.title = cursor.getString(nameCol);
                    item.path = cursor.getString(pathCol);
                    item.duration = cursor.getLong(durationCol);
                    item.size = cursor.getLong(sizeCol);
                    item.dateAdded = cursor.getLong(dateCol);
                    item.width = cursor.getInt(widthCol);
                    item.height = cursor.getInt(heightCol);
                    item.mimeType = cursor.getString(mimeCol);
                    item.bucketName = cursor.getString(bucketCol);

                    if (TextUtils.isEmpty(item.bucketName)) {
                        item.bucketName = "Unknown";
                    }

                    allVideos.add(item);

                    List<VideoItem> folderList = folderMap.get(item.bucketName);
                    if (folderList == null) {
                        folderList = new ArrayList<>();
                        folderMap.put(item.bucketName, folderList);
                    }
                    folderList.add(item);
                }
            }
        }

        loaded = true;

        // Show folder chips by default
        setupFolderChips();
        folderFilterBar.setVisibility(View.VISIBLE);

        applyFilter();
    }

    private void applyFilter() {
        filteredVideos.clear();

        List<VideoItem> source;
        if (currentFolder != null) {
            source = folderMap.get(currentFolder);
            if (source == null) source = new ArrayList<>();
        } else {
            source = allVideos;
        }

        if (TextUtils.isEmpty(currentQuery)) {
            filteredVideos.addAll(source);
        } else {
            String query = currentQuery.toLowerCase();
            for (VideoItem item : source) {
                if (item.title != null && item.title.toLowerCase().contains(query)) {
                    filteredVideos.add(item);
                }
            }
        }

        adapter.setItems(filteredVideos);

        if (filteredVideos.isEmpty()) {
            emptyView.setText(TextUtils.isEmpty(currentQuery) ?
                    "No videos found" : "No matching videos");
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        updateSubtitle();
    }

    private void updateSubtitle() {
        String text;
        if (currentFolder != null) {
            text = currentFolder + " · " + filteredVideos.size() + " videos";
        } else {
            text = filteredVideos.size() + " videos";
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(text);
        }
    }

    private void playVideo(VideoItem item) {
        Uri videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id);

        // Skip the first-run onboarding overlay in PlayerActivity
        Prefs prefs = new Prefs(this);
        prefs.markFirstRun();

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(videoUri, item.mimeType != null ? item.mimeType : "video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void showVideoInfo(VideoItem item) {
        StringBuilder info = new StringBuilder();
        info.append("Title: ").append(item.title).append("\n");
        info.append("Duration: ").append(item.getFormattedDuration()).append("\n");
        info.append("Size: ").append(item.getFormattedSize()).append("\n");
        String res = item.getResolution();
        if (res != null) {
            info.append("Resolution: ").append(res).append("\n");
        }
        info.append("Folder: ").append(item.bucketName).append("\n");
        if (item.mimeType != null) {
            info.append("Type: ").append(item.mimeType).append("\n");
        }
        if (item.path != null) {
            info.append("Path: ").append(item.path);
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(info.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.media_library, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("Search videos...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText;
                applyFilter();
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.action_folders) {
            toggleFolderFilter();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        SortOrder[] orders = SortOrder.values();
        String[] labels = new String[orders.length];
        int checked = 0;
        for (int i = 0; i < orders.length; i++) {
            labels[i] = orders[i].label;
            if (orders[i] == sortOrder) checked = i;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Sort by")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    sortOrder = orders[which];
                    dialog.dismiss();
                    loadVideos();
                })
                .show();
    }

    private void toggleFolderFilter() {
        if (folderFilterBar.getVisibility() == View.VISIBLE) {
            folderFilterBar.setVisibility(View.GONE);
            currentFolder = null;
            applyFilter();
        } else {
            folderFilterBar.setVisibility(View.VISIBLE);
            setupFolderChips();
        }
    }

    private void setupFolderChips() {
        chipGroup.removeAllViews();

        Chip allChip = new Chip(this);
        allChip.setText("All");
        allChip.setCheckable(true);
        allChip.setChecked(currentFolder == null);
        allChip.setOnClickListener(v -> {
            currentFolder = null;
            updateChipSelection();
            applyFilter();
        });
        chipGroup.addView(allChip);

        List<String> folders = new ArrayList<>(folderMap.keySet());
        Collections.sort(folders, String.CASE_INSENSITIVE_ORDER);

        for (String folder : folders) {
            Chip chip = new Chip(this);
            chip.setText(folder + " (" + folderMap.get(folder).size() + ")");
            chip.setCheckable(true);
            chip.setChecked(folder.equals(currentFolder));
            chip.setOnClickListener(v -> {
                currentFolder = folder;
                updateChipSelection();
                applyFilter();
            });
            chipGroup.addView(chip);
        }
    }

    private void updateChipSelection() {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (i == 0) {
                    chip.setChecked(currentFolder == null);
                } else {
                    String folderName = extractFolderName(chip.getText().toString());
                    chip.setChecked(folderName.equals(currentFolder));
                }
            }
        }
    }

    private String extractFolderName(String chipText) {
        int idx = chipText.lastIndexOf(" (");
        if (idx > 0) {
            return chipText.substring(0, idx);
        }
        return chipText;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!loaded && ContextCompat.checkSelfPermission(this,
                Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_VIDEO :
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        }
    }
}
