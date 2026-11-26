package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Home-screen arrangement controls; changes take effect when returning Home. */
public class HomeCustomizationActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF090D16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(root);

        TextView heading = heading("‹  Customize Home", 24);
        heading.setOnClickListener(v -> finish());
        root.addView(heading);
        description(root, "Choose what appears on your PWStudy home screen. Changes are saved on this device.");
        section(root, "HOME SECTIONS");
        toggle(root, "Telegram study channels", "Keep your existing Telegram access visible",
                HomePreferences.TELEGRAM, true);
        toggle(root, "Offline library", "Show your saved PDFs and videos",
                HomePreferences.LIBRARY, true);
        toggle(root, "Recent PDFs", "Display PDFs saved inside PWStudy",
                HomePreferences.RECENT, true);
        toggle(root, "Study tools shortcut", "Show a single shortcut to the dedicated Tools screen",
                HomePreferences.TOOLS, true);

        section(root, "LAYOUT & CARDS");
        toggle(root, "Grid tool layout", "Two cards per row instead of a vertical list",
                HomePreferences.GRID, true);
        toggle(root, "Compact cards", "Use smaller home actions to fit more on screen",
                HomePreferences.COMPACT, false);
        toggle(root, "Show descriptions", "Display helpful text under each tool name",
                HomePreferences.DETAILS, true);
        button(root, "Recent PDFs: choose number",
                () -> new AlertDialog.Builder(this).setTitle("Recent PDFs shown")
                        .setSingleChoiceItems(new String[]{"3 PDFs", "6 PDFs", "9 PDFs"},
                                HomePreferences.recentCount(this) == 9 ? 2
                                        : HomePreferences.recentCount(this) == 6 ? 1 : 0,
                                (dialog, which) -> {
                                    HomePreferences.setRecentCount(this, which == 2 ? 9 : which == 1 ? 6 : 3);
                                    dialog.dismiss();
                                }).setNegativeButton("Cancel", null).show());

        section(root, "MORE CUSTOMIZATION");
        button(root, "Theme, text colours & background",
                () -> startActivity(new Intent(this, CustomizationActivity.class)));
        button(root, "Customize PDF toolbar",
                () -> startActivity(new Intent(this, PdfToolbarEditorActivity.class)));
        button(root, "PDF reading settings",
                () -> startActivity(new Intent(this, SettingsActivity.class).putExtra("section", "pdf")));
        button(root, "Reset Home layout", () ->
                new AlertDialog.Builder(this).setTitle("Reset home layout?")
                        .setMessage("This only resets Home choices, not your PDF files or reading progress.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Reset", (d, which) -> {
                            HomePreferences.reset(this);
                            recreate();
                        }).show());
        button(root, "Done", this::finish);
        setContentView(scroll);
    }

    private TextView heading(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(size);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(5), dp(9), dp(5), dp(14));
        return text;
    }
    private void section(LinearLayout root, String label) {
        TextView title = heading(label, 13);
        title.setTextColor(AppAppearance.accentColor(this));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        root.addView(title, p);
    }
    private void description(LinearLayout root, String details) {
        TextView text = new TextView(this);
        text.setText(details);
        text.setTextColor(0xFFABBDD2);
        text.setTextSize(13);
        text.setPadding(dp(4), 0, dp(4), dp(15));
        root.addView(text);
    }
    private LinearLayout card(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1A2333);
        background.setCornerRadius(dp(14));
        row.setBackground(background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        root.addView(row, lp);
        return row;
    }
    private void toggle(LinearLayout root, String title, String detail, String key, boolean initial) {
        LinearLayout row = card(root);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = heading(title, 15);
        name.setPadding(0, 0, 0, 0);
        labels.addView(name);
        TextView description = new TextView(this);
        description.setText(detail);
        description.setTextColor(0xFFADBED1);
        description.setTextSize(12);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(this);
        control.setContentDescription(title);
        control.setChecked(HomePreferences.get(this, key, initial));
        control.setOnCheckedChangeListener((button, checked) -> HomePreferences.set(this, key, checked));
        row.addView(control);
        row.setOnClickListener(v -> control.setChecked(!control.isChecked()));
    }
    private void button(LinearLayout root, String label, Runnable action) {
        LinearLayout row = card(root);
        TextView text = heading(label, 15);
        text.setPadding(0, dp(4), 0, dp(4));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = heading("›", 22);
        arrow.setPadding(dp(9), 0, 0, 0);
        row.addView(arrow);
        row.setContentDescription(label);
        row.setOnClickListener(v -> action.run());
    }
}
