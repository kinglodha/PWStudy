package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebActivity extends Activity {
    private WebView web;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 16, 25));

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String script = "(function(){var nodes=[].slice.call(document.querySelectorAll('button,a,[role=button]'));"
                        + "nodes.forEach(function(n){var t=(n.innerText||n.textContent||'').trim().toUpperCase();"
                        + "if(t==='BACK'||t==='OFFLINE LIBRARY'){var p=n.parentElement;"
                        + "if(p&&p.children.length>=2){p.style.display='none';}else{n.style.display='none';}}});})();";
                view.evaluateJavascript(script, null);
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                askDownload(url, userAgent, contentDisposition, mimeType));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (state == null) web.loadUrl("https://www.pwjarvis.com/study/batches");
        else web.restoreState(state);
    }

    private void askDownload(String url, String userAgent, String disposition, String mimeType) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            Toast.makeText(this, "यह डाउनलोड लिंक समर्थित नहीं है।", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Download to Offline Library?")
                .setMessage("यह फ़ाइल ऐप के Offline Library में सेव होगी।")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Download", (d, w) -> downloadFile(url, userAgent, disposition, mimeType))
                .show();
    }

    private void downloadFile(String link, String userAgent, String disposition, String mimeType) {
        Toast.makeText(this, "Download शुरू हो रहा है…", Toast.LENGTH_SHORT).show();
        String cookies = CookieManager.getInstance().getCookie(link);
        executor.execute(() -> {
            FileOutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(link);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent);
                if (cookies != null) connection.setRequestProperty("Cookie", cookies);
                connection.setRequestProperty("Referer", "https://www.pwjarvis.com/");
                connection.connect();

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("Server response: " + code);

                String name = guessFileName(connection.getURL().toString(), disposition, mimeType);
                String folder = isVideo(name, mimeType) ? "Videos" : "Documents";
                File base = new File(getExternalFilesDir(null), "PWStudy/" + folder);
                if (!base.exists() && !base.mkdirs()) throw new Exception("Storage folder नहीं बन पाया");
                File destination = new File(base, name);
                String stem = name;
                int dot = name.lastIndexOf('.');
                String ext = dot >= 0 ? name.substring(dot) : "";
                if (dot >= 0) stem = name.substring(0, dot);
                int suffix = 1;
                while (destination.exists()) destination = new File(base, stem + "_" + suffix++ + ext);

                output = new FileOutputStream(destination);
                BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
                input.close();
                File saved = destination;
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Download complete: " + saved.getName() + " (Offline Library → " + folder + ")",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Download failed" : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "Download नहीं हुआ: " + message, Toast.LENGTH_LONG).show());
            } finally {
                try { if (output != null) output.close(); } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String guessFileName(String url, String disposition, String mime) {
        String name = null;
        if (disposition != null) {
            for (String part : disposition.split(";")) {
                String p = part.trim();
                if (p.toLowerCase().startsWith("filename=")) {
                    name = p.substring(9).replace("\"", "").trim();
                    break;
                }
            }
        }
        if (name == null || name.isEmpty()) {
            try {
                String path = Uri.parse(url).getLastPathSegment();
                if (path != null) name = URLDecoder.decode(path, "UTF-8");
            } catch (Exception ignored) {}
        }
        if (name == null || name.isEmpty() || !name.contains(".")) {
            String extension = URLConnection.guessContentTypeFromName(name == null ? "" : name);
            if (mime != null && mime.contains("pdf")) extension = "application/pdf";
            if ("application/pdf".equals(extension)) name = "document.pdf";
            else if (mime != null && mime.startsWith("video/")) name = "video.mp4";
            else name = "download_" + System.currentTimeMillis() + ".bin";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private boolean isVideo(String name, String mime) {
        String n = name.toLowerCase();
        return (mime != null && mime.startsWith("video/")) ||
                n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".mov");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        executor.shutdown();
        super.onDestroy();
    }
}
