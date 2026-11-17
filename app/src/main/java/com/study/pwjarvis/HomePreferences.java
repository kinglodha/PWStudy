package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferences for the home layout; separate from PDF reader and appearance settings. */
public final class HomePreferences {
    private HomePreferences() {}
    private static final String FILE = "pwstudy_home";
    public static final String TELEGRAM = "show_telegram";
    public static final String LIBRARY = "show_library";
    public static final String RECENT = "show_recent";
    public static final String TOOLS = "show_tools";
    public static final String GRID = "tools_grid";
    public static final String DETAILS = "show_details";
    public static final String COMPACT = "compact_cards";
    public static final String RECENT_COUNT = "recent_count";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    public static boolean get(Context c, String key, boolean fallback) {
        return prefs(c).getBoolean(key, fallback);
    }
    public static void set(Context c, String key, boolean value) {
        prefs(c).edit().putBoolean(key, value).apply();
    }
    public static int recentCount(Context c) {
        return prefs(c).getInt(RECENT_COUNT, 3);
    }
    public static void setRecentCount(Context c, int value) {
        prefs(c).edit().putInt(RECENT_COUNT, value).apply();
    }
    public static void reset(Context c) {
        prefs(c).edit().clear().apply();
    }
}
