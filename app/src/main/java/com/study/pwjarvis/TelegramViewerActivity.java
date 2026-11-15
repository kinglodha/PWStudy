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
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
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
    private static final String TELEGRAM_WEB = "https://web.telegram.org/k/";

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
    private boolean pageFailed;

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
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button loginButton = new Button(this);
        loginButton.setText("Login");
        loginButton.setAllCaps(false);
        loginButton.setOnClickListener(v -> openTelegramLogin());
        header.addView(loginButton, new LinearLayout.LayoutParams(-2, dp(42)));

        Button channelsButton = new Button(this);
        channelsButton.setText("Channels");
        channelsButton.setAllCaps(false);
        channelsButton.setOnClickListener(v -> showChannelList());
        header.addView(channelsButton, new LinearLayout.LayoutParams(-2, dp(42)));
        root.addView(header);

        status = new TextView(this);
        status.setText("Sign in using Telegram Web, then choose one of the seven channels.");
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
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                super.onPageStarted(view, url, icon);
                pageFailed = false;
                status.setText("Loading Telegram Web inside PWStudy…");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    pageFailed = true;
                    status.setText("Telegram could not load. Check internet and Android WebView.");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    pageFailed = true;
                    status.setText("Telegram returned HTTP " + response.getStatusCode()
                            + ". The page may be unavailable.");
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                if (!pageFailed) {
                    status.setText("Telegram Web loaded. Sign in by phone if needed. "
                            + "Channel invitations may still require a native Telegram client.");
                }
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
            button.setText(channel.name + "\nOPEN CHANNEL");
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

    private void showWebPage(String url, String message) {
        channelScroll.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        status.setText(message);
        web.loadUrl(url);
    }

    private void openTelegramLogin() {
        showWebPage(TELEGRAM_WEB,
                "Telegram Web sign-in: use your phone number and verification code.");
    }

    private void openChannel(StudyChannel channel) {
        String link = toTelegramWebLink(Uri.parse(channel.url));
        if (link == null) {
            status.setText("This channel link is not supported.");
            return;
        }
        showWebPage(link, "Opening " + channel.name
                + " in Telegram Web. You may need to sign in first.");
    }

    private void showChannelList() {
        if (web != null) {
            web.stopLoading();
            web.setVisibility(View.GONE);
        }
        channelScroll.setVisibility(View.VISIBLE);
        status.setText("Choose one of the seven channels. Login remains available above.");
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return true;
        if (isAllowedTelegramUrl(uri)) return false;
        String destination = toTelegramWebLink(uri);
        if (destination != null) {
            web.loadUrl(destination);
            return true;
        }
        status.setText("Blocked navigation outside Telegram inside PWStudy.");
        return true;
    }

    // Telegram's t.me pages are previews, not an authenticated channel client.
    // Route supported deep links to the Telegram Web client instead of opening
    // an external Android application. Web K deep-link handling is best effort;
    // reliable private-channel access will require an authenticated TDLib client.
    private String toTelegramWebLink(Uri uri) {
        if (uri == null) return null;
        String scheme = uri.getScheme();
        String deepLink = null;
        if ("tg".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            if ("join".equalsIgnoreCase(host) || "resolve".equalsIgnoreCase(host)) {
                deepLink = uri.toString();
            }
        } else if ("https".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (!(host.equals("t.me") || host.equals("telegram.me"))) return null;
            String path = uri.getPath();
            if (path == null || path.length() < 2) return null;
            String handle = path.substring(1);
            if (handle.indexOf('/') >= 0) return null;
            if (handle.startsWith("+")) {
                if (handle.length() < 2) return null;
                deepLink = "tg://join?invite=" + handle.substring(1);
            } else if (handle.matches("[A-Za-z0-9_]{5,32}")) {
                deepLink = "tg://resolve?domain=" + handle;
            }
        }
        return deepLink == null ? null
                : TELEGRAM_WEB + "#?tgaddr=" + Uri.encode(deepLink);
    }

    private boolean isAllowedTelegramUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("web.telegram.org")
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
