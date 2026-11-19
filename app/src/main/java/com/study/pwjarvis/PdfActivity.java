package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

/** Offline PDF reader: page navigation, zoom/pan and reading-position persistence. */
public class PdfActivity extends Activity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private ImageView pageImage;
    private TextView pageLabel;
    private HorizontalScrollView horizontal;
    private ScrollView vertical;
    private Bitmap currentBitmap;
    private int pageIndex = 0;
    private float zoom = 1f;
    private String documentKey;
    private int screenWidth;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18,18,18));
        Uri uri = getIntent().getData();
        if (uri == null) {
            String extra = getIntent().getStringExtra("pdf_uri");
            if (extra != null) uri = Uri.parse(extra);
        }
        if (uri == null) { showError("No PDF selected"); return; }
        documentKey = uri.toString();
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                descriptor = ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                descriptor = getContentResolver().openFileDescriptor(uri, "r");
            }
            if (descriptor == null) throw new IOException("Cannot access PDF");
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) throw new IOException("Empty PDF");
        } catch (Exception ex) {
            showError("Unable to open PDF: " + ex.getMessage());
            return;
        }
        pageIndex = Math.max(0, Math.min(renderer.getPageCount()-1,
                getPreferences(MODE_PRIVATE).getInt(documentKey, 0)));
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        buildUi();
        renderPage();
    }

    private Button button(String label, LinearLayout row, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        b.setOnClickListener(listener);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(24,24,24));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(controls);
        button("Back", controls, v -> finish());
        button("−", controls, v -> changeZoom(zoom / 1.5f));
        button("Fit", controls, v -> changeZoom(1f));
        button("+", controls, v -> changeZoom(zoom * 1.5f));
        button("Go to", controls, v -> jumpToPage());
        button("Marks", controls, v -> showBookmarks());
        pageLabel = new TextView(this);
        pageLabel.setTextColor(Color.WHITE);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setPadding(8, 8, 8, 8);
        root.addView(pageLabel);
        horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        vertical = new ScrollView(this);
        vertical.setFillViewport(true);
        pageImage = new ImageView(this);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageImage.setAdjustViewBounds(true);
        vertical.addView(pageImage);
        horizontal.addView(vertical);
        root.addView(horizontal, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this);
        root.addView(navigation);
        button("◀ Previous", navigation, v -> goTo(pageIndex - 1));
        button("Next ▶", navigation, v -> goTo(pageIndex + 1));
        button("☆ Save", navigation, v -> toggleBookmark());
        setContentView(root);
    }

    private void changeZoom(float value) {
        float next = Math.max(1f, Math.min(4f, value));
        if (next != zoom) { zoom = next; renderPage(); }
    }

    private void goTo(int index) {
        if (renderer == null || index < 0 || index >= renderer.getPageCount()) return;
        pageIndex = index;
        getPreferences(MODE_PRIVATE).edit().putInt(documentKey, pageIndex).apply();
        renderPage();
    }

    private void jumpToPage() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("1 - " + renderer.getPageCount());
        new AlertDialog.Builder(this).setTitle("Go to page").setView(input)
            .setPositiveButton("Go", (d,w) -> {
                try { goTo(Integer.parseInt(input.getText().toString()) - 1); }
                catch (NumberFormatException ignored) { }
            }).setNegativeButton("Cancel", null).show();
    }

    private String bookmarkKey() {
        return "bookmark:" + documentKey;
    }

    private Set<String> savedBookmarks() {
        return new HashSet<>(getPreferences(MODE_PRIVATE)
                .getStringSet(bookmarkKey(), Collections.emptySet()));
    }

    private void toggleBookmark() {
        Set<String> bookmarks = savedBookmarks();
        String page = Integer.toString(pageIndex);
        boolean added = bookmarks.add(page);
        if (!added) bookmarks.remove(page);
        getPreferences(MODE_PRIVATE).edit()
                .putStringSet(bookmarkKey(), bookmarks).apply();
        Toast.makeText(this, added ? "Page bookmarked" : "Bookmark removed",
                Toast.LENGTH_SHORT).show();
        updatePageLabel();
    }

    private void showBookmarks() {
        ArrayList<Integer> pages = new ArrayList<>();
        for (String entry : savedBookmarks()) {
            try {
                int index = Integer.parseInt(entry);
                if (index >= 0 && index < renderer.getPageCount()) pages.add(index);
            } catch (NumberFormatException ignored) { }
        }
        Collections.sort(pages);
        if (pages.isEmpty()) {
            Toast.makeText(this, "No bookmarked pages yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[pages.size()];
        for (int i = 0; i < pages.size(); i++) labels[i] = "Page " + (pages.get(i) + 1);
        new AlertDialog.Builder(this).setTitle("Saved pages")
                .setItems(labels, (dialog, index) -> goTo(pages.get(index)))
                .setNegativeButton("Close", null).show();
    }

    private void updatePageLabel() {
        if (renderer == null || pageLabel == null) return;
        String star = savedBookmarks().contains(Integer.toString(pageIndex)) ? " ★" : "";
        pageLabel.setText("Page " + (pageIndex + 1) + " / " + renderer.getPageCount()
                + star + "     Zoom " + Math.round(zoom * 100) + "%");
    }

    private void renderPage() {
        if (renderer == null) return;
        updatePageLabel();
        try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
            float fit = (screenWidth - 16f) / page.getWidth();
            int width = Math.max(1, Math.round(page.getWidth() * fit * zoom));
            int height = Math.max(1, Math.round(page.getHeight() * fit * zoom));
            // Prevent very large allocations on low-memory devices.
            float cap = Math.min(1f, (float)Math.sqrt(16000000.0 / ((double)width * height)));
            width = Math.max(1, Math.round(width * cap));
            height = Math.max(1, Math.round(height * cap));
            Bitmap next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            next.eraseColor(Color.WHITE);
            page.render(next, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pageImage.setImageBitmap(next);
            pageImage.setLayoutParams(new android.widget.FrameLayout.LayoutParams(width, height));
            vertical.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    Math.max(screenWidth, width), -1));
            Bitmap old = currentBitmap;
            currentBitmap = next;
            if (old != null && old != next) old.recycle();
            horizontal.post(() -> { horizontal.scrollTo(0, 0); vertical.scrollTo(0, 0); });
        } catch (Exception ex) {
            Toast.makeText(this, "Page rendering failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("PDF error").setMessage(message)
            .setPositiveButton("Close", (d,w) -> finish()).setCancelable(false).show();
    }

    @Override protected void onDestroy() {
        pageImage = null;
        if (currentBitmap != null) { currentBitmap.recycle(); currentBitmap = null; }
        if (renderer != null) { renderer.close(); renderer = null; }
        if (descriptor != null) { try { descriptor.close(); } catch (IOException ignored) {} }
        super.onDestroy();
    }
}
