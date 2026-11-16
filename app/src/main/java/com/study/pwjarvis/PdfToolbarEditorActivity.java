package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** User-editable position, visibility, label, size and outline color of EVERY PDF control. */
public class PdfToolbarEditorActivity extends Activity {
    private ScrollView scroll;
    private LinearLayout list;
    private int dp(int size) { return (int) (size * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF0A101B);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(30));
        scroll.addView(root);
        TextView title = text("‹  PDF toolbar customization", 22, Color.WHITE, true);
        title.setPadding(dp(4), dp(3), 0, dp(12));
        title.setOnClickListener(v -> finish());
        root.addView(title);
        TextView help = text("Long-press a control and drag it onto another to change its place. Or use ↑ ↓ and Edit. All changes save automatically.", 13, 0xFFB7C5DA, false);
        root.addView(help);
        Button reset = button("Reset all buttons");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reset all PDF buttons?")
                .setMessage("Restore original positions, labels, sizes, colors and visibility.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    PdfToolbarLayout.resetAll(this);
                    refresh();
                }).show());
        root.addView(reset);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        setContentView(scroll);
        refresh();
    }

    private TextView text(String label, int textSize, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(textSize);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setMinWidth(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF27364F);
        bg.setCornerRadius(dp(10));
        button.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(43));
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        button.setLayoutParams(p);
        return button;
    }
    private void refresh() {
        int savedY = scroll.getScrollY();
        list.removeAllViews();
        for (int position = 0; position < PdfToolbarLayout.PLACES.length; position++) {
            List<String> ids = PdfToolbarLayout.items(this, position, true);
            TextView header = text(PdfToolbarLayout.PLACES[position] + "  •  " + ids.size(), 16,
                    0xFF80B7F3, true);
            header.setPadding(dp(2), dp(22), 0, dp(10));
            list.addView(header);
            if (ids.isEmpty()) {
                TextView empty = text("Empty — use Edit on a button to move it here", 12, 0xFF8F9BAE, false);
                list.addView(empty);
            }
            for (String id : ids) addEditorRow(id);
        }
        scroll.post(() -> scroll.scrollTo(0, savedY));
    }
    private void addEditorRow(String id) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF172233);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), PdfToolbarLayout.accent(this, id));
        card.setBackground(background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(9));
        list.addView(card, cardParams);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("≡  " + PdfToolbarLayout.name(id) + "  [" + PdfToolbarLayout.label(this, id) + "]",
                14, Color.WHITE, true);
        label.setSingleLine(false);
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Switch visible = new Switch(this);
        visible.setChecked(PdfToolbarLayout.visible(this, id));
        visible.setContentDescription("Show " + PdfToolbarLayout.name(id));
        visible.setOnCheckedChangeListener((b, checked) -> PdfToolbarLayout.setVisible(this, id, checked));
        heading.addView(visible);
        card.addView(heading);
        TextView hint = text("Hold and drag to move this control", 11, 0xFF9CACBF, false);
        card.addView(hint);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button edit = button("Edit");
        Button up = button("↑");
        Button down = button("↓");
        Button restore = button("Reset");
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 2));
        actions.addView(up, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(down, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(restore, new LinearLayout.LayoutParams(0, dp(44), 2));
        card.addView(actions);
        edit.setOnClickListener(v -> editControl(id));
        up.setOnClickListener(v -> { PdfToolbarLayout.move(this, id, -1); refresh(); });
        down.setOnClickListener(v -> { PdfToolbarLayout.move(this, id, 1); refresh(); });
        restore.setOnClickListener(v -> { PdfToolbarLayout.resetOne(this, id); refresh(); });

        card.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText("pdf_control", id);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(card);
            if (Build.VERSION.SDK_INT >= 24) card.startDragAndDrop(data, shadow, id, 0);
            else card.startDrag(data, shadow, id, 0);
            return true;
        });
        card.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getLocalState() instanceof String;
                case DragEvent.ACTION_DRAG_ENTERED:
                    card.setAlpha(.6f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    card.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    card.setAlpha(1f);
                    Object from = event.getLocalState();
                    if (from instanceof String) {
                        PdfToolbarLayout.placeBefore(this, (String) from, id);
                        refresh();
                        return true;
                    }
                    return false;
                case DragEvent.ACTION_DRAG_ENDED:
                    card.setAlpha(1f);
                    return true;
                default:
                    return true;
            }
        });
    }
    private Spinner spinner(String[] options, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        return spinner;
    }
    private void addField(LinearLayout panel, String name, View field) {
        TextView label = text(name, 13, 0xFF294063, true);
        label.setPadding(0, dp(10), 0, dp(3));
        panel.addView(label);
        panel.addView(field, new LinearLayout.LayoutParams(-1, dp(46)));
    }
    private void editControl(String id) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText(PdfToolbarLayout.label(this, id));
        name.setSelectAllOnFocus(true);
        addField(panel, "Button label (up to 20 characters)", name);
        Spinner place = spinner(PdfToolbarLayout.PLACES, PdfToolbarLayout.slot(this, id));
        addField(panel, "Move button to", place);
        Spinner size = spinner(PdfToolbarLayout.SIZES, PdfToolbarLayout.size(this, id));
        addField(panel, "Button size", size);
        Spinner color = spinner(PdfToolbarLayout.COLORS, PdfToolbarLayout.colorIndex(this, id));
        addField(panel, "Button border color", color);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(panel);
        new AlertDialog.Builder(this)
                .setTitle(PdfToolbarLayout.name(id))
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, selected) -> {
                    PdfToolbarLayout.setLabel(this, id, name.getText().toString());
                    PdfToolbarLayout.setSlot(this, id, place.getSelectedItemPosition());
                    PdfToolbarLayout.setSize(this, id, size.getSelectedItemPosition());
                    PdfToolbarLayout.setColorIndex(this, id, color.getSelectedItemPosition());
                    refresh();
                    Toast.makeText(this, "PDF button saved", Toast.LENGTH_SHORT).show();
                }).show();
    }
}
