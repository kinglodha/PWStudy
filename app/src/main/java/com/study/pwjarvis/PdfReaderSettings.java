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
        return validDelay(value) ? value : 4;
    }
    public static void setControlsDelay(Context c, int seconds) {
        p(c).edit().putInt("controls_delay", validDelay(seconds) ? seconds : 4).apply();
    }
    private static boolean validDelay(int value) {
        return value >= 0 && value <= 10 && value % 2 == 0;
    }
    public static void applyDefaults(Context c, int theme, boolean fit, boolean remember,
            boolean swipe, boolean awake, int delay) {
        if (theme < 0 || theme > 2 || !validDelay(delay))
            throw new IllegalArgumentException("Invalid reader defaults");
        p(c).edit().putInt("theme", theme).putBoolean("fit_width", fit)
            .putBoolean("remember_page", remember).putBoolean("swipe_navigation", swipe)
            .putBoolean("keep_awake", awake).putInt("controls_delay", delay).apply();
    }
    public static void resetDefaults(Context c) {
        applyDefaults(c, 0, false, true, true, false, 4);
    }
    public static org.json.JSONObject exportDefaults(Context c) throws org.json.JSONException {
        return new org.json.JSONObject().put("format", "tweak-reader-settings")
            .put("version", 1).put("theme", theme(c)).put("fit_width", fitWidth(c))
            .put("remember_page", rememberPage(c)).put("swipe_navigation", swipeNavigation(c))
            .put("keep_awake", keepAwake(c)).put("controls_delay", controlsDelay(c));
    }
    public static void validateDefaults(org.json.JSONObject data) throws org.json.JSONException {
        if (!"tweak-reader-settings".equals(data.get("format"))
            || !Integer.valueOf(1).equals(data.get("version")))
            throw new org.json.JSONException("Unsupported backup format");
        Object theme = data.get("theme"), delay = data.get("controls_delay");
        if (!(theme instanceof Integer) || (Integer)theme < 0 || (Integer)theme > 2
            || !(delay instanceof Integer) || !validDelay((Integer)delay))
            throw new org.json.JSONException("Invalid reader values");
        for (String key : new String[]{"fit_width", "remember_page", "swipe_navigation", "keep_awake"})
            if (!(data.get(key) instanceof Boolean)) throw new org.json.JSONException("Invalid toggle: " + key);
    }
    public static void importDefaults(Context c, org.json.JSONObject data) throws org.json.JSONException {
        validateDefaults(data);
        applyDefaults(c, data.getInt("theme"), data.getBoolean("fit_width"),
            data.getBoolean("remember_page"), data.getBoolean("swipe_navigation"),
            data.getBoolean("keep_awake"), data.getInt("controls_delay"));
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
    public static void movePage(Context c,String oldId,String newId) {
        if(oldId==null||newId==null||oldId.equals(newId))return;
        SharedPreferences prefs=p(c);
        if(prefs.contains(pageKey(oldId)))prefs.edit()
            .putInt(pageKey(newId),lastPage(c,oldId)).remove(pageKey(oldId)).apply();
    }
    public static void flushPage(Context c,String document,int index) {
        if(document!=null&&rememberPage(c))
            p(c).edit().putInt(pageKey(document),Math.max(0,index)).commit();
    }
    public static void reset(Context c) { p(c).edit().clear().apply(); }
}
