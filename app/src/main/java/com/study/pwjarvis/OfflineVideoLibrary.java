package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.webkit.URLUtil;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Small offline video library backed by Android DownloadManager.
 *
 * Only direct HTTPS media URLs are supported. The system download service
 * handles background transfers, retries, and download notifications.
 * Downloads are placed in app-owned external Movies storage: no broad storage
 * permissions or external Telegram session access are required.
 */
final class OfflineVideoLibrary {
    private static final String FOLDER = "PWStudy";
    private static final String[] EXTENSIONS = {".mp4", ".m4v", ".webm", ".mkv", ".mov"};

    private OfflineVideoLibrary() { }

    static void offerDownload(Activity activity, Uri source) {
        if (source == null || !"https".equalsIgnoreCase(source.getScheme())) {
            toast(activity, "Only direct HTTPS video downloads are supported");
            return;
        }
        String host = source.getHost();
        if (host == null || host.isEmpty() || source.getUserInfo() != null) {
            toast(activity, "Invalid download URL");
            return;
        }
        String name = filename(source);
        if (name == null) {
            toast(activity, "This link is not a supported direct video file");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Save video for offline study?")
                .setMessage("Download " + name + " using Android Download Manager? " +
                        "Only save videos you are permitted to download. " +
                        "Protected streams and private channel media are not supported.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Download", (dialog, which) -> enqueue(activity, source, name))
                .show();
    }

    static void showSaved(Activity activity) {
        File dir = directory(activity);
        if (dir == null) {
            toast(activity, "Offline video storage is unavailable");
            return;
        }
        File[] files = dir.listFiles(file -> file.isFile() && isVideo(file.getName()));
        if (files == null || files.length == 0) {
            new AlertDialog.Builder(activity)
                    .setTitle("Offline videos")
                    .setMessage("No downloaded videos yet. Direct HTTPS video downloads will appear here.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        ArrayList<String> labels = new ArrayList<>();
        for (File file : files) {
            long mb = Math.max(1L, (file.length() + 1048575L) / 1048576L);
            labels.add(file.getName() + "  •  " + mb + " MB");
        }
        new AlertDialog.Builder(activity)
                .setTitle("Offline videos (" + files.length + ")")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    Intent intent = new Intent(activity, PlayerActivity.class);
                    intent.setData(Uri.fromFile(files[which]));
                    activity.startActivity(intent);
                })
                .setNegativeButton("Close", null).show();
    }

    private static void enqueue(Activity activity, Uri source, String name) {
        File dir = directory(activity);
        if (dir == null) {
            toast(activity, "Cannot access offline storage");
            return;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            toast(activity, "Cannot create offline video folder");
            return;
        }
        String uniqueName = availableFilename(dir, name);
        try {
            DownloadManager manager =
                    (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Download service unavailable");
            DownloadManager.Request request = new DownloadManager.Request(source);
            request.setTitle(uniqueName);
            request.setDescription("PWStudy offline lecture");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_MOVIES,
                    FOLDER + "/" + uniqueName);
            manager.enqueue(request);
            toast(activity, "Download queued. Check system Downloads for progress.");
        } catch (Exception error) {
            toast(activity, "Download could not start: " + error.getMessage());
        }
    }

    private static File directory(Context context) {
        File root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        return root == null ? null : new File(root, FOLDER);
    }

    private static String availableFilename(File directory, String name) {
        int dot = name.lastIndexOf('.');
        String stem = name.substring(0, dot);
        String ext = name.substring(dot);
        String candidate = name;
        for (int number = 2; new File(directory, candidate).exists(); number++) {
            candidate = stem + " (" + number + ")" + ext;
        }
        return candidate;
    }

    private static String filename(Uri uri) {
        String path = uri.getPath();
        if (path == null) return null;
        String name = Uri.decode(path.substring(path.lastIndexOf('/') + 1));
        if (!isVideo(name)) return null;
        // A filename must be safe as a single file component on Android.
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() > 100) {
            int dot = name.lastIndexOf('.');
            name = name.substring(0, 90) + name.substring(dot);
        }
        return name.isEmpty() ? null : name;
    }

    private static boolean isVideo(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) if (lower.endsWith(extension)) return true;
        return false;
    }

    private static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}
