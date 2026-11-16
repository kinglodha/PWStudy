package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared, persistent reader defaults. App and PDF screens use the same settings. */
public final class PdfReaderSettings {
    private PdfReaderSettings() {}
    private static final String FILE = "pwstudy_pdf_settings";
    private static SharedPreferences p(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    public static int theme(Context c) {
        return Math.max(0, Math.min(2, p(c).getInt("theme", 0)));
    }
    public static void setTheme(Context c, int value) {
        p(c).edit().putInt("theme", Math.max(0, Math.min(2, value))).apply();
    }
    public static boolean fitWidth(Context c) { return p(c).getBoolean("fit_width", false); }
    public static void setFitWidth(Context c, boolean v) { p(c).edit().putBoolean("fit_width", v).apply(); }
    public static boolean keepAwake(Context c) { return p(c).getBoolean("keep_awake", false); }
    public static void setKeepAwake(Context c, boolean v) { p(c).edit().putBoolean("keep_awake", v).apply(); }
    public static boolean swipeNavigation(Context c) { return p(c).getBoolean("swipe_navigation", true); }
    public static void setSwipeNavigation(Context c, boolean v) { p(c).edit().putBoolean("swipe_navigation", v).apply(); }
    public static boolean rememberPage(Context c) { return p(c).getBoolean("remember_page", true); }
    public static void setRememberPage(Context c, boolean v) { p(c).edit().putBoolean("remember_page", v).apply(); }
    /** 0 disables automatic toolbar hiding. */
    public static int controlsDelay(Context c) {
        int value = p(c).getInt("controls_delay", 4);
        return value == 0 || value == 8 ? value : 4;
    }
    public static void setControlsDelay(Context c, int seconds) {
        p(c).edit().putInt("controls_delay", seconds == 0 || seconds == 8 ? seconds : 4).apply();
    }
    private static String pageKey(String document) {
        return "last_page_" + Integer.toHexString(document.hashCode());
    }
    public static int lastPage(Context c, String document) {
        return document == null ? 0 : Math.max(0, p(c).getInt(pageKey(document), 0));
    }
    public static void savePage(Context c, String document, int index) {
        if (document != null && rememberPage(c)) {
            p(c).edit().putInt(pageKey(document), Math.max(0, index)).apply();
        }
    }
    public static void reset(Context c) { p(c).edit().clear().apply(); }
}
