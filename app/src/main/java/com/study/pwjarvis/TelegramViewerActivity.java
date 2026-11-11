package com.study.pwjarvis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class TelegramViewerActivity extends Activity {
    private static final int BG = Color.rgb(5, 7, 12);
    private static final int PANEL = Color.rgb(18, 25, 38);

    private static final class StudyChannel {
        final String name;
        final String url;
        final String subject;

        StudyChannel(String name, String url, String subject) {
            this.name = name;
            this.url = url;
            this.subject = subject;
        }
    }

    private final StudyChannel[] channels = {
            new StudyChannel("Yakeen 2.0 Alpha", "https://t.me/yakeen_2_aplha_2027", "BATCH"),
            new StudyChannel("PHYSICS", "https://t.me/+1Us9I5v80jNjNDQ1", "PHYSICS"),
            new StudyChannel("PHYSICAL CHEMISTRY", "https://t.me/+jPzDsZLxVUZmMGVl", "CHEMISTRY"),
            new StudyChannel("INORGANIC CHEMISTRY", "https://t.me/+lVhOzKTDSs84MGJl", "CHEMISTRY"),
            new StudyChannel("BOTANY", "https://t.me/+_MYNwUQF1n43YjRl", "BIOLOGY"),
            new StudyChannel("ZOOLOGY", "https://t.me/+aLgCob6m9bhlYjU1", "BIOLOGY"),
            new StudyChannel("ORGANIC CHEMISTRY", "https://t.me/+zrVWpo5mY5FmNDU1", "CHEMISTRY")
    };

    private WebView web;
    private TextView status;
    private LinearLayout channelPanel;
    private ScrollView channelScroll;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("STUDY CHANNELS");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button channelsButton = new Button(this);
        channelsButton.setText("Channels");
        channelsButton.setAllCaps(false);
        channelsButton.setOnClickListener(v -> showChannelList());
        header.addView(channelsButton, new LinearLayout.LayoutParams(-2, dp(42)));
        root.addView(header);

        status = new TextView(this);
        status.setText("Choose a teacher or subject channel below.");
        status.setTextColor(0xFFADB8C8);
        status.setTextSize(12);
        status.setPadding(dp(3), dp(4), dp(3), dp(8));
        root.addView(status);

        channelPanel = new LinearLayout(this);
        channelPanel.setOrientation(LinearLayout.VERTICAL);
        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(false);
        channelScroll.addView(channelPanel);
        root.addView(channelScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        web = new WebView(this);
        web.setBackgroundColor(BG);
        web.setVisibility(View.GONE);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedTelegramUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowedTelegramUrl(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                status.setText("Telegram page loaded inside PWStudy. Sign in if Telegram asks.");
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        Button backToChannels = new Button(this);
        backToChannels.setText("Back to study channels");
        backToChannels.setAllCaps(false);
        backToChannels.setOnClickListener(v -> showChannelList());
        root.addView(backToChannels, new LinearLayout.LayoutParams(-1, dp(46)));

        setContentView(root);
        AppAppearance.apply(this, root);
        buildChannelList();
    }

    private void buildChannelList() {
        channelPanel.removeAllViews();
        String currentGroup = "";
        for (StudyChannel channel : channels) {
            if (!channel.subject.equals(currentGroup)) {
                currentGroup = channel.subject;
                TextView group = new TextView(this);
                group.setText(currentGroup);
                group.setTextColor(0xFF9EBCE5);
                group.setTextSize(12);
                group.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                group.setPadding(dp(4), dp(12), dp(4), dp(4));
                channelPanel.addView(group, new LinearLayout.LayoutParams(-1, dp(34)));
            }
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(channel.name + "\\nOPEN CHANNEL INSIDE APP");
            button.setTextColor(Color.WHITE);
            button.setTextSize(14);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(66));
            params.bottomMargin = dp(8);
            channelPanel.addView(button, params);
            button.setOnClickListener(v -> openChannel(channel));
        }
    }

    private void openChannel(StudyChannel channel) {
        channelScroll.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        status.setText("Opening " + channel.name + " inside PWStudy…");
        web.loadUrl(channel.url);
    }

    private void showChannelList() {
        if (web != null) {
            web.stopLoading();
            web.setVisibility(View.GONE);
        }
        channelScroll.setVisibility(View.VISIBLE);
        status.setText("Choose one of the configured study channels.");
    }

    private boolean isAllowedTelegramUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("t.me") || host.endsWith(".t.me")
                || host.equals("web.telegram.org") || host.equals("telegram.me")
                || host.equals("telegram.org") || host.endsWith(".telegram.org");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.getVisibility() == View.VISIBLE && web.canGoBack()) {
            web.goBack();
        } else if (web != null && web.getVisibility() == View.VISIBLE) {
            showChannelList();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
