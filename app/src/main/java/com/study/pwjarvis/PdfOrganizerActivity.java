package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.text.format.Formatter;
import androidx.core.view.WindowCompat;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local-only audit and rename planning. Source documents stay untouched. */
public class PdfOrganizerActivity extends Activity {
    private static final int PICK = 5012;
    private static final int EXPORT = 5013;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ArrayList<PdfFolderAudit.Entry> visible = new ArrayList<>();
    private final Set<String> duplicateIds = new HashSet<>();
    private PdfFolderAudit.Result report;
    private Uri root;
    private TextView status;
    private Button scanButton;
    private Button duplicateButton;
    private Button stopButton;
    private Button exportButton;
    private EditText query;
    private Spinner filter;
    private BaseAdapter adapter;
    private boolean busy;
    private boolean destroyed;
    private String filterValue = "All PDFs";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(12), dp(10), dp(12), dp(8));
        screen.setBackgroundColor(0xff10151e);
        TextView heading = text("Offline PDF Organizer", 23);
        screen.addView(heading);
        screen.addView(text("Review names, subjects and duplicate files without changing originals.", 14));
        screen.addView(button("Choose storage folder", this::choose));
        LinearLayout controls = new LinearLayout(this);
        scanButton = button("Scan names", () -> startScan(false));
        duplicateButton = button("Verify duplicates", () -> startScan(true));
        controls.addView(scanButton, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(duplicateButton, new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(controls);
        stopButton = button("Cancel scan", () -> {
            cancelled.set(true);
            status.setText("Stopping safely…");
        });
        screen.addView(stopButton);
        exportButton = button("Export review CSV", this::chooseExport);
        screen.addView(exportButton);
        status = text("Choose a folder to begin.", 13);
        status.setPadding(0, dp(6), 0, dp(6));
        screen.addView(status);

        query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Find PDF or proposed name");
        query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xffacb8c6);
        screen.addView(query);
        filter = new Spinner(this);
        String[] values = {"All PDFs", "Rename suggestions", "Physics", "Chemistry",
                "Biology", "Needs review", "Name collisions", "Verified duplicates"};
        ArrayAdapter<String> filters = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values);
        filter.setAdapter(filters);
        screen.addView(filter);
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                filterValue = values[position];
                refresh();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable e) {}
        });
        ListView list = new ListView(this);
        adapter = new BaseAdapter() {
            public int getCount() { return visible.size(); }
            public Object getItem(int position) { return visible.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View old, ViewGroup parent) {
                PdfFolderAudit.Entry entry = visible.get(position);
                LinearLayout row = new LinearLayout(PdfOrganizerActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10), dp(9), dp(10), dp(9));
                row.addView(text(entry.name, 16));
                row.addView(text(entry.path + entry.suggestion, 13));
                String size = entry.bytes < 0 ? "Unknown size" :
                        Formatter.formatFileSize(PdfOrganizerActivity.this, entry.bytes);
                String label = entry.subject + " · " + size;
                if (duplicateIds.contains(entry.uri.toString())) label += " · Duplicate";
                if (report != null && report.collisions.contains(PdfFolderAudit.collisionKey(entry))) {
                    label += " · Name collision";
                }
                row.addView(text(label, 12));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> inspect(visible.get(position)));
        screen.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        String saved = getSharedPreferences("pdf_manager", MODE_PRIVATE).getString("tree", null);
        String own = getPreferences(MODE_PRIVATE).getString("tree", saved);
        if (own != null) {
            try {
                root = Uri.parse(own);
                status.setText("Storage selected. Run a read-only scan.");
            } catch (RuntimeException ignored) {}
        }
        updateButtons();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xffe4eaf1);
        return view;
    }

    private Button button(String name, Runnable action) {
        Button view = new Button(this);
        view.setText(name);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void updateButtons() {
        scanButton.setEnabled(!busy && root != null);
        duplicateButton.setEnabled(!busy && root != null);
        stopButton.setEnabled(busy);
        exportButton.setEnabled(!busy && report != null);
    }

    private void choose() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK);
        } catch (Exception e) {
            status.setText("Storage picker unavailable.");
        }
    }

    private void chooseExport() {
        if (busy || report == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "Tweak_PDF_Review.csv");
        try {
            startActivityForResult(intent, EXPORT);
        } catch (Exception e) {
            status.setText("Export picker unavailable.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
                root = uri;
                getPreferences(MODE_PRIVATE).edit().putString("tree", uri.toString()).apply();
                report = null;
                duplicateIds.clear();
                refresh();
                status.setText("Ready. Source files will not be modified.");
                updateButtons();
            } catch (SecurityException e) {
                status.setText("Unable to save storage permission.");
            }
        } else if (request == EXPORT && report != null) {
            export(uri, report);
        }
    }

    private void startScan(boolean hashes) {
        if (root == null || busy) return;
        busy = true;
        cancelled.set(false);
        updateButtons();
        status.setText(hashes ? "Scanning and comparing SHA-256 hashes…" : "Scanning PDF filenames…");
        Uri selected = root;
        worker.execute(() -> {
            try {
                PdfFolderAudit.Result result = new PdfFolderAudit(this, cancelled).scan(selected, hashes,
                        (files, folders) -> runOnUiThread(() -> {
                            if (!destroyed && !cancelled.get()) {
                                status.setText(files + " PDFs · " + folders + " folders");
                            }
                        }));
                runOnUiThread(() -> finishScan(result));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    busy = false;
                    status.setText("Scan failed. Re-select the folder if its access expired.");
                    updateButtons();
                });
            }
        });
    }

    private void finishScan(PdfFolderAudit.Result result) {
        if (destroyed) return;
        report = result;
        busy = false;
        duplicateIds.clear();
        for (List<PdfFolderAudit.Entry> group : result.duplicates.values()) {
            for (PdfFolderAudit.Entry entry : group) duplicateIds.add(entry.uri.toString());
        }
        refresh();
        updateButtons();
    }

    private void refresh() {
        if (adapter == null) return;
        visible.clear();
        if (report != null) {
            String term = query.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (PdfFolderAudit.Entry entry : report.entries) {
                if (!matches(entry)) continue;
                String value = (entry.path + entry.name + " " + entry.suggestion).toLowerCase(Locale.ROOT);
                if (value.contains(term)) visible.add(entry);
            }
            status.setText((report.partial ? "Partial scan · " : "") +
                    visible.size() + " of " + report.entries.size() + " PDFs · " +
                    Formatter.formatFileSize(this, report.bytes) + " · " +
                    report.duplicates.size() + " duplicate groups · " + report.errors + " errors");
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matches(PdfFolderAudit.Entry entry) {
        switch (filterValue) {
            case "Rename suggestions": return !entry.name.equals(entry.suggestion);
            case "Name collisions": return report.collisions.contains(PdfFolderAudit.collisionKey(entry));
            case "Verified duplicates": return duplicateIds.contains(entry.uri.toString());
            case "All PDFs": return true;
            default: return filterValue.equals(entry.subject);
        }
    }

    private void inspect(PdfFolderAudit.Entry entry) {
        String size = entry.bytes < 0 ? "Unknown" : Formatter.formatFileSize(this, entry.bytes);
        String summary = "Original: " + entry.path + entry.name + "\n\nSuggested: " +
                entry.suggestion + "\nSubject: " + entry.subject + "\nSize: " + size +
                "\n\nNo rename or deletion has been applied.";
        new AlertDialog.Builder(this)
                .setTitle("Review PDF")
                .setMessage(summary)
                .setPositiveButton("Open PDF", (dialog, which) -> {
                    try {
                        startActivity(new Intent(this, PdfActivity.class).setData(entry.uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open PDF", Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Copy name", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Suggested PDF filename", entry.suggestion));
                })
                .setNegativeButton("Close", null).show();
    }

    private void export(Uri target, PdfFolderAudit.Result snapshot) {
        busy = true;
        updateButtons();
        Set<String> duplicates = new HashSet<>(duplicateIds);
        worker.execute(() -> {
            String message;
            try (OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
                if (output == null) throw new java.io.IOException("Cannot open export");
                try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                    writer.write("Original path,Proposed filename,Subject,Bytes,Collision,Verified duplicate\n");
                    for (PdfFolderAudit.Entry entry : snapshot.entries) {
                        writer.write(PdfNameRules.csv(entry.path + entry.name) + "," +
                                PdfNameRules.csv(entry.suggestion) + "," +
                                PdfNameRules.csv(entry.subject) + "," + entry.bytes + "," +
                                snapshot.collisions.contains(PdfFolderAudit.collisionKey(entry)) + "," +
                                duplicates.contains(entry.uri.toString()) + "\n");
                    }
                }
                message = "Review CSV saved. Original PDFs unchanged.";
            } catch (Exception e) {
                message = "Export failed; check destination access.";
            }
            String finalMessage = message;
            runOnUiThread(() -> {
                if (destroyed) return;
                busy = false;
                status.setText(finalMessage);
                updateButtons();
            });
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        cancelled.set(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
