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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/** App settings and reader settings, accessible from the home and PDF toolbar. */
public class SettingsActivity extends Activity {
    private final int background = Color.rgb(8, 12, 20);
    private final int card = Color.rgb(22, 30, 44);
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
        root.addView(header);
        addText(root, "Make PWStudy comfortable for long study sessions.", 13, 0xFFADB8C8);

        addHeading(root, "APP");
        addAction(root, "Appearance & colours", "Background image, text and accent colours",
                () -> startActivity(new Intent(this, CustomizationActivity.class)));
        addAction(root, "Offline library", "Manage saved PDFs and videos",
                () -> startActivity(new Intent(this, LibraryActivity.class)));

        LinearLayout pdfSection = new LinearLayout(this);
        pdfSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(pdfSection);
        addHeading(pdfSection, "PDF VIEWER");
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
                new String[]{"Never hide", "After 4 seconds", "After 8 seconds"},
                delay == 0 ? 0 : delay == 8 ? 2 : 1,
                which -> PdfReaderSettings.setControlsDelay(this, which == 0 ? 0 : which == 2 ? 8 : 4));
        addAction(pdfSection, "Reset PDF preferences", "Restore original reading defaults", () ->
                new AlertDialog.Builder(this).setTitle("Reset PDF settings?")
                        .setMessage("Reader themes, controls and saved reading positions will be reset.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Reset", (dialog, which) -> {
                            PdfReaderSettings.reset(this);
                            recreate();
                        }).show());

        setContentView(scroll);
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
        result.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        parent.addView(result, p);
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
        textColumn(r, heading, detail);
        Switch s = new Switch(this);
        s.setChecked(current);
        s.setContentDescription(heading);
        s.setOnCheckedChangeListener((button, checked) -> action.change(checked));
        r.addView(s);
        r.setOnClickListener(v -> s.setChecked(!s.isChecked()));
    }
    private interface OnChoice { void choose(int position); }
    private void addPicker(LinearLayout parent, String heading, String[] choices, int selected, OnChoice action) {
        LinearLayout r = row(parent);
        TextView name = title(heading, 14, Color.WHITE);
        r.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, choices) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView label = (TextView) super.getView(position, convertView, parent);
                label.setTextColor(Color.WHITE);
                label.setTextSize(13);
                return label;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView label = (TextView) super.getDropDownView(position, convertView, parent);
                label.setTextColor(Color.WHITE);
                label.setBackgroundColor(card);
                label.setPadding(dp(10), dp(8), dp(6), dp(8));
                return label;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                action.choose(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        r.addView(spinner, new LinearLayout.LayoutParams(dp(155), dp(48)));
    }
}
