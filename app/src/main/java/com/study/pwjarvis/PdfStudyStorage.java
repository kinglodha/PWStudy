package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PWStudy-local bookmarks and plain-text page notes (not embedded into the source PDF). */
public final class PdfStudyStorage {
    private static final String PREFS = "pwstudy_study_pdf_metadata_v1";
    private PdfStudyStorage() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static String key(String document) {
        return Integer.toHexString(document == null ? 0 : document.hashCode())
                + "_" + (document == null ? 0 : document.length());
    }
    private static String bookmarksKey(String document) {
        return "bookmarks_" + key(document);
    }
    public static List<Integer> bookmarks(Context c, String document) {
        List<Integer> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString(bookmarksKey(document), "[]"));
            for (int i = 0; i < array.length(); i++) {
                int page = array.optInt(i, -1);
                if (page >= 0 && !result.contains(page)) result.add(page);
            }
        } catch (Exception ignored) {}
        Collections.sort(result);
        return result;
    }
    public static boolean isBookmarked(Context c, String document, int page) {
        return bookmarks(c, document).contains(page);
    }
    public static void toggleBookmark(Context c, String document, int page) {
        List<Integer> pages = bookmarks(c, document);
        if (pages.contains(page)) pages.remove(Integer.valueOf(page));
        else pages.add(page);
        Collections.sort(pages);
        JSONArray array = new JSONArray();
        for (Integer value : pages) array.put(value);
        prefs(c).edit().putString(bookmarksKey(document), array.toString()).apply();
    }
    public static String pageNote(Context c, String document, int page) {
        return prefs(c).getString("note_" + key(document) + "_" + page, "");
    }
    public static void saveNote(Context c, String document, int page, String text) {
        String note = text == null ? "" : text.trim();
        if (note.length() > 4000) note = note.substring(0, 4000);
        SharedPreferences.Editor edit = prefs(c).edit();
        String k = "note_" + key(document) + "_" + page;
        if (note.isEmpty()) edit.remove(k); else edit.putString(k, note);
        edit.apply();
    }
}
