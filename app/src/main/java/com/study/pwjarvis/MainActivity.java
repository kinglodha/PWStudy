package com.study.pwjarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(20));
        root.setBackgroundColor(Color.rgb(12, 16, 25));

        TextView title = new TextView(this);
        title.setText("PW JARVIS STUDY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = dp(34);
        root.addView(title, titleParams);

        Button website = makeButton("WEBSITE\nOpen PW Website", true);
        LinearLayout.LayoutParams websiteParams = new LinearLayout.LayoutParams(-1, dp(150));
        root.addView(website, websiteParams);
        website.setOnClickListener(v -> startActivity(new Intent(this, WebActivity.class)));

        TextView spacer = new TextView(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        Button library = makeButton("OFFLINE LIBRARY  ↓", false);
        LinearLayout.LayoutParams libraryParams = new LinearLayout.LayoutParams(-1, dp(58));
        libraryParams.bottomMargin = dp(4);
        root.addView(library, libraryParams);
        library.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));

        setContentView(root);
    }

    private Button makeButton(String text, boolean filled) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(filled ? 18 : 15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg;
        if (filled) {
            bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.rgb(28, 93, 170), Color.rgb(22, 58, 112)});
        } else {
            bg = new GradientDrawable();
            bg.setColor(Color.TRANSPARENT);
        }
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(90, 145, 205));
        button.setBackground(bg);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }
}
