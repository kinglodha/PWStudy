package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local study tools. Search and speech work for PDFs that contain selectable text.
 * Notes/bookmarks stay in PWStudy; source PDFs are never overwritten.
 */
public class PdfStudyToolsActivity extends Activity {
    private static final int EXPORT_DESTINATION = 810;
    private static final int MERGE_SOURCE = 811;
    private static final int MERGE_DESTINATION = 812;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String documentId;
    private int currentPage;
    private int pageCount;
    private TextView status;
    private LinearLayout results;
    private Button bookmarkButton;
    private Uri secondDocument;
    private int extractStart;
    private int extractEnd;
    private TextToSpeech speech;
    private boolean voiceReady;
    private String pendingSpeech;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        documentId = getIntent().getStringExtra("document_id");
        currentPage = getIntent().getIntExtra("page_index", 0);
        pageCount = getIntent().getIntExtra("page_count", 0);
        if (documentId == null || pageCount <= 0) {
            Toast.makeText(this, "Open a PDF first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        PDFBoxResourceLoader.init(getApplicationContext());
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(0xFF0C1423);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(32));
        scroller.addView(root);
        TextView header = text("‹  Advanced PDF Study Tools", 22, Color.WHITE, true);
        header.setOnClickListener(v -> finish());
        root.addView(header);
        TextView summary = text("Page " + (currentPage + 1) + " of " + pageCount
                + "  •  Offline document tools", 13, 0xFFB4C2D8, false);
        summary.setPadding(0, dp(8), 0, dp(16));
        root.addView(summary);
        status = text("Your original PDF is kept unchanged. Notes and bookmarks save inside PWStudy.", 13,
                0xFFB9C9DE, false);
        status.setPadding(dp(4), dp(10), dp(4), dp(14));
        root.addView(status);
        addSection(root, "READ & FIND");
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText query = new EditText(this);
        query.setHint("Find words in this PDF");
        query.setSingleLine(true);
        query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xFF97A9C1);
        searchRow.addView(query, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button find = button("Search");
        searchRow.addView(find);
        root.addView(searchRow);
        find.setOnClickListener(v -> searchPdf(query.getText().toString()));
        addButton(root, "🔊 Read current page aloud", this::readPage);
        addButton(root, "Stop reading", () -> {
            pendingSpeech = null;
            if (speech != null) speech.stop();
            updateStatus("Reading stopped.");
        });
        addSection(root, "STUDY ORGANIZER");
        bookmarkButton = addButton(root, "", this::toggleBookmark);
        updateBookmarkLabel();
        addButton(root, "View saved bookmarks", this::openBookmarks);
        addButton(root, "Write / edit notes for this page", this::editNote);
        addSection(root, "EDIT A COPY");
        addButton(root, "Extract selected page range to a new PDF", this::askPageRange);
        addButton(root, "Combine with another PDF (save a new file)", this::pickMergeSource);
        addSection(root, "SEARCH RESULTS");
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
        TextView hint = text("Searching selectable PDF text works offline. Scanned image-only PDFs need OCR, which is not included in this version.", 12, 0xFF9FB1CA, false);
        hint.setPadding(0, dp(14), 0, 0);
        root.addView(hint);
        setContentView(scroller);
    }

    private TextView text(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1D3655);
        background.setCornerRadius(dp(11));
        b.setBackground(background);
        return b;
    }
    private Button addButton(LinearLayout row, String title, Runnable onClick) {
        Button b = button(title);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        row.addView(b, params);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }
    private void addSection(LinearLayout parent, String label) {
        TextView heading = text(label, 15, 0xFF81B9F5, true);
        heading.setPadding(0, dp(16), 0, dp(9));
        parent.addView(heading);
    }
    private void updateStatus(String message) {
        if (isFinishing() || isDestroyed()) return;
        runOnUiThread(() -> { if (!isFinishing() && status != null) status.setText(message); });
    }
    private void updateBookmarkLabel() {
        bookmarkButton.setText(PdfStudyStorage.isBookmarked(this, documentId, currentPage)
                ? "★ Remove bookmark from page " + (currentPage + 1)
                : "☆ Bookmark page " + (currentPage + 1));
    }
    private void toggleBookmark() {
        PdfStudyStorage.toggleBookmark(this, documentId, currentPage);
        updateBookmarkLabel();
        updateStatus("Bookmark updated for page " + (currentPage + 1) + ".");
    }
    private void openBookmarks() {
        List<Integer> pages = PdfStudyStorage.bookmarks(this, documentId);
        if (pages.isEmpty()) {
            updateStatus("No bookmarks yet. Bookmark a page first.");
            return;
        }
        String[] names = new String[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            int p = pages.get(i);
            String note = PdfStudyStorage.pageNote(this, documentId, p);
            names[i] = "Page " + (p + 1) + (note.isEmpty() ? "" : "  •  " + note.replace('\n', ' ').substring(0, Math.min(note.length(), 32)));
        }
        new AlertDialog.Builder(this).setTitle("Bookmarks")
                .setItems(names, (dialog, which) -> jumpToPage(pages.get(which)))
                .setNegativeButton("Close", null).show();
    }
    private void editNote() {
        EditText input = new EditText(this);
        input.setMinLines(4);
        input.setMaxLines(9);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(PdfStudyStorage.pageNote(this, documentId, currentPage));
        int pad = dp(14);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this).setTitle("Notes • page " + (currentPage + 1))
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete note", (d, w) -> {
                    PdfStudyStorage.saveNote(this, documentId, currentPage, "");
                    updateStatus("Note removed.");
                })
                .setPositiveButton("Save", (d, w) -> {
                    PdfStudyStorage.saveNote(this, documentId, currentPage, input.getText().toString());
                    updateStatus("Page note saved inside PWStudy.");
                }).show();
    }
    private void jumpToPage(int zeroBased) {
        setResult(RESULT_OK, new Intent().putExtra("go_to_page", zeroBased));
        finish();
    }
    private InputStream openDocument(String id) throws Exception {
        Uri parsed = Uri.parse(id);
        if ("content".equalsIgnoreCase(parsed.getScheme()))
            return getContentResolver().openInputStream(parsed);
        if ("file".equalsIgnoreCase(parsed.getScheme()))
            return new FileInputStream(new File(parsed.getPath()));
        return new FileInputStream(new File(id));
    }
    private void asyncRead(Runnable task) {
        io.execute(task);
    }
    private void searchPdf(String input) {
        final String query = input.trim();
        if (query.isEmpty()) {
            updateStatus("Enter text to search.");
            return;
        }
        results.removeAllViews();
        updateStatus("Searching document…");
        asyncRead(() -> {
            List<Integer> pages = new ArrayList<>();
            List<String> previews = new ArrayList<>();
            try (InputStream in = openDocument(documentId);
                 PDDocument pdf = PDDocument.load(in, MemoryUsageSetting.setupTempFileOnly())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String needle = query.toLowerCase(Locale.ROOT);
                for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String pageText = stripper.getText(pdf);
                    int where = pageText.toLowerCase(Locale.ROOT).indexOf(needle);
                    if (where >= 0) {
                        pages.add(i);
                        int start = Math.max(0, where - 37);
                        int end = Math.min(pageText.length(), where + query.length() + 52);
                        previews.add(pageText.substring(start, end).replace('\n', ' ').replace('\r', ' '));
                    }
                    if (i % 25 == 0) updateStatus("Searching page " + (i + 1) + " / " + pdf.getNumberOfPages());
                    if (pages.size() >= 100) break;
                }
                runOnUiThread(() -> showSearchResults(pages, previews, query));
            } catch (Exception e) {
                updateStatus("Search failed: " + e.getMessage());
            }
        });
    }
    private void showSearchResults(List<Integer> pages, List<String> previews, String query) {
        if (isFinishing() || isDestroyed()) return;
        results.removeAllViews();
        updateStatus(pages.isEmpty() ? "No matching selectable text for: " + query
                : pages.size() + " matching pages for: " + query
                + (pages.size() >= 100 ? " (first 100 shown)" : ""));
        for (int i = 0; i < pages.size(); i++) {
            final int target = pages.get(i);
            addButton(results, "Page " + (target + 1) + ": " + previews.get(i),
                    () -> jumpToPage(target));
        }
    }
    private void readPage() {
        updateStatus("Extracting page text for read-aloud…");
        asyncRead(() -> {
            try (InputStream in = openDocument(documentId);
                 PDDocument pdf = PDDocument.load(in, MemoryUsageSetting.setupTempFileOnly())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(currentPage + 1);
                stripper.setEndPage(currentPage + 1);
                String pageText = stripper.getText(pdf).trim();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (pageText.isEmpty()) {
                        updateStatus("This page has no selectable text. OCR is needed for scans.");
                        return;
                    }
                    int max = TextToSpeech.getMaxSpeechInputLength() - 100;
                    pendingSpeech = pageText.substring(0, Math.min(pageText.length(), max));
                    if (speech == null) speech = new TextToSpeech(this, this::onSpeechReady);
                    else if (voiceReady) speakPending();
                });
            } catch (Exception e) { updateStatus("Read-aloud unavailable: " + e.getMessage()); }
        });
    }
    private void onSpeechReady(int code) {
        voiceReady = code == TextToSpeech.SUCCESS;
        if (!voiceReady) {
            updateStatus("Device text-to-speech engine is unavailable.");
            return;
        }
        speech.setLanguage(Locale.getDefault());
        speakPending();
    }
    private void speakPending() {
        if (speech == null || !voiceReady || pendingSpeech == null) return;
        speech.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "pwstudy-read-pdf-page");
        pendingSpeech = null;
        updateStatus("Reading page " + (currentPage + 1) + ". Long pages may be shortened.");
    }
    private void askPageRange() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText((currentPage + 1) + "-" + (currentPage + 1));
        input.setHint("e.g. 5-12");
        new AlertDialog.Builder(this).setTitle("Extract pages (1–" + pageCount + ")")
                .setMessage("Create a new PDF containing an inclusive range of pages.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose save location", (d, w) -> {
                    String[] parts = input.getText().toString().trim().split("-");
                    try {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = parts.length == 1 ? start : Integer.parseInt(parts[1].trim());
                        if (parts.length > 2 || start < 1 || end < start || end > pageCount)
                            throw new IllegalArgumentException("Range is outside the PDF");
                        extractStart = start - 1;
                        extractEnd = end - 1;
                        createDestination(EXPORT_DESTINATION, "PWStudy-pages-" + start + "-" + end + ".pdf");
                    } catch (Exception e) {
                        updateStatus("Invalid range. Use a start-end range from 1 to " + pageCount + ".");
                    }
                }).show();
    }
    private void pickMergeSource() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("application/pdf");
        startActivityForResult(pick, MERGE_SOURCE);
    }
    private void createDestination(int code, String filename) {
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/pdf");
        save.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(save, code);
    }
    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == MERGE_SOURCE) {
            secondDocument = uri;
            createDestination(MERGE_DESTINATION, "PWStudy-combined.pdf");
        } else if (requestCode == EXPORT_DESTINATION) {
            exportRange(uri);
        } else if (requestCode == MERGE_DESTINATION && secondDocument != null) {
            mergePdf(uri, secondDocument);
        }
    }
    private void exportRange(Uri destination) {
        updateStatus("Extracting pages…");
        final int first = extractStart, last = extractEnd;
        asyncRead(() -> {
            try (InputStream source = openDocument(documentId);
                 PDDocument original = PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly());
                 PDDocument extracted = new PDDocument()) {
                for (int page = first; page <= last; page++) extracted.importPage(original.getPage(page));
                try (OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                    if (out == null) throw new Exception("Cannot open save location");
                    extracted.save(out);
                }
                updateStatus("Saved " + (last - first + 1) + " extracted pages as a new PDF.");
            } catch (Exception e) { updateStatus("Page extraction failed: " + e.getMessage()); }
        });
    }
    private void mergePdf(Uri destination, Uri second) {
        updateStatus("Merging PDFs…");
        asyncRead(() -> {
            try (InputStream firstStream = openDocument(documentId);
                 InputStream secondStream = getContentResolver().openInputStream(second);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (firstStream == null || secondStream == null || out == null)
                    throw new Exception("Could not open the selected PDFs");
                PDFMergerUtility merge = new PDFMergerUtility();
                merge.addSource(firstStream);
                merge.addSource(secondStream);
                merge.setDestinationStream(out);
                merge.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
                updateStatus("A new combined PDF was saved. Originals are unchanged.");
            } catch (Exception e) { updateStatus("Merge failed: " + e.getMessage()); }
        });
    }
    @Override protected void onDestroy() {
        io.shutdownNow();
        if (speech != null) {
            speech.stop();
            speech.shutdown();
        }
        super.onDestroy();
    }
}
