package com.study.pwjarvis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
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
            new StudyChannel("Yakeen NEET", "https://t.me/YakeenNeet_3", "BATCH"),
            new StudyChannel("PHYSICS", "https://t.me/+1Us9I5v80jNjNDQ1", "PHYSICS"),
            new StudyChannel("SALEEM SIR PHYSICS", "https://t.me/Saleem_Sir_physics_NEET", "PHYSICS"),
            new StudyChannel("PHYSICAL CHEMISTRY", "https://t.me/+jPzDsZLxVUZmMGVl", "CHEMISTRY"),
            new StudyChannel("INORGANIC CHEMISTRY", "https://t.me/+lVhOzKTDSs84MGJl", "CHEMISTRY"),
            new StudyChannel("BOTANY", "https://t.me/+_MYNwUQF1n43YjRl", "BIOLOGY"),
            new StudyChannel("ZOOLOGY", "https://t.me/+aLgCob6m9bhlYjU1", "BIOLOGY"),
            new StudyChannel("ORGANIC CHEMISTRY", "https://t.me/+zrVWpo5mY5FmNDU1", "CHEMISTRY")
    };

    private WebView web;
    private LinearLayout pageRoot;
    private FrameLayout fullscreenLayer;
    private View fullscreenVideo;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private Button landscapeButton;
    private boolean manualLandscape;
    private int orientationBeforeFullscreen = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private LinearLayout header;
    private FrameLayout webContainer;
    private Button studyBackOverlay;
    private TextView status;
    private LinearLayout channelPanel;
    private ScrollView channelScroll;
    private boolean pageFailed;
    private final ExecutorService pdfWorker = Executors.newSingleThreadExecutor();

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Keep channel view edge-to-edge inside the app. Pad only the list.
        root.setBackgroundColor(BG);
        pageRoot = root;

        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView heading = new TextView(this);
        heading.setText("STUDY CHANNELS");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button channelsButton = new Button(this);
        channelsButton.setText("Channels");
        channelsButton.setAllCaps(false);
        channelsButton.setOnClickListener(v -> showChannelList());
        header.addView(channelsButton, new LinearLayout.LayoutParams(-2, dp(42)));
        root.addView(header);

        status = new TextView(this);
        status.setText("Choose one of your nine study channels.");
        status.setTextColor(0xFFADB8C8);
        status.setTextSize(12);
        status.setPadding(dp(15), dp(4), dp(15), dp(8));
        root.addView(status);

        channelPanel = new LinearLayout(this);
        channelPanel.setOrientation(LinearLayout.VERTICAL);
        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(false);
        channelScroll.setPadding(dp(12), 0, dp(12), dp(8));
        channelScroll.addView(channelPanel);
        root.addView(channelScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        webContainer = new FrameLayout(this);
        webContainer.setVisibility(View.GONE);
        web = new WebView(this);
        web.setBackgroundColor(BG);
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
        // Telegram Web delivers HTML5 video fullscreen through WebChromeClient.
        // Without these callbacks Android keeps playback inside the portrait WebView.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (view == null || fullscreenLayer == null || fullscreenVideo != null) {
                    if (callback != null) callback.onCustomViewHidden();
                    return;
                }
                fullscreenVideo = view;
                fullscreenCallback = callback;
                orientationBeforeFullscreen = getRequestedOrientation();
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                fullscreenLayer.addView(view, new FrameLayout.LayoutParams(-1, -1));
                pageRoot.setVisibility(View.GONE);
                fullscreenLayer.setVisibility(View.VISIBLE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                updateVideoSystemUi();
            }

            @Override
            public void onHideCustomView() {
                exitVideoFullscreen(false);
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            Uri uri = Uri.parse(url);
            if (isPdfLink(uri) || (mimeType != null
                    && mimeType.toLowerCase(Locale.ROOT).contains("pdf"))) {
                downloadAndOpenPdf(url, userAgent);
            } else if (url != null && url.startsWith("blob:")) {
                Toast.makeText(this, "Telegram Web blob downloads need native integration.",
                        Toast.LENGTH_LONG).show();
            }
        });
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
                status.setText("Opening selected study channel…");
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
                    status.setText("Use Back to return to your nine study channels. "
                            + "Telegram may request sign-in if this device has no saved session.");
                }
            }
        });
        webContainer.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // Telegram Web is a single-page application. Its own top-left back
        // arrow can open the unrestricted Telegram inbox instead of PWStudy.
        // Cover that control with a native button owned by PWStudy.
        studyBackOverlay = new Button(this);
        studyBackOverlay.setText("← Channels");
        studyBackOverlay.setAllCaps(false);
        studyBackOverlay.setTextSize(13);
        studyBackOverlay.setTextColor(Color.WHITE);
        studyBackOverlay.setPadding(0, 0, 0, 0);
        studyBackOverlay.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(PANEL));
        studyBackOverlay.setOnClickListener(v -> showChannelList());
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                dp(106), dp(64), Gravity.TOP | Gravity.START);
        webContainer.addView(studyBackOverlay, overlayParams);
        // A native orientation control also works when Telegram does not expose
        // an HTML5 fullscreen button (for example on some embedded players).
        landscapeButton = new Button(this);
        landscapeButton.setText("⛶ Landscape");
        landscapeButton.setAllCaps(false);
        landscapeButton.setTextSize(12);
        landscapeButton.setTextColor(Color.WHITE);
        landscapeButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(PANEL));
        landscapeButton.setOnClickListener(v -> toggleTelegramLandscape());
        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(
                dp(120), dp(52), Gravity.TOP | Gravity.END);
        rotateParams.topMargin = dp(6);
        rotateParams.rightMargin = dp(6);
        webContainer.addView(landscapeButton, rotateParams);
        root.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout outer = new FrameLayout(this);
        outer.addView(root, new FrameLayout.LayoutParams(-1, -1));
        fullscreenLayer = new FrameLayout(this);
        fullscreenLayer.setBackgroundColor(Color.BLACK);
        fullscreenLayer.setVisibility(View.GONE);
        outer.addView(fullscreenLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(outer);
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
        header.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        webContainer.setVisibility(View.VISIBLE);
        status.setText(message);
        web.loadUrl(url);
    }

    private void openChannel(StudyChannel channel) {
        String link = toTelegramWebLink(Uri.parse(channel.url));
        if (link == null) {
            status.setText("This channel link is not supported.");
            return;
        }
        showWebPage(link, "Opening " + channel.name
                + ". Sign in only if Telegram requests it.");
    }

    private void toggleTelegramLandscape() {
        if (fullscreenVideo != null) return; // HTML5 fullscreen owns rotation.
        manualLandscape = !manualLandscape;
        landscapeButton.setText(manualLandscape ? "↺ Portrait" : "⛶ Landscape");
        setRequestedOrientation(manualLandscape
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void updateVideoSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                fullscreenVideo == null ? View.SYSTEM_UI_FLAG_VISIBLE
                        : View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void exitVideoFullscreen(boolean notifyWeb) {
        if (fullscreenVideo == null) return;
        View previous = fullscreenVideo;
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenVideo = null;
        fullscreenCallback = null;
        fullscreenLayer.removeView(previous);
        fullscreenLayer.setVisibility(View.GONE);
        pageRoot.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(orientationBeforeFullscreen);
        updateVideoSystemUi();
        if (notifyWeb && callback != null) callback.onCustomViewHidden();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Keep the existing Telegram WebView and the playing HTML5 video alive.
        if (fullscreenVideo != null) updateVideoSystemUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreenVideo != null) updateVideoSystemUi();
    }

    private void showChannelList() {
        exitVideoFullscreen(true);
        manualLandscape = false;
        if (landscapeButton != null) landscapeButton.setText("⛶ Landscape");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (web != null) {
            web.stopLoading();
        }
        webContainer.setVisibility(View.GONE);
        header.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        channelScroll.setVisibility(View.VISIBLE);
        status.setText("Choose one of your nine study channels.");
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return true;
        if (isPdfLink(uri)) {
            downloadAndOpenPdf(uri.toString(), web.getSettings().getUserAgentString());
            return true;
        }
        if (isAllowedTelegramUrl(uri)) return false;
        String destination = toTelegramWebLink(uri);
        if (destination != null) {
            web.loadUrl(destination);
            return true;
        }
        status.setText("Blocked navigation outside Telegram inside PWStudy.");
        return true;
    }

    private boolean isPdfLink(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String path = uri.getPath();
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * Telegram Web cannot render most PDF responses itself. For real HTTPS PDF
     * URLs, save an authenticated copy in the offline library and open our
     * native reader. Blob URLs still require a native Telegram media API.
     */
    private void downloadAndOpenPdf(String url, String userAgent) {
        if (url == null || !url.startsWith("https://")) {
            Toast.makeText(this, "Only HTTPS PDF downloads are supported.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Opening PDF in PWStudy…", Toast.LENGTH_SHORT).show();
        final String cookies = CookieManager.getInstance().getCookie(url);
        pdfWorker.execute(() -> {
            HttpURLConnection connection = null;
            File temp = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "application/pdf,*/*");
                connection.setRequestProperty("Referer", TELEGRAM_WEB);
                if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent);
                if (cookies != null) connection.setRequestProperty("Cookie", cookies);
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
                    throw new Exception("HTTP " + connection.getResponseCode());
                File parent = new File(getExternalFilesDir(null), "PWStudy/Documents");
                if (!parent.isDirectory() && !parent.mkdirs())
                    throw new Exception("Cannot create PDF folder");
                String base = Uri.parse(url).getLastPathSegment();
                if (base == null || !base.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    base = "Telegram.pdf";
                base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
                String filename = System.currentTimeMillis() + "_" + base;
                File destination = new File(parent, filename);
                temp = new File(parent, filename + ".download");
                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] header = new byte[1024];
                    int size = in.read(header);
                    if (size < 5) throw new Exception("Empty or invalid PDF");
                    boolean pdf = false;
                    for (int i = 0; i <= size - 5; i++) {
                        if (header[i] == '%' && header[i+1] == 'P' &&
                                header[i+2] == 'D' && header[i+3] == 'F' &&
                                header[i+4] == '-') { pdf = true; break; }
                    }
                    if (!pdf) throw new Exception("Server returned a page, not a PDF");
                    out.write(header, 0, size);
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                if (!temp.renameTo(destination))
                    throw new Exception("Cannot finish downloaded PDF");
                if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> {
                        Intent open = new Intent(this, PdfActivity.class);
                        open.putExtra("file_path", destination.getAbsolutePath());
                        startActivity(open);
                    });
                }
            } catch (Exception error) {
                String message = error.getMessage();
                if (!isFinishing() && !isDestroyed()) runOnUiThread(() ->
                        Toast.makeText(this, "PDF could not open: " + message,
                                Toast.LENGTH_LONG).show());
            } finally {
                if (temp != null && temp.exists()) temp.delete();
                if (connection != null) connection.disconnect();
            }
        });
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
        if (fullscreenVideo != null) {
            exitVideoFullscreen(true);
        } else if (web != null && webContainer.getVisibility() == View.VISIBLE) {
            // Never navigate into Telegram Web's previous pages on Android Back.
            showChannelList();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        exitVideoFullscreen(true);
        pdfWorker.shutdownNow();
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
