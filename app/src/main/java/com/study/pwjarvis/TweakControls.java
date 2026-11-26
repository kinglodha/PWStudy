package com.study.pwjarvis;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;

/** Shared pressed-state and accessible rounded controls, without changing screen structure. */
public final class TweakControls {
    private TweakControls() {}

    public static int dp(Context context, int size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }

    public static RippleDrawable surface(Context c, int fill, int border, int radius) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(fill);
        base.setCornerRadius(dp(c, radius));
        if (border != Color.TRANSPARENT) base.setStroke(dp(c, 1), border);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radius));
        return new RippleDrawable(ColorStateList.valueOf(0x337faeff), base, mask);
    }

    public static void button(Button b, boolean light, boolean selected) {
        int fill = selected ? (light ? 0xffdce8ff : 0xff254d83) :
                (light ? 0xfff2f5fa : 0xff253044);
        int border = selected ? 0xff649afa : (light ? 0xffcdd6e2 : 0xff455773);
        b.setBackgroundTintList(null);
        b.setBackground(surface(b.getContext(), fill, border, 12));
        b.setTextColor(light ? 0xff20324e : 0xfff2f6ff);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setPadding(dp(b.getContext(), 8), 0, dp(b.getContext(), 8), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(b.getContext(), 40));
    }

    public static void icon(View v, boolean light) {
        v.setBackground(surface(v.getContext(), light ? 0xffe8eef9 : 0xff253650,
                light ? 0xffc9d6eb : 0xff466184, 12));
        v.setFocusable(true);
    }
}
