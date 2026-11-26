package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.core.view.WindowCompat;
import java.util.Arrays;

/** Shared UI for optional offline study tools; does not restyle existing screens. */
public abstract class StudyToolActivity extends Activity {
    protected StudyDatabase store;
    protected LinearLayout root;
    protected LinearLayout content;
    protected static final int BACKGROUND = 0xff10151e;
    protected static final int SURFACE = 0xff202b3c;
    protected static final int SECONDARY = 0xffb5c5db;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        store = new StudyDatabase(this);
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected void screen(String title, String description) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout heading = row();
        heading.addView(button("‹", this::onBackPressed), new LinearLayout.LayoutParams(dp(48), dp(48)));
        heading.addView(text(title, 22, true), new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(heading);
        if (description != null) root.addView(text(description, 13, false));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    protected LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    protected TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(bold ? Color.WHITE : SECONDARY);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        return view;
    }

    protected Button button(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    protected void addPair(LinearLayout parent, String first, Runnable a, String second, Runnable b) {
        LinearLayout row = row();
        row.addView(button(first, a), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(button(second, b), new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row);
    }

    protected LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(SURFACE);
        background.setCornerRadius(dp(12));
        box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(box, params);
        return box;
    }

    protected EditText input(String hint, String value, boolean singleLine) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(singleLine);
        field.setText(value == null ? "" : value);
        if (!singleLine) field.setMinLines(3);
        return field;
    }

    protected Spinner subjects(String initial) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, StudyDatabase.SUBJECTS);
        spinner.setAdapter(adapter);
        int index = Arrays.asList(StudyDatabase.SUBJECTS).indexOf(initial);
        spinner.setSelection(Math.max(0, index));
        return spinner;
    }

    protected LinearLayout dialogBody() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        return form;
    }

    protected View dialogScroll(LinearLayout body) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        return scroll;
    }

    protected void confirm(String title, String message, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (d,w) -> action.run()).show();
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }
}
