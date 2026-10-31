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
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
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

        Button website = makeCardButton("WEBSITE", "Open PW Website");
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(-1, dp(150));
        first.bottomMargin = dp(20);
        root.addView(website, first);
        website.setOnClickListener(v -> startActivity(new Intent(this, WebActivity.class)));

        Button library = makeCardButton("DOWNLOAD DOCUMENTS\n& VIDEOS", "Offline Library");
        root.addView(library, new LinearLayout.LayoutParams(-1, dp(150)));
        library.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));

        TextView footer = new TextView(this);
        footer.setText("Your study space");
        footer.setTextColor(Color.LTGRAY);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(28);
        root.addView(footer, footerParams);

        setContentView(root);
    }

    private Button makeCardButton(String heading, String subtitle) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(heading + "\n" + subtitle);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(28, 93, 170), Color.rgb(22, 58, 112)});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.rgb(65, 125, 205));
        button.setBackground(bg);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        return button;
    }
}
