package com.brouken.player;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MediaLibraryActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_MANAGE_STORAGE = 101;

    private static final String[] VIDEO_EXTENSIONS = {
            ".3gp", ".avi", ".flv", ".m4v", ".mkv", ".mov", ".mp4", ".mpd",
            ".mpe", ".mpeg", ".mpg", ".mts", ".ts", ".webm", ".wmv"
    };

    private RecyclerView recyclerView;
    private TextView emptyView;
    private Toolbar toolbar;

    private MediaLibraryAdapter videoAdapter;
    private FolderAdapter folderAdapter;

    private List<VideoItem> allVideos = new ArrayList<>();
    private List<VideoItem> filteredVideos = new ArrayList<>();
    private Map<String, List<VideoItem>> folderMap = new LinkedHashMap<>();
    private List<FolderItem> folderList = new ArrayList<>();

    private String currentFolder = null; // null = show folder list
    private String currentQuery = "";
    private SortOrder sortOrder = SortOrder.DATE_DESC;
    private boolean loaded = false;
    private boolean showHidden = false;

    private MenuItem hiddenToggle;

    // View mode
    private static final int MODE_FOLDERS = 0;
    private static final int MODE_VIDEOS = 1;
    private int currentMode = MODE_FOLDERS;

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
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        // Tint overflow menu icon white for dark theme
        Drawable overflowIcon = toolbar.getOverflowIcon();
        if (overflowIcon != null) {
            overflowIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        }

        recyclerView = findViewById(R.id.recycler_videos);
        emptyView = findViewById(R.id.empty_view);

        // Handle back press: folder list → do nothing (let system handle), videos → go to folders
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentMode == MODE_VIDEOS) {
                    showFolderList();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Setup both adapters
        videoAdapter = new MediaLibraryAdapter(this);
        videoAdapter.setOnItemClickListener(new MediaLibraryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(VideoItem item) {
                playVideo(item);
            }

            @Override
            public void onItemLongClick(VideoItem item) {
                showVideoInfo(item);
            }
        });

        folderAdapter = new FolderAdapter(this);
        folderAdapter.setOnFolderClickListener(folder -> {
            openFolder(folder.name);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                showHidden = true;
                loadVideos();
            }
        }
    }

    private void loadVideos() {
        allVideos.clear();
        folderMap.clear();

        loadFromMediaStore();

        if (showHidden && canScanFileSystem()) {
            scanFileSystem();
        }

        loaded = true;

        buildFolderList();

        if (currentMode == MODE_FOLDERS) {
            showFolderList();
        } else {
            showVideoList();
        }

        if (hiddenToggle != null) {
            hiddenToggle.setTitle(showHidden ? "Hide hidden" : "Show hidden");
        }
    }

    private void buildFolderList() {
        folderList.clear();
        for (Map.Entry<String, List<VideoItem>> entry : folderMap.entrySet()) {
            folderList.add(new FolderItem(entry.getKey(), entry.getValue()));
        }
        Collections.sort(folderList, (a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    private void showFolderList() {
        currentMode = MODE_FOLDERS;
        currentFolder = null;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().setSubtitle(folderList.size() + " folders · " + allVideos.size() + " videos");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        // Apply search filter to folders
        List<FolderItem> displayFolders;
        if (TextUtils.isEmpty(currentQuery)) {
            displayFolders = folderList;
        } else {
            displayFolders = new ArrayList<>();
            String query = currentQuery.toLowerCase();
            for (FolderItem folder : folderList) {
                // Check if folder name matches or any video title matches
                boolean folderMatch = folder.name.toLowerCase().contains(query);
                boolean videoMatch = false;
                for (VideoItem v : folder.videos) {
                    if (v.title != null && v.title.toLowerCase().contains(query)) {
                        videoMatch = true;
                        break;
                    }
                }
                if (folderMatch || videoMatch) {
                    displayFolders.add(folder);
                }
            }
        }

        folderAdapter.setItems(displayFolders);
        recyclerView.setAdapter(folderAdapter);

        if (displayFolders.isEmpty()) {
            emptyView.setText(TextUtils.isEmpty(currentQuery) ? "No videos found" : "No matching folders");
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void openFolder(String folderName) {
        currentMode = MODE_VIDEOS;
        currentFolder = folderName;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(folderName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Tint back arrow white
        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon != null) {
            navIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        }

        showVideoList();
    }

    private void showVideoList() {
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

        videoAdapter.setItems(filteredVideos);
        recyclerView.setAdapter(videoAdapter);

        if (filteredVideos.isEmpty()) {
            emptyView.setText(TextUtils.isEmpty(currentQuery) ?
                    "No videos in this folder" : "No matching videos");
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        if (getSupportActionBar() != null) {
            String subtitle = filteredVideos.size() + " videos";
            if (showHidden) subtitle += " (incl. hidden)";
            getSupportActionBar().setSubtitle(subtitle);
        }

        // Tint back arrow white (must be done after layout)
        toolbar.post(() -> {
            Drawable navIcon = toolbar.getNavigationIcon();
            if (navIcon != null) {
                navIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (currentMode == MODE_VIDEOS) {
            showFolderList();
            return true;
        }
        return super.onSupportNavigateUp();
    }

    // --- MediaStore loading ---

    private void loadFromMediaStore() {
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
    }

    // --- File system scanning for .nomedia ---

    private boolean canScanFileSystem() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageManagerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
        }
    }

    private void scanFileSystem() {
        Set<String> existingPaths = new HashSet<>();
        for (VideoItem item : allVideos) {
            if (item.path != null) {
                existingPaths.add(item.path);
            }
        }

        File storageRoot = Environment.getExternalStorageDirectory();
        if (storageRoot != null && storageRoot.exists()) {
            scanDirectory(storageRoot, existingPaths);
        }
    }

    private void scanDirectory(File dir, Set<String> existingPaths) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("Android")) {
                    scanDirectory(file, existingPaths);
                }
            } else if (file.isFile() && isVideoFile(file.getName())) {
                String path = file.getAbsolutePath();
                if (!existingPaths.contains(path)) {
                    existingPaths.add(path);

                    VideoItem item = new VideoItem();
                    item.id = -1;
                    item.title = file.getName();
                    item.path = path;
                    item.size = file.length();
                    item.dateAdded = file.lastModified() / 1000;
                    item.bucketName = file.getParentFile() != null ?
                            file.getParentFile().getName() : "Unknown";
                    item.mimeType = getMimeType(file.getName());

                    try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                        retriever.setDataSource(path);
                        String dur = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (dur != null) {
                            item.duration = Long.parseLong(dur);
                        }
                        String w = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                        String h = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                        if (w != null && h != null) {
                            item.width = Integer.parseInt(w);
                            item.height = Integer.parseInt(h);
                        }
                    } catch (Exception e) {
                        continue;
                    }

                    if (item.duration <= 0) continue;

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
    }

    private boolean isVideoFile(String name) {
        String lower = name.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private String getMimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".avi")) return "video/avi";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".3gp")) return "video/3gpp";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".ts") || lower.endsWith(".mts")) return "video/mp2t";
        if (lower.endsWith(".wmv")) return "video/x-ms-wmv";
        if (lower.endsWith(".m4v")) return "video/mp4";
        return "video/*";
    }

    // --- Playback ---

    private void playVideo(VideoItem item) {
        Uri videoUri;
        if (item.id > 0) {
            videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id);
        } else {
            videoUri = Uri.fromFile(new File(item.path));
        }

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
        if (item.id < 0) {
            info.append("\n⚠ Hidden file (in .nomedia folder)");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(item.title)
                .setMessage(info.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // --- Menu ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.media_library, menu);

        hiddenToggle = menu.findItem(R.id.action_show_hidden);

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
                if (currentMode == MODE_FOLDERS) {
                    showFolderList();
                } else {
                    showVideoList();
                }
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
        } else if (id == R.id.action_show_hidden) {
            toggleShowHidden();
            return true;
        } else if (id == android.R.id.home) {
            if (currentMode == MODE_VIDEOS) {
                showFolderList();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleShowHidden() {
        if (showHidden) {
            showHidden = false;
            loadVideos();
        } else {
            if (canScanFileSystem()) {
                showHidden = true;
                loadVideos();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Storage access required")
                        .setMessage("To scan hidden videos (.nomedia folders), the app needs full storage access. Grant in next screen.")
                        .setPositiveButton(android.R.string.ok, (d, w) -> requestStorageManagerPermission())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                showHidden = true;
                loadVideos();
            }
        }
    }

    private void showSortDialog() {
        SortOrder[] orders = SortOrder.values();
        String[] labels = new String[orders.length];
        int checked = 0;
        for (int i = 0; i < orders.length; i++) {
            labels[i] = orders[i].label;
            if (orders[i] == sortOrder) checked = i;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Sort by")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    sortOrder = orders[which];
                    dialog.dismiss();
                    loadVideos();
                })
                .show();
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
