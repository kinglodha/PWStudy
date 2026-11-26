package com.study.pwjarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * PWStudy home: PDF-first navigation, recent saved PDFs, useful offline tools,
 * and persistent arrangement preferences. All actions link to working screens.
 */
public class MainActivity extends Activity {
    private static final int PICK_READER_PDF = 6401;
    private static final int PICK_TOOLS_PDF = 6402;
    private static final int BACKGROUND = 0xFF090D16;
    private static final int CARD = 0xFF1C2838;
    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout toolsAnchor;
    private boolean toolsFromTab;

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        renderHome();
    }

    @Override protected void onResume() {
        super.onResume();
        // Re-render preferences after returning from Settings or Customize Home.
        renderHome();
    }

    private void renderHome() {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(BACKGROUND);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17), dp(16), dp(17), dp(30));
        scroll.addView(content);
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addHeader(content);
        addHero(content);
        if (HomePreferences.get(this, HomePreferences.RECENT, true)) addRecentPdfs(content);
        if (HomePreferences.get(this, HomePreferences.TOOLS, true) || toolsFromTab) addMoreTools(content);
        addBottomNavigation(frame);

        setContentView(frame);
        AppAppearance.apply(this, content);
        if (toolsFromTab && toolsAnchor != null) {
            scroll.post(() -> scroll.smoothScrollTo(0, toolsAnchor.getTop()));
        }
    }

    private void addHeader(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("TWEAK", 26, true, Color.WHITE);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView customize = text("⚙", 26, false, Color.WHITE);
        customize.setContentDescription("Customize Home");
        customize.setGravity(Gravity.CENTER);
        row.addView(customize, new LinearLayout.LayoutParams(dp(52), dp(52)));
        customize.setOnClickListener(v -> open(HomeCustomizationActivity.class));
        TextView settings = text("⋮", 27, true, Color.WHITE);
        settings.setContentDescription("Settings");
        settings.setGravity(Gravity.CENTER);
        row.addView(settings, new LinearLayout.LayoutParams(dp(40), dp(52)));
        settings.setOnClickListener(v -> open(SettingsActivity.class));
        parent.addView(row);
        TextView description = text("Your PDFs, lectures and study tools in one place", 13, false, 0xFFAFBDD1);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.bottomMargin = dp(15);
        parent.addView(description, dp);
    }

    private void addHero(LinearLayout parent) {
        boolean telegram = HomePreferences.get(this, HomePreferences.TELEGRAM, true);
        boolean library = HomePreferences.get(this, HomePreferences.LIBRARY, true);
        addSection(parent, "STUDY SPACE", "Quick access to what you use most");
        addCard(parent, "📑  PDFs", "Open PDFs from your phone or Downloads", true,
                () -> open(PdfManagerActivity.class));
        addCard(parent, "＋  Open PDF", "Pick a PDF and start reading instantly", false,
                () -> choosePdf(PICK_READER_PDF));
        if (telegram) addCard(parent, "✈  Telegram channels", "Your study channels inside PWStudy", false,
                () -> open(TelegramViewerActivity.class));
        if (library) addCard(parent, "▣  Offline library", "Downloaded videos and PDFs saved in PWStudy", false,
                () -> open(LibraryActivity.class));
    }

    private void addRecentPdfs(LinearLayout parent) {
        addSection(parent, "RECENT PDF FILES", "PDFs saved in PWStudy's offline library");
        List<File> recent = recentPdfs();
        if (recent.isEmpty()) {
            addCard(parent, "No saved PDFs yet", "Use PDFs to select a device file, or save a PDF to the offline library", false,
                    () -> open(DeviceDocumentsActivity.class));
            return;
        }
        int maximum = Math.min(HomePreferences.recentCount(this), recent.size());
        for (int i = 0; i < maximum; i++) {
            File file = recent.get(i);
            String folder = file.getParentFile() == null ? "Offline" : file.getParentFile().getName();
            addCard(parent, "📄  " + file.getName(), folder + "  •  Saved PDF", false, () -> {
                Intent intent = new Intent(this, PdfActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
            });
        }
    }

    private List<File> recentPdfs() {
        List<File> recent = new ArrayList<>();
        File storage = getExternalFilesDir(null);
        if (storage == null) return recent;
        File base = new File(storage, "PWStudy");
        for (String folder : Arrays.asList("Physics", "Chemistry", "Biology", "Documents", "Videos")) {
            File[] files = new File(base, folder).listFiles();
            if (files == null) continue;
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    recent.add(file);
                }
            }
        }
        recent.sort((first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        return recent;
    }

    private void addMoreTools(LinearLayout parent) {
        addSection(parent, "MORE TOOLS", "Reader, organizing and customization shortcuts");
        toolsAnchor = new LinearLayout(this);
        toolsAnchor.setOrientation(LinearLayout.VERTICAL);
        parent.addView(toolsAnchor);
        boolean grid = HomePreferences.get(this, HomePreferences.GRID, true);
        String[][] features = {
                {"⌕  Search PDF", "Find text inside a PDF"},
                {"★  Bookmarks & notes", "Save notes and return to pages"},
                {"▤  Extract pages", "Make a new PDF from selected pages"},
                {"⇄  Merge PDFs", "Combine files into a new PDF"},
                {"☷  PDF toolbar", "Reorder, hide and style reader controls"},
                {"◐  Reader settings", "Zoom, page fitting, theme and navigation"},
                {"🎨  Appearance", "Background image and accent colours"},
                {"⊞  Customize Home", "Change home cards, layout and shortcuts"},
                {"📂  Device PDFs", "Browse PDFs in phone folders"},
                {"▶  Video library", "Access saved lecture videos"},
                {"PDF organizer", "Offline filename review and duplicate audit"},
                {"Study planner", "Tasks, deadlines and revision checklist"},
                {"Flashcards", "Offline question-answer decks and spaced review"}
        };
        Runnable[] actions = new Runnable[] {
                () -> choosePdf(PICK_TOOLS_PDF), () -> choosePdf(PICK_TOOLS_PDF),
                () -> choosePdf(PICK_TOOLS_PDF), () -> open(LibraryActivity.class),
                () -> open(PdfToolbarEditorActivity.class), this::openReaderSettings,
                () -> open(CustomizationActivity.class), () -> open(HomeCustomizationActivity.class),
                () -> open(DeviceDocumentsActivity.class), () -> open(LibraryActivity.class),
                () -> open(PdfOrganizerActivity.class),
                () -> open(StudyPlannerActivity.class), () -> open(FlashcardsActivity.class)
        };
        if (!grid) {
            for (int i = 0; i < features.length; i++) {
                addCard(toolsAnchor, features[i][0], features[i][1], false, actions[i]);
            }
        } else {
            for (int i = 0; i < features.length; i += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.bottomMargin = dp(8);
                toolsAnchor.addView(row, rowParams);
                addGridCard(row, features[i][0], features[i][1], actions[i], false);
                if (i + 1 < features.length)
                    addGridCard(row, features[i + 1][0], features[i + 1][1], actions[i + 1], true);
            }
        }
        TextView hint = text("Search, notes and extraction open after choosing a PDF. Merge saved PDFs from Offline Library.", 12,
                false, 0xFF9EADC2);
        hint.setPadding(dp(6), dp(8), dp(6), dp(12));
        toolsAnchor.addView(hint);
    }

    private void addGridCard(LinearLayout parent, String title, String detail, Runnable action, boolean second) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (second) params.leftMargin = dp(8);
        LinearLayout card = card(title, detail, false, action);
        card.setMinimumHeight(dp(HomePreferences.get(this, HomePreferences.COMPACT, false) ? 83 : 110));
        parent.addView(card, params);
    }

    private void addCard(LinearLayout parent, String title, String detail, boolean prominent, Runnable action) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        parent.addView(card(title, detail, prominent, action), params);
    }

    private LinearLayout card(String title, String detail, boolean prominent, Runnable action) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        boolean compact = HomePreferences.get(this, HomePreferences.COMPACT, false);
        wrapper.setPadding(dp(14), dp(compact ? 10 : 14), dp(13), dp(compact ? 10 : 14));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                prominent ? new int[]{AppAppearance.accentColor(this), 0xFF193C71}
                        : new int[]{CARD, 0xFF152132});
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), prominent ? 0xFF77A7E6 : 0xFF334760);
        wrapper.setBackground(background);
        TextView name = text(title, compact ? 15 : 16, true, Color.WHITE);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        wrapper.addView(name);
        if (HomePreferences.get(this, HomePreferences.DETAILS, true) && detail != null) {
            TextView subtitle = text(detail, 12, false, 0xFFD0D8E5);
            subtitle.setPadding(0, dp(4), 0, 0);
            subtitle.setMaxLines(3);
            wrapper.addView(subtitle);
        }
        wrapper.setContentDescription(title + ". " + detail);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setOnClickListener(v -> action.run());
        return wrapper;
    }

    private void addSection(LinearLayout parent, String label, String detail) {
        TextView heading = text(label, 16, true, Color.WHITE);
        LinearLayout.LayoutParams header = new LinearLayout.LayoutParams(-1, -2);
        header.topMargin = dp(12);
        parent.addView(heading, header);
        TextView secondary = text(detail, 12, false, 0xFF9AACC4);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
        sub.bottomMargin = dp(10);
        parent.addView(secondary, sub);
    }

    private TextView text(String content, int size, boolean bold, int colour) {
        TextView label = new TextView(this);
        label.setText(content);
        label.setTextSize(size);
        label.setTextColor(colour);
        label.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private void addBottomNavigation(LinearLayout parent) {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setBackgroundColor(0xFF1A202D);
        addTab(navigation, "⌂\nHome", () -> { toolsFromTab = false; scroll.smoothScrollTo(0, 0); });
        addTab(navigation, "▣\nPDFs", () -> open(PdfManagerActivity.class));
        addTab(navigation, "⊞\nTools", () -> {
            toolsFromTab = true;
            renderHome();
        });
        addTab(navigation, "▤\nLibrary", () -> open(LibraryActivity.class));
        addTab(navigation, "⚙\nSettings", () -> open(SettingsActivity.class));
        parent.addView(navigation, new LinearLayout.LayoutParams(-1, dp(66)));
    }

    private void addTab(LinearLayout row, String title, Runnable action) {
        TextView tab = text(title, 12, false, Color.WHITE);
        tab.setGravity(Gravity.CENTER);
        tab.setContentDescription(title.replace("\n", " "));
        row.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
        tab.setOnClickListener(v -> action.run());
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }

    private void openReaderSettings() {
        startActivity(new Intent(this, SettingsActivity.class).putExtra("section", "pdf"));
    }

    private void choosePdf(int request) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("application/pdf");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(pick, request); }
        catch (Exception e) { Toast.makeText(this, "No PDF file picker available", Toast.LENGTH_LONG).show(); }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request != PICK_READER_PDF && request != PICK_TOOLS_PDF) return;
        Uri uri = data.getData();
        try {
            int grants = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (grants != 0) getContentResolver().takePersistableUriPermission(uri, grants);
        } catch (SecurityException ignored) {
            // The transient read grant still allows opening the selected PDF.
        }
        if (request == PICK_READER_PDF) {
            Intent read = new Intent(this, PdfActivity.class);
            read.setData(uri);
            read.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(read);
            return;
        }
        try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (fd == null) throw new IllegalStateException("Cannot read selected PDF");
            try (PdfRenderer renderer = new PdfRenderer(fd)) {
                int count = renderer.getPageCount();
                if (count == 0) throw new IllegalStateException("PDF contains no pages");
                Intent tools = new Intent(this, PdfStudyToolsActivity.class);
                tools.putExtra("document_id", uri.toString());
                tools.putExtra("page_index", 0);
                tools.putExtra("page_count", count);
                tools.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(tools);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open PDF tools: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
