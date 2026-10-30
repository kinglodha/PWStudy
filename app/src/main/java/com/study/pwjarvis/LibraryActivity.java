package com.study.pwjarvis;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class LibraryActivity extends Activity {

    LinearLayout list;
    EditText searchBox;

    String selectedSubject = "Physics";
    String sortMode = "date";

    final int PICK_FILES = 1001;

    int BLACK = 0xFF000000;
    int CARD = 0xFF181818;
    int WHITE = 0xFFFFFFFF;
    int GREY = 0xFFAAAAAA;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        showSubjects();
    }

    // Main storage folder
    File getRoot() {
        File root = new File(
            getExternalFilesDir(null),
            "PWStudy"
        );

        if (!root.exists()) {
            root.mkdirs();
        }

        return root;
    }

    // Subject folder
    File getFolder(String subject) {

        File folder = new File(getRoot(), subject);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }

    // Main screen setup
    void setup(String title) {

        ScrollView scroll = new ScrollView(this);

        list = new LinearLayout(this);
        list.setOrientation(1);
        list.setPadding(18, 15, 18, 25);
        list.setBackgroundColor(BLACK);

        scroll.addView(list);
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(25);
        heading.setTextColor(WHITE);
        heading.setPadding(5, 20, 5, 25);

        list.addView(heading);
    }

    // Text label
    TextView label(String value) {

        TextView t = new TextView(this);

        t.setText(value);
        t.setTextSize(15);
        t.setTextColor(GREY);
        t.setPadding(8, 12, 8, 12);

        return t;
    }

    // Button
    Button makeButton(String text) {

        Button b = new Button(this);

        b.setText(text);
        b.setTextColor(WHITE);
        b.setAllCaps(false);

        b.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(CARD)
        );

        return b;
    }

    // Subject screen
    void showSubjects() {

        setup("PWStudy");

        list.addView(label("OFFLINE STUDY LIBRARY"));

        list.addView(label(
            "Your study materials are stored inside PWStudy."
        ));

        addSubject("Physics");
        addSubject("Chemistry");
        addSubject("Biology");

        list.addView(label("STORAGE INFORMATION"));

        list.addView(label(
            "Storage: " + getRoot().getAbsolutePath()
        ));

        Button refresh = makeButton("↻ Refresh Library");
        list.addView(refresh);

        refresh.setOnClickListener(v -> showSubjects());
    }

    // Subject card
    void addSubject(String subject) {

        File folder = getFolder(subject);

        int count = folder.listFiles() == null
            ? 0 : folder.listFiles().length;

        Button button = makeButton(
            subject + "   →   " + count + " files"
        );

        list.addView(button);

        button.setOnClickListener(v -> {

            selectedSubject = subject;
            showFiles();
        });
    }

    // Subject files screen
    void showFiles() {

        setup(selectedSubject);

        Button back = makeButton("← Back to Subjects");
        list.addView(back);

        back.setOnClickListener(v -> showSubjects());

        list.addView(label("YOUR FILES"));

        // Search
        searchBox = new EditText(this);

        searchBox.setSingleLine(true);
        searchBox.setHint("Search files...");
        searchBox.setHintTextColor(GREY);
        searchBox.setTextColor(WHITE);
        searchBox.setPadding(15, 12, 15, 12);
        searchBox.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(GREY)
        );

        list.addView(searchBox);

        // Import
        Button importButton = makeButton("+ Import Files");
        list.addView(importButton);

        importButton.setOnClickListener(v -> importFiles());

        // Sorting
        Button sortButton = makeButton("⇅ Sort: " + sortMode);
        list.addView(sortButton);

        sortButton.setOnClickListener(v -> {

            if (sortMode.equals("date")) {
                sortMode = "name";
            } else {
                sortMode = "date";
            }

            showFiles();
        });

        // Refresh
        Button refresh = makeButton("↻ Refresh");
        list.addView(refresh);

        refresh.setOnClickListener(v -> showFiles());

        // File list
        list.addView(label("DOWNLOADED MATERIALS"));

        displayFiles("");
        
        searchBox.addTextChangedListener(
            new android.text.TextWatcher() {

                public void beforeTextChanged(
                    CharSequence s, int start,
                    int count, int after
                ) {}

                public void onTextChanged(
                    CharSequence s, int start,
                    int before, int count
                ) {
                    displayFiles(s.toString());
                }

                public void afterTextChanged(
                    android.text.Editable e
                ) {}
            }
        );
    }

    // Display files
    void displayFiles(String query) {

        // Remove old file cards, keep top controls
        int start = 8;

        if (list.getChildCount() > start) {
            list.removeViews(
                start,
                list.getChildCount() - start
            );
        }

        File folder = getFolder(selectedSubject);
        File[] files = folder.listFiles();

        if (files == null || files.length == 0) {

            list.addView(label(
                "No files found. Import your study materials."
            ));

            return;
        }

        Arrays.sort(files, (a, b) -> {

            if (sortMode.equals("name")) {
                return a.getName().compareToIgnoreCase(b.getName());
            }

            return Long.compare(
                b.lastModified(),
                a.lastModified()
            );
        });

        int shown = 0;

        for (File file : files) {

            if (!file.isFile()) continue;

            if (!file.getName().toLowerCase().contains(
                query.toLowerCase()
            )) continue;

            shown++;

            addFileCard(file);
        }

        if (shown == 0) {
            list.addView(label("No matching files."));
        }
    }

    // File card
    void addFileCard(File file) {

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(1);
        card.setPadding(12, 12, 12, 12);
        card.setBackgroundColor(CARD);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                -1, -2
            );

        params.setMargins(0, 7, 0, 7);

        card.setLayoutParams(params);

        TextView name = new TextView(this);

        name.setText("📄 " + file.getName());
        name.setTextColor(WHITE);
        name.setTextSize(16);

        card.addView(name);

        TextView info = new TextView(this);

        info.setText(
            formatSize(file.length()) +
            "   •   " +
            formatDate(file.lastModified())
        );

        info.setTextColor(GREY);
        info.setTextSize(12);

        card.addView(info);

        Button options = makeButton("File Options");
        card.addView(options);

        options.setOnClickListener(v -> showFileOptions(file));

        card.setOnClickListener(v -> showFileOptions(file));

        list.addView(card);
    }

    // Import multiple files
    void importFiles() {

        Intent intent = new Intent(
            Intent.ACTION_OPEN_DOCUMENT
        );

        intent.setType("*/*");

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        );

        intent.putExtra(
            Intent.EXTRA_ALLOW_MULTIPLE,
            true
        );

        startActivityForResult(intent, PICK_FILES);
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        );

        if (requestCode != PICK_FILES ||
            resultCode != RESULT_OK ||
            data == null) {
            return;
        }

        int imported = 0;

        if (data.getClipData() != null) {

            android.content.ClipData clip =
                data.getClipData();

            for (int i = 0; i < clip.getItemCount(); i++) {

                Uri uri = clip.getItemAt(i).getUri();

                if (copyFile(uri)) {
                    imported++;
                }
            }

        } else if (data.getData() != null) {

            if (copyFile(data.getData())) {
                imported++;
            }
        }

        Toast.makeText(
            this,
            imported + " file(s) imported",
            Toast.LENGTH_LONG
        ).show();

        showFiles();
    }

    // Copy imported file into PWStudy
    boolean copyFile(Uri uri) {

        try {

            String name = "Imported_" +
                System.currentTimeMillis();

            String mime = getContentResolver()
                .getType(uri);

            if (mime != null) {

                String ext = android.webkit.MimeTypeMap
                    .getSingleton()
                    .getExtensionFromMimeType(mime);

                if (ext != null) {
                    name += "." + ext;
                }
            }

            File destination = new File(
                getFolder(selectedSubject),
                name
            );

            InputStream input =
                getContentResolver().openInputStream(uri);

            if (input == null) return false;

            FileOutputStream output =
                new FileOutputStream(destination);

            byte[] buffer = new byte[8192];

            int length;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            input.close();
            output.close();

            return true;

        } catch (Exception e) {

            Toast.makeText(
                this,
                "Import failed: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();

            return false;
        }
    }

    // File options
    void showFileOptions(File file) {

        String[] options = {
            "Open",
            "Rename",
            "Delete",
            "File Details"
        };

        new AlertDialog.Builder(this)
            .setTitle(file.getName())
            .setItems(options, (dialog, which) -> {

                if (which == 0) {
                    openFile(file);
                }

                if (which == 1) {
                    renameFile(file);
                }

                if (which == 2) {
                    deleteFile(file);
                }

                if (which == 3) {
                    showDetails(file);
                }

            })
            .show();
    }

    // Open file
    void openFile(File file) {

        String name = file.getName().toLowerCase();

        if (name.endsWith(".mp4") ||
            name.endsWith(".mkv") ||
            name.endsWith(".webm") ||
            name.endsWith(".mp3")) {

            Intent intent = new Intent(
                this,
                PlayerActivity.class
            );

            intent.putExtra(
                "video_uri",
                Uri.fromFile(file).toString()
            );

            startActivity(intent);

        } else {

            Toast.makeText(
                this,
                "PDF viewing needs a working PDF viewer. " +
                "Your current PdfActivity is only a placeholder.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    // Rename
    void renameFile(File file) {

        EditText input = new EditText(this);

        input.setText(file.getName());
        input.setTextColor(WHITE);

        new AlertDialog.Builder(this)
            .setTitle("Rename File")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {

                String newName =
                    input.getText().toString().trim();

                if (newName.isEmpty()) return;

                File newFile = new File(
                    file.getParent(),
                    newName
                );

                if (newFile.exists()) {

                    Toast.makeText(
                        this,
                        "A file with this name already exists",
                        Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                if (file.renameTo(newFile)) {

                    Toast.makeText(
                        this,
                        "File renamed",
                        Toast.LENGTH_SHORT
                    ).show();

                    showFiles();

                } else {

                    Toast.makeText(
                        this,
                        "Rename failed",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Delete
    void deleteFile(File file) {

        new AlertDialog.Builder(this)
            .setTitle("Delete File?")
            .setMessage(
                "Are you sure you want to delete " +
                file.getName() + "?"
            )
            .setPositiveButton("Delete", (d, w) -> {

                if (file.delete()) {

                    Toast.makeText(
                        this,
                        "File deleted",
                        Toast.LENGTH_SHORT
                    ).show();

                    showFiles();

                } else {

                    Toast.makeText(
                        this,
                        "Delete failed",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Details
    void showDetails(File file) {

        String details =
            "Name: " + file.getName() +
            "\n\nSize: " + formatSize(file.length()) +
            "\n\nLocation: " + file.getAbsolutePath() +
            "\n\nModified: " +
            formatDate(file.lastModified());

        new AlertDialog.Builder(this)
            .setTitle("File Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show();
    }

    // File size
    String formatSize(long bytes) {

        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {
            return String.format(
                Locale.US,
                "%.1f KB",
                bytes / 1024.0
            );
        }

        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(
                Locale.US,
                "%.1f MB",
                bytes / (1024.0 * 1024.0)
            );
        }

        return String.format(
            Locale.US,
            "%.2f GB",
            bytes / (1024.0 * 1024.0 * 1024.0)
        );
    }

    // Date
    String formatDate(long time) {

        return new SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(new Date(time));
    }
}