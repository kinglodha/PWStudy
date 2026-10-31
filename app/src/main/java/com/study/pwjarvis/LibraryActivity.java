package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LibraryActivity extends Activity {
    private final String[] folders = {"All", "Physics", "Chemistry", "Biology", "Documents", "Videos"};
    private File base;
    private Spinner filter;
    private ListView list;
    private final List<File> shown = new ArrayList<>();

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("OFFLINE LIBRARY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));

        filter = new Spinner(this);
        filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folders));
        root.addView(filter, new LinearLayout.LayoutParams(-1, dp(50)));

        Button home = new Button(this);
        home.setText("Back to Home");
        home.setOnClickListener(v -> finish());
        root.addView(home, new LinearLayout.LayoutParams(-1, dp(46)));

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { refresh(); }
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
        shown.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        List<String> labels = new ArrayList<>();
        for (File f : shown) {
            String folder = f.getParentFile() == null ? "" : f.getParentFile().getName();
            labels.add(f.getName() + "\n" + folder + "  •  " + readableSize(f.length()) +
                    "  •  " + DateFormat.getDateInstance().format(new Date(f.lastModified())));
        }
        if (labels.isEmpty()) labels.add("No downloaded files yet.\nOpen Website and download a document or video.");
        list.setAdapter(new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_2, android.R.id.text1, labels
        ) {
            @Override
            public android.view.View getView(int position, android.view.View convertView,
                                             android.view.ViewGroup parent) {
                android.view.View row = super.getView(position, convertView, parent);
                TextView name = row.findViewById(android.R.id.text1);
                TextView details = row.findViewById(android.R.id.text2);
                String[] parts = getItem(position).split("\\n", 2);
                name.setText(parts[0]);
                name.setTextColor(Color.WHITE);
                details.setText(parts.length > 1 ? parts[1] : "");
                details.setTextColor(Color.GRAY);
                row.setBackgroundColor(Color.BLACK);
                return row;
            }
        });
    }

    private void collect(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) if (file.isFile()) shown.add(file);
    }

    private void openFile(File file) {
        String name = file.getName().toLowerCase();
        Intent intent;
        if (name.endsWith(".pdf")) {
            intent = new Intent(this, PdfActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
        } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
                name.endsWith(".mov") || name.endsWith(".mp3") || name.endsWith(".m4v")) {
            intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
        } else {
            Toast.makeText(this, "इस फ़ाइल का viewer उपलब्ध नहीं है।", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
    }

    private void showFileActions(File file) {
        String[] actions = {"Open", "Rename", "Delete", "Details"};
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (d, which) -> {
            if (which == 0) openFile(file);
            else if (which == 1) rename(file);
            else if (which == 2) new AlertDialog.Builder(this).setMessage("Delete this file?")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (x, y) -> {
                        if (file.delete()) Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        refresh();
                    }).show();
            else new AlertDialog.Builder(this).setMessage("Path: " + file.getAbsolutePath() +
                    "\nSize: " + readableSize(file.length())).setPositiveButton("OK", null).show();
        }).show();
    }

    private void rename(File file) {
        android.widget.EditText input = new android.widget.EditText(this);
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
                    if (target.exists()) Toast.makeText(this, "यह नाम पहले से मौजूद है।", Toast.LENGTH_SHORT).show();
                    else if (file.renameTo(target)) refresh();
                    else Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
