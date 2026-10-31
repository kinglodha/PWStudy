package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.io.MemoryUsageSetting;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryActivity extends Activity {
    private final String[] folders = {"All", "Physics", "Chemistry", "Biology", "Documents", "Videos"};
    private File base;
    private Spinner filter;
    private ListView list;
    private EditText search;
    private TextView summary;
    private final List<File> shown = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean newestFirst = true;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        base = new File(getExternalFilesDir(null), "PWStudy");
        for (String folder : folders) {
            if (!folder.equals("All")) new File(base, folder).mkdirs();
        }
        PDFBoxResourceLoader.init(getApplicationContext());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(12, 16, 25));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setMinWidth(0);
        back.setPadding(0, 0, 0, 0);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));
        TextView title = new TextView(this);
        title.setText("OFFLINE LIBRARY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button sort = new Button(this);
        sort.setText("Sort");
        sort.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            sort.setText(newestFirst ? "Newest" : "Oldest");
            refresh();
        });
        sort.setText("Newest");
        header.addView(sort, new LinearLayout.LayoutParams(dp(82), dp(48)));
        root.addView(header);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search downloaded files");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.GRAY);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { refresh(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));

        filter = new Spinner(this);
        filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folders));
        root.addView(filter, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout tools = new LinearLayout(this);
        Button merge = new Button(this);
        merge.setText("Merge PDFs");
        merge.setOnClickListener(v -> choosePdfsToMerge());
        tools.addView(merge, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh");
        refreshButton.setOnClickListener(v -> refresh());
        tools.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(tools);

        summary = new TextView(this);
        summary.setTextColor(Color.LTGRAY);
        summary.setPadding(dp(4), dp(6), dp(4), dp(6));
        root.addView(summary);

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { refresh(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> openFile(shown.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showFileActions(shown.get(position));
            return true;
        });
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (list == null || filter == null) return;
        shown.clear();
        String selected = String.valueOf(filter.getSelectedItem());
        if ("All".equals(selected)) {
            for (String folder : folders) if (!folder.equals("All")) collect(new File(base, folder));
        } else collect(new File(base, selected));

        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (!query.isEmpty()) {
            List<File> matches = new ArrayList<>();
            for (File file : shown) if (file.getName().toLowerCase(java.util.Locale.ROOT).contains(query)) matches.add(file);
            shown.clear();
            shown.addAll(matches);
        }
        shown.sort((a, b) -> newestFirst
                ? Long.compare(b.lastModified(), a.lastModified())
                : Long.compare(a.lastModified(), b.lastModified()));

        long total = 0;
        for (File f : shown) total += f.length();
        summary.setText(shown.size() + " file(s)  •  " + readableSize(total) + " shown");

        List<String> labels = new ArrayList<>();
        for (File f : shown) {
            String folder = f.getParentFile() == null ? "" : f.getParentFile().getName();
            labels.add(f.getName() + "\n" + folder + "  •  " + readableSize(f.length()) +
                    "  •  " + DateFormat.getDateInstance().format(new Date(f.lastModified())));
        }
        if (labels.isEmpty()) labels.add("No matching downloads.\nDownload files from the PW Jarvis website.");
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, labels) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                TextView name = row.findViewById(android.R.id.text1);
                TextView details = row.findViewById(android.R.id.text2);
                String[] parts = getItem(position).split("\\n", 2);
                name.setText(parts[0]);
                name.setTextColor(Color.WHITE);
                details.setText(parts.length > 1 ? parts[1] : "");
                details.setTextColor(Color.LTGRAY);
                row.setBackgroundColor(Color.rgb(12, 16, 25));
                return row;
            }
        });
    }

    private void collect(File folder) {
        File[] files = folder.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) shown.add(file);
    }

    private void openFile(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        Intent intent;
        if (name.endsWith(".pdf")) {
            intent = new Intent(this, PdfActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
        } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
                name.endsWith(".mov") || name.endsWith(".mp3") || name.endsWith(".m4v")) {
            intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
        } else {
            Toast.makeText(this, "No viewer available for this file type.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
    }

    private void showFileActions(File file) {
        String[] actions = {"Open", "Share", "Save copy to Downloads", "Rename", "Delete", "Details"};
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (d, which) -> {
            if (which == 0) openFile(file);
            else if (which == 1) shareFile(file);
            else if (which == 2) exportToDownloads(file);
            else if (which == 3) rename(file);
            else if (which == 4) new AlertDialog.Builder(this).setMessage("Delete this file permanently?")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (x, y) -> {
                        if (file.delete()) Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        refresh();
                    }).show();
            else new AlertDialog.Builder(this).setMessage("Folder: " + file.getParentFile().getName() +
                    "\nPath: " + file.getAbsolutePath() + "\nSize: " + readableSize(file.length()))
                    .setPositiveButton("OK", null).show();
        }).show();
    }

    private void shareFile(File file) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(getContentResolver().getType(uri) == null ? "*/*" : getContentResolver().getType(uri));
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share file"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share this file.", Toast.LENGTH_LONG).show();
        }
    }

    private void exportToDownloads(File file) {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Save to Downloads requires Android 10 or newer.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Downloads.MIME_TYPE, file.getName().toLowerCase().endsWith(".pdf")
                    ? "application/pdf" : "application/octet-stream");
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/PWStudy");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create Downloads entry");
            try (FileInputStream in = new FileInputStream(file);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Could not open output");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.flush();
            }
            Toast.makeText(this, "Saved to Downloads/PWStudy", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void choosePdfsToMerge() {
        List<File> pdfs = new ArrayList<>();
        for (String folder : folders) if (!folder.equals("All")) {
            File dir = new File(base, folder);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"));
            if (files != null) pdfs.addAll(Arrays.asList(files));
        }
        if (pdfs.size() < 2) {
            Toast.makeText(this, "At least two downloaded PDFs are required.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[pdfs.size()];
        boolean[] checked = new boolean[pdfs.size()];
        for (int i = 0; i < pdfs.size(); i++) names[i] = pdfs.get(i).getName();
        new AlertDialog.Builder(this).setTitle("Select PDFs to merge (at least 2)")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Merge", (dialog, which) -> {
                    List<File> selected = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) selected.add(pdfs.get(i));
                    if (selected.size() < 2) {
                        Toast.makeText(this, "Select at least two PDFs.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mergePdfs(selected);
                }).show();
    }

    private void mergePdfs(List<File> files) {
        Toast.makeText(this, "Merging PDFs…", Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            try {
                File outputDir = new File(base, "Documents");
                if (!outputDir.exists() && !outputDir.mkdirs()) throw new Exception("Cannot create output folder");
                File output = new File(outputDir, "Merged_" + System.currentTimeMillis() + ".pdf");
                PDFMergerUtility merger = new PDFMergerUtility();
                for (File file : files) merger.addSource(file);
                merger.setDestinationFileName(output.getAbsolutePath());
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
                runOnUiThread(() -> {
                    Toast.makeText(this, "Merged PDF saved in Documents.", Toast.LENGTH_LONG).show();
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Merge failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void rename(File file) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(file.getName());
        new AlertDialog.Builder(this).setTitle("Rename file").setView(input)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty() || newName.contains("/") || newName.contains("\\")) {
                        Toast.makeText(this, "Invalid file name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File target = new File(file.getParentFile(), newName);
                    if (target.exists()) Toast.makeText(this, "Name already exists.", Toast.LENGTH_SHORT).show();
                    else if (file.renameTo(target)) refresh();
                    else Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
