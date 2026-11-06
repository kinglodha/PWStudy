package com.study.pwjarvis;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public final class AppAppearance {
    private AppAppearance() {}
    private static final String PREFS = "pwstudy_appearance";
    private static final String TEXT = "text_color";
    private static final String ACCENT = "accent_color";
    private static final String IMAGE = "background_uri";
    public static final int DEFAULT_TEXT = Color.WHITE;
    public static final int DEFAULT_ACCENT = Color.rgb(35, 112, 210);
    public static int textColor(Context c) { return c.getSharedPreferences(PREFS,0).getInt(TEXT,DEFAULT_TEXT); }
    public static int accentColor(Context c) { return c.getSharedPreferences(PREFS,0).getInt(ACCENT,DEFAULT_ACCENT); }
    public static String backgroundUri(Context c) { return c.getSharedPreferences(PREFS,0).getString(IMAGE,null); }
    public static void setTextColor(Context c,int color) { c.getSharedPreferences(PREFS,0).edit().putInt(TEXT,color).apply(); }
    public static void setAccentColor(Context c,int color) { c.getSharedPreferences(PREFS,0).edit().putInt(ACCENT,color).apply(); }
    public static void setBackgroundUri(Context c,String uri) { c.getSharedPreferences(PREFS,0).edit().putString(IMAGE,uri).apply(); }
    public static void clearBackground(Context c) { c.getSharedPreferences(PREFS,0).edit().remove(IMAGE).apply(); }

    public static void apply(Activity activity, View root) {
        root.setBackgroundColor(Color.rgb(5,7,12));
        String saved = backgroundUri(activity);
        if (saved != null) {
            try (java.io.InputStream in = activity.getContentResolver().openInputStream(Uri.parse(saved))) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap != null) root.setBackground(new BitmapDrawable(activity.getResources(), bitmap));
            } catch (Exception ignored) { clearBackground(activity); }
        }
        applyText(root, textColor(activity), accentColor(activity));
    }

    private static void applyText(View view,int text,int accent) {
        if (view instanceof TextView) {
            ((TextView)view).setTextColor(text);
            if (view instanceof Button) ((Button)view).setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) applyText(group.getChildAt(i),text,accent);
        }
    }
}
