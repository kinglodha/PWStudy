package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stored, per-control PDF toolbar layout. Does not modify PDF reading preferences. */
public final class PdfToolbarLayout {
    private PdfToolbarLayout() {}
    private static final String PREFS = "pwstudy_pdf_toolbar_layout";
    public static final String[] IDS = {
            "back", "settings", "zoom_out", "zoom_reset", "zoom_in",
            "fit_width", "fit_page", "rotate", "go_to", "full", "theme",
            "previous", "page_indicator", "next", "page_slider"
    };
    public static final String[] PLACES = {
            "Top bar", "Bottom row 1", "Bottom row 2", "Bottom row 3", "Bottom row 4"
    };
    public static final String[] SIZES = {"Small", "Normal", "Large"};
    public static final String[] COLORS = {"Original", "Blue", "Green", "Orange", "Pink", "Cyan"};

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public static String defaultLabel(String id) {
        switch (id) {
            case "back": return "‹";
            case "settings": return "⚙ Settings";
            case "zoom_out": return "−";
            case "zoom_reset": return "100%";
            case "zoom_in": return "+";
            case "fit_width": return "Fit Width";
            case "fit_page": return "Fit Page";
            case "rotate": return "Rotate";
            case "go_to": return "Go to";
            case "full": return "Full";
            case "theme": return "Theme";
            case "previous": return "Previous";
            case "page_indicator": return "Page indicator";
            case "next": return "Next";
            case "page_slider": return "Page slider";
            default: return id;
        }
    }
    public static String name(String id) {
        if ("back".equals(id)) return "Back";
        if ("zoom_out".equals(id)) return "Zoom out";
        if ("zoom_in".equals(id)) return "Zoom in";
        if ("zoom_reset".equals(id)) return "Reset zoom";
        if ("full".equals(id)) return "Fullscreen";
        return defaultLabel(id);
    }
    private static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }
    private static int defaultSlot(String id) {
        int index = indexOf(id);
        if (index <= 1) return 0;
        if (index <= 6) return 1;
        if (index <= 10) return 2;
        if (index <= 13) return 3;
        return 4;
    }
    public static int slot(Context c, String id) {
        int value = prefs(c).getInt("slot_" + id, defaultSlot(id));
        return Math.max(0, Math.min(4, value));
    }
    public static int order(Context c, String id) {
        return prefs(c).getInt("order_" + id, indexOf(id) * 10);
    }
    public static boolean visible(Context c, String id) {
        return prefs(c).getBoolean("visible_" + id, true);
    }
    public static void setVisible(Context c, String id, boolean value) {
        prefs(c).edit().putBoolean("visible_" + id, value).apply();
    }
    public static String label(Context c, String id) {
        return prefs(c).getString("label_" + id, defaultLabel(id));
    }
    public static void setLabel(Context c, String id, String text) {
        String value = text == null ? "" : text.trim().replace('\n', ' ');
        if (value.length() > 20) value = value.substring(0, 20);
        if (value.isEmpty()) prefs(c).edit().remove("label_" + id).apply();
        else prefs(c).edit().putString("label_" + id, value).apply();
    }
    public static int size(Context c, String id) {
        return Math.max(0, Math.min(2, prefs(c).getInt("size_" + id, 1)));
    }
    public static void setSize(Context c, String id, int size) {
        prefs(c).edit().putInt("size_" + id, Math.max(0, Math.min(2, size))).apply();
    }
    public static int colorIndex(Context c, String id) {
        return Math.max(0, Math.min(5, prefs(c).getInt("color_" + id, 0)));
    }
    public static void setColorIndex(Context c, String id, int value) {
        prefs(c).edit().putInt("color_" + id, Math.max(0, Math.min(5, value))).apply();
    }
    public static int accent(Context c, String id) {
        switch (colorIndex(c, id)) {
            case 1: return Color.rgb(72, 149, 239);
            case 2: return Color.rgb(62, 181, 131);
            case 3: return Color.rgb(239, 165, 64);
            case 4: return Color.rgb(213, 98, 175);
            case 5: return Color.rgb(61, 188, 209);
            default: return Color.rgb(85, 130, 180);
        }
    }
    /** Ordered items, including hidden ones when requested by the editor. */
    public static List<String> items(Context c, int inSlot, boolean includeHidden) {
        List<String> ids = new ArrayList<>();
        for (String id : IDS) {
            if (slot(c, id) == inSlot && (includeHidden || visible(c, id))) ids.add(id);
        }
        Collections.sort(ids, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int byOrder = Integer.compare(order(c, a), order(c, b));
                return byOrder != 0 ? byOrder : Integer.compare(indexOf(a), indexOf(b));
            }
        });
        return ids;
    }
    public static void setSlot(Context c, String id, int targetSlot) {
        int place = Math.max(0, Math.min(4, targetSlot));
        if (slot(c, id) == place) return;
        List<String> target = items(c, place, true);
        SharedPreferences.Editor editor = prefs(c).edit();
        editor.putInt("slot_" + id, place).putInt("order_" + id, target.size() * 10 + 10);
        editor.apply();
    }
    public static void move(Context c, String id, int delta) {
        List<String> row = items(c, slot(c, id), true);
        int from = row.indexOf(id);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= row.size()) return;
        Collections.swap(row, from, to);
        saveOrder(c, row);
    }
    /** Drag a control onto another; supports cross-row moves too. */
    public static void placeBefore(Context c, String draggedId, String targetId) {
        if (draggedId.equals(targetId) || indexOf(draggedId) < 0 || indexOf(targetId) < 0) return;
        int destination = slot(c, targetId);
        List<String> row = items(c, destination, true);
        row.remove(draggedId);
        int to = row.indexOf(targetId);
        row.add(Math.max(0, to), draggedId);
        SharedPreferences.Editor editor = prefs(c).edit();
        editor.putInt("slot_" + draggedId, destination);
        for (int i = 0; i < row.size(); i++) editor.putInt("order_" + row.get(i), i * 10);
        editor.apply();
    }
    private static void saveOrder(Context c, List<String> row) {
        SharedPreferences.Editor editor = prefs(c).edit();
        for (int i = 0; i < row.size(); i++) editor.putInt("order_" + row.get(i), i * 10);
        editor.apply();
    }
    public static void resetOne(Context c, String id) {
        prefs(c).edit().remove("slot_" + id).remove("order_" + id)
                .remove("visible_" + id).remove("label_" + id)
                .remove("size_" + id).remove("color_" + id).apply();
    }
    public static void resetAll(Context c) {
        prefs(c).edit().clear().apply();
    }
}
