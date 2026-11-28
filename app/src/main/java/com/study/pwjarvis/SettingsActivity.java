package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import java.util.ArrayList;
import java.util.Locale;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;
import android.widget.Switch;
import android.widget.TextView;

/** App settings and reader settings, accessible from the home and PDF toolbar. */
public class SettingsActivity extends Activity {
    private final int background = Color.rgb(8, 12, 20);
    private final int card = Color.rgb(22, 30, 44);
    private final ArrayList<View> searchRows = new ArrayList<>();
    private static final int EXPORT_READER = 401, IMPORT_READER = 402;
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(32));
        scroll.addView(root);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(background);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹");
        back.setContentDescription("Go back");
        back.setAllCaps(false);
        back.setTextSize(26);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        back.setOnClickListener(v -> finish());
        TextView heading = title("Settings", 24, Color.WHITE);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1));
        screen.addView(header);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFFADB8C8);
        search.setHint("Search settings…");
        search.setContentDescription("Search settings");
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
        searchParams.setMargins(dp(18), 0, dp(18), dp(6));
        screen.addView(search, searchParams);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.setFocusableInTouchMode(true);
        screen.requestFocus();
        addText(root, "Make Tweak comfortable for long study sessions.", 13, 0xFFADB8C8);

        addHeading(root, "APP");
        addAction(root, "App icon", "Choose the Tweak launcher icon", this::chooseAppIcon);
        addPicker(root, "Telegram rotation button position", new String[]{"Bottom left", "Bottom right"},
                getSharedPreferences("telegram_controls", MODE_PRIVATE).getInt("rotation_position", 0),
                which -> getSharedPreferences("telegram_controls", MODE_PRIVATE).edit().putInt("rotation_position", which).apply());
        addAction(root, "Study presets", "Exam mode, Night owl and Battery saver reader defaults", this::showPresets);
        addAction(root, "Customize Home", "Show or hide cards, grid layout, recent PDFs and quick tools",
                () -> startActivity(new Intent(this, HomeCustomizationActivity.class)));
        addAction(root, "Appearance & colours", "Background image, text and accent colours",
                () -> startActivity(new Intent(this, CustomizationActivity.class)));
        addAction(root, "PDFs on this device", "Open files from Downloads and folders with the built-in viewer",
                () -> startActivity(new Intent(this, DeviceDocumentsActivity.class)));
        addAction(root, "Offline library", "Manage saved PDFs and videos",
                () -> startActivity(new Intent(this, LibraryActivity.class)));

        LinearLayout pdfSection = new LinearLayout(this);
        pdfSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(pdfSection);
        addHeading(pdfSection, "PDF VIEWER");
        addText(pdfSection, "In an open PDF, tap PDF Tools for offline search, bookmarks, notes, read-aloud, page extraction and merge.", 12, 0xFFADB8C8);
        addAction(pdfSection, "Open PDFs", "Pick a PDF to read and access its advanced tools",
                () -> startActivity(new Intent(this, DeviceDocumentsActivity.class)));
        addAction(pdfSection, "Customize every PDF button",
                "Drag to reorder; move between rows; change size, text, color or visibility",
                () -> startActivity(new Intent(this, PdfToolbarEditorActivity.class)));
        addText(pdfSection, "These preferences are saved and apply to PDFs opened from Telegram, the library or device.", 12, 0xFFADB8C8);

        addPicker(pdfSection, "Reading theme", new String[]{"Normal", "Sepia", "Night / inverted"},
                PdfReaderSettings.theme(this), which -> PdfReaderSettings.setTheme(this, which));
        addSwitch(pdfSection, "Fit page width", "Start pages at full screen width; pan vertically to read",
                PdfReaderSettings.fitWidth(this), value -> PdfReaderSettings.setFitWidth(this, value));
        addSwitch(pdfSection, "Remember last page", "Resume where you stopped reading a PDF",
                PdfReaderSettings.rememberPage(this), value -> PdfReaderSettings.setRememberPage(this, value));
        addSwitch(pdfSection, "Swipe to change pages", "Change page only when the PDF is not being panned",
                PdfReaderSettings.swipeNavigation(this), value -> PdfReaderSettings.setSwipeNavigation(this, value));
        addSwitch(pdfSection, "Keep screen awake", "Prevent screen dimming while the viewer is open",
                PdfReaderSettings.keepAwake(this), value -> PdfReaderSettings.setKeepAwake(this, value));
        int delay = PdfReaderSettings.controlsDelay(this);
        addPicker(pdfSection, "Toolbar auto-hide",
                new String[]{"Never hide", "2 sec", "4 sec", "6 sec", "8 sec", "10 sec"},
                delay == 0 ? 0 : delay / 2,
                which -> PdfReaderSettings.setControlsDelay(this, which * 2));
        addAction(pdfSection, "Reset PDF preferences", "Restore original reading defaults", () ->
                new AlertDialog.Builder(this).setTitle("Reset PDF settings?")
                        .setMessage("Reader themes, controls and saved reading positions will be reset.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Reset", (dialog, which) -> {
                            PdfReaderSettings.reset(this);
                            recreate();
                        }).show());

        addAction(pdfSection, "Reset reader defaults (keep progress)", "Restore reader options without clearing saved pages",
                () -> new AlertDialog.Builder(this).setTitle("Reset reader defaults?")
                    .setMessage("Saved reading positions, files and toolbar layouts are kept.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset", (d, w) -> { PdfReaderSettings.resetDefaults(this); recreate(); }).show());
        addHeading(root, "BACKUP");
        addAction(root, "Export reader settings", "Save a local JSON backup of reading preferences", () -> chooseBackup(true));
        addAction(root, "Import reader settings", "Restore reader preferences from a JSON backup", () -> chooseBackup(false));
        addText(root, "Reader backups do not contain PDFs, account data, saved pages or toolbar layouts.", 12, 0xFFADB8C8);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                String[] tokens = value.toString().trim().toLowerCase(Locale.ROOT).split("\\s+");
                for (View row : searchRows) {
                    String terms = String.valueOf(row.getTag()).toLowerCase(Locale.ROOT);
                    boolean matches = true;
                    for (String token : tokens) if (!terms.contains(token)) { matches = false; break; }
                    row.setVisibility(matches ? View.VISIBLE : View.GONE);
                }
                scroll.scrollTo(0, 0);
            }
            public void afterTextChanged(Editable value) {}
        });
        setContentView(screen);
        if ("pdf".equals(getIntent().getStringExtra("section"))) {
            scroll.post(() -> scroll.smoothScrollTo(0, pdfSection.getTop()));
        }
    }
    private TextView title(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private void addText(LinearLayout parent, String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(5), dp(4), dp(5), dp(12));
        parent.addView(t);
    }
    private void addHeading(LinearLayout parent, String text) {
        TextView t = title(text, 13, AppAppearance.accentColor(this));
        t.setPadding(dp(5), dp(18), 0, dp(8));
        parent.addView(t);
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0xFF263244);
        result.setMinimumHeight(dp(48));
        result.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        parent.addView(result, p);
        searchRows.add(result);
        return result;
    }
    private LinearLayout textColumn(LinearLayout row, String heading, String explanation) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView name = title(heading, 15, Color.WHITE);
        column.addView(name);
        if (explanation != null) {
            TextView details = new TextView(this);
            details.setText(explanation);
            details.setTextSize(12);
            details.setTextColor(0xFFADB8C8);
            details.setPadding(0, dp(4), 0, 0);
            column.addView(details);
        }
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        return column;
    }
    private void addAction(LinearLayout parent, String heading, String detail, Runnable action) {
        LinearLayout r = row(parent);
        r.setTag(heading + " " + detail);
        textColumn(r, heading, detail);
        TextView chevron = title("›", 26, 0xFF91AACE);
        chevron.setPadding(dp(12), 0, 0, 0);
        r.addView(chevron);
        r.setOnClickListener(v -> action.run());
        r.setContentDescription(heading);
    }
    private interface OnToggle { void change(boolean v); }
    private void addSwitch(LinearLayout parent, String heading, String detail, boolean current, OnToggle action) {
        LinearLayout r = row(parent);
        r.setTag(heading + " " + detail);
        textColumn(r, heading, detail);
        Switch s = new Switch(this);
        s.setChecked(current);
        s.setMinHeight(dp(48));
        s.setContentDescription(heading);
        s.setOnCheckedChangeListener((button, checked) -> action.change(checked));
        r.addView(s);
        r.setOnClickListener(v -> s.setChecked(!s.isChecked()));
    }
    private interface OnChoice { void choose(int position); }
    private void addPicker(LinearLayout parent, String heading, String[] choices, int selected, OnChoice action) {
        LinearLayout r = row(parent);
        r.setTag(heading + " " + android.text.TextUtils.join(" ", choices)
            + ("Reading theme".equals(heading) ? " dark mode night eye comfort" : " toolbar timeout hide"));
        LinearLayout column = textColumn(r, heading, null);
        final int[] current = {Math.max(0, Math.min(selected, choices.length - 1))};
        TextView active = title(choices[current[0]], 14, 0xFFE2E8F0);
        active.setPadding(0, dp(5), 0, 0);
        column.addView(active);
        TextView chevron = title("⌄", 24, 0xFF8E9BAE);
        chevron.setPadding(dp(12), 0, dp(4), 0);
        r.addView(chevron);
        r.setContentDescription(heading + ": " + choices[current[0]]);
        r.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(heading)
            .setSingleChoiceItems(choices, current[0], (dialog, position) -> {
                current[0] = position;
                action.choose(position);
                active.setText(choices[position]);
                r.setContentDescription(heading + ": " + choices[position]);
                dialog.dismiss();
            }).setNegativeButton("Cancel", null).show());
    }

    private void chooseAppIcon() {
        String[] labels = {"Original Tweak", "Minimal infinity"};
        android.content.pm.PackageManager pm = getPackageManager();
        android.content.ComponentName[] icons = {
            new android.content.ComponentName(this, "com.study.pwjarvis.TweakDefaultLauncher"),
            new android.content.ComponentName(this, "com.study.pwjarvis.TweakInfinityLauncher")
        };
        int selected = pm.getComponentEnabledSetting(icons[1]) ==
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ? 1 : 0;
        new AlertDialog.Builder(this).setTitle("App icon")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    if (which == selected) { dialog.dismiss(); return; }
                    try {
                        int enabled = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                        int disabled = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                        int flags = android.content.pm.PackageManager.DONT_KILL_APP;
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            pm.setComponentEnabledSettings(java.util.Arrays.asList(
                                new android.content.pm.PackageManager.ComponentEnabledSetting(icons[which], enabled, flags),
                                new android.content.pm.PackageManager.ComponentEnabledSetting(icons[1 - which], disabled, flags)));
                        } else {
                            pm.setComponentEnabledSetting(icons[which], enabled, flags);
                            pm.setComponentEnabledSetting(icons[1 - which], disabled, flags);
                        }
                        notifyUser("App icon changed. Your launcher may take a moment to refresh.");
                    } catch (Exception e) {
                        notifyUser("Could not change the launcher icon.");
                    }
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showPresets() {
        String[] names = {"Exam mode", "Night owl", "Battery saver"};
        String[] details = {
            "Normal theme, fit width, remember page, swipe navigation, keep awake, controls stay visible.",
            "Night/inverted theme, fit width, remember page, swipe navigation, normal screen timeout, 4 sec toolbar.",
            "Night/inverted theme, fit width, remember page, swipe navigation, normal screen timeout, 2 sec toolbar."
        };
        new AlertDialog.Builder(this).setTitle("Reader presets").setItems(names, (d, i) ->
            new AlertDialog.Builder(this).setTitle("Apply " + names[i] + "?")
                .setMessage(details[i] + "\n\nYour files, home layout and saved reading positions remain untouched. Battery savings depend on your display and device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    PdfReaderSettings.applyDefaults(this, i == 0 ? 0 : 2, true, true, true,
                        i == 0, i == 0 ? 0 : i == 1 ? 4 : 2);
                    recreate();
                }).show()).setNegativeButton("Cancel", null).show();
    }

    private void chooseBackup(boolean export) {
        Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(export ? "application/json" : "*/*");
        if (export) intent.putExtra(Intent.EXTRA_TITLE, "tweak-reader-settings.json");
        try { startActivityForResult(intent, export ? EXPORT_READER : IMPORT_READER); }
        catch (android.content.ActivityNotFoundException e) { notifyUser("Document picker unavailable."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == EXPORT_READER) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (out == null) throw new java.io.IOException("Cannot open destination");
                out.write(PdfReaderSettings.exportDefaults(this).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
                notifyUser("Reader preferences exported.");
            } catch (Exception e) { notifyUser("Could not export reader preferences."); }
        } else if (request == IMPORT_READER) {
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                if (in == null) throw new java.io.IOException("Cannot open backup");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (bytes.size() + n > 65536) throw new java.io.IOException("Backup is too large");
                    bytes.write(buffer, 0, n);
                }
                JSONObject settings = new JSONObject(bytes.toString("UTF-8"));
                PdfReaderSettings.validateDefaults(settings);
                new AlertDialog.Builder(this).setTitle("Restore reader preferences?")
                    .setMessage("Only the reader defaults will change. Files, saved pages and other settings are kept.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (d, w) -> {
                        try {
                            PdfReaderSettings.importDefaults(this, settings);
                            notifyUser("Reader preferences restored.");
                            recreate();
                        } catch (Exception e) { notifyUser("Invalid reader backup."); }
                    }).show();
            } catch (Exception e) { notifyUser("Invalid or unreadable reader backup. No settings changed."); }
        }
    }

    private void notifyUser(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
}
