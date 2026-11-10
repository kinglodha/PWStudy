package com.study.pwjarvis;

import android.app.Activity;
import androidx.core.view.WindowCompat;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(5, 7, 12));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(5, 7, 12));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("PW JARVIS STUDY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = dp(8);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your study space");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.bottomMargin = dp(22);
        root.addView(subtitle, subtitleParams);

        Button loading = makeButton("LOADING NAME", "Your NEET study channels", true);
        root.addView(loading, buttonParams(dp(76)));
        loading.setOnClickListener(v -> {
            Toast.makeText(this, "Select a Telegram channel below.", Toast.LENGTH_SHORT).show();
        });

        addSectionTitle(root, "TELEGRAM CHANNELS", "Open your subject-wise study channels");
        addChannel(root, "1. YAKEEN 2.0 ALPHA", "https://t.me/yakeen_2_aplha_2027");
        addChannel(root, "2. PHYSICS", "https://t.me/+1Us9I5v80jNjNDQ1");
        addChannel(root, "3. PHYSICAL CHEMISTRY", "https://t.me/+jPzDsZLxVUZmMGVl");
        addChannel(root, "4. INORGANIC CHEMISTRY", "https://t.me/+lVhOzKTDSs84MGJl");
        addChannel(root, "5. BOTANY", "https://t.me/+_MYNwUQF1n43YjRl");
        addChannel(root, "6. ZOOLOGY", "https://t.me/+aLgCob6m9bhlYjU1");
        addChannel(root, "7. ORGANIC CHEMISTRY", "https://t.me/+zrVWpo5mY5FmNDU1");

        addSectionTitle(root, "STUDY TOOLS", "Your saved files and app features");
        Button library = makeButton("OFFLINE LIBRARY",
                "Files downloaded and saved inside this app", false);
        root.addView(library, spacedButtonParams());
        library.setOnClickListener(v ->
                startActivity(new Intent(this, LibraryActivity.class)));

        Button telegramViewer = makeButton("TELEGRAM VIEWER",
                "Filter channel videos by teacher and subject", false);
        root.addView(telegramViewer, spacedButtonParams());
        telegramViewer.setOnClickListener(v ->
                startActivity(new Intent(this, TelegramViewerActivity.class)));

        Button device = makeButton("DEVICE DOCUMENTS",
                "Browse documents and folders on this phone", false);
        root.addView(device, spacedButtonParams());
        device.setOnClickListener(v ->
                startActivity(new Intent(this, DeviceDocumentsActivity.class)));

        Button customize = makeButton("CUSTOMIZE APP",
                "Change supported appearance settings", false);
        LinearLayout.LayoutParams customizeParams = buttonParams(dp(64));
        customizeParams.topMargin = dp(22);
        root.addView(customize, customizeParams);
        customize.setOnClickListener(v ->
                startActivity(new Intent(this, CustomizationActivity.class)));

        setContentView(scroll);
        AppAppearance.apply(this, root);
    }

    private void addSectionTitle(LinearLayout root, String heading, String detail) {
        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(25);
        params.bottomMargin = dp(4);
        root.addView(title, params);

        TextView subtitle = new TextView(this);
        subtitle.setText(detail);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.bottomMargin = dp(10);
        root.addView(subtitle, detailParams);
    }

    private void addChannel(LinearLayout root, String name, String url) {
        Button channel = makeButton(name, "OPEN TELEGRAM CHANNEL  ↗", false);
        LinearLayout.LayoutParams params = buttonParams(dp(72));
        params.topMargin = dp(9);
        root.addView(channel, params);
        channel.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("org.telegram.messenger");
                startActivity(intent);
            } catch (Exception telegramMissing) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception noHandler) {
                    Toast.makeText(this, "No app found to open this Telegram link.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private LinearLayout.LayoutParams buttonParams(int height) {
        return new LinearLayout.LayoutParams(-1, height);
    }

    private LinearLayout.LayoutParams spacedButtonParams() {
        LinearLayout.LayoutParams params = buttonParams(dp(78));
        params.topMargin = dp(10);
        return params;
    }

    private Button makeButton(String title, String detail, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title + "\n" + detail);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                primary
                        ? new int[]{Color.rgb(28, 93, 170), Color.rgb(22, 58, 112)}
                        : new int[]{Color.rgb(29, 39, 55), Color.rgb(20, 28, 42)});
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Color.rgb(75, 115, 160));
        button.setBackground(background);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        return button;
    }
}