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
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelegramViewerActivity extends Activity {
    private static final int BG = Color.rgb(5, 7, 12);
    private static final int PANEL = Color.rgb(18, 25, 38);

    private static final class Channel {
        final String title;
        final String username;
        final String[] subjects;

        Channel(String title, String username, String... subjects) {
            this.title = title;
            this.username = username;
            this.subjects = subjects;
        }
    }

    private final Channel[] channels = {
            new Channel("Saleem Sir • Physics", "saleem_sir_phy_pw", "Physics"),
            new Channel("Yakeen 2 Alpha 2027 • Botany + IOC",
                    "Pw_Yakeen_2_Alpha_2027", "Botany", "Inorganic Chemistry (IOC)"),
            new Channel("Amit Mahajan Sir • Physical Chemistry",
                    "Amit_Mahajan_Sir_Chemistry", "Physical Chemistry")
    };

    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private Spinner channelSpinner;
    private Spinner subjectSpinner;
    private EditText teacherFilter;
    private EditText keywordFilter;
    private CheckBox videosOnly;
    private TextView status;
    private WebView web;
    private int selectedChannel = -1;
    private boolean pageLoaded;
    private String userAgent = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36";

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Telegram Study Viewer");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.setBackgroundColor(BG);

        TextView heading = new TextView(this);
        heading.setText("TELEGRAM STUDY VIEWER");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView note = new TextView(this);
        note.setText("Public channel preview • choose a channel, then filter its video posts");
        note.setTextColor(0xFFADB8C8);
        note.setTextSize(12);
        root.addView(note, new LinearLayout.LayoutParams(-1, dp(28)));

        channelSpinner = new Spinner(this);
        List<String> channelNames = new ArrayList<>();
        for (Channel channel : channels) channelNames.add(channel.title);
        channelSpinner.setAdapter(adapter(channelNames));
        root.addView(channelSpinner, new LinearLayout.LayoutParams(-1, dp(46)));

        subjectSpinner = new Spinner(this);
        root.addView(subjectSpinner, new LinearLayout.LayoutParams(-1, dp(46)));

        teacherFilter = new EditText(this);
        teacherFilter.setSingleLine(true);
        teacherFilter.setTextColor(Color.WHITE);
        teacherFilter.setHintTextColor(0xFF8D99AA);
        teacherFilter.setHint("Teacher name filter (e.g. teacher's name)");
        teacherFilter.setTextSize(14);
        teacherFilter.setPadding(dp(12), 0, dp(12), 0);
        teacherFilter.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF526176));
        root.addView(teacherFilter, new LinearLayout.LayoutParams(-1, dp(46)));

        keywordFilter = new EditText(this);
        keywordFilter.setSingleLine(true);
        keywordFilter.setTextColor(Color.WHITE);
        keywordFilter.setHintTextColor(0xFF8D99AA);
        keywordFilter.setHint("Search topic or lecture title");
        keywordFilter.setTextSize(14);
        keywordFilter.setPadding(dp(12), 0, dp(12), 0);
        keywordFilter.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF526176));
        root.addView(keywordFilter, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        videosOnly = new CheckBox(this);
        videosOnly.setText("Videos only");
        videosOnly.setTextColor(Color.WHITE);
        videosOnly.setTextSize(12);
        videosOnly.setChecked(true);
        actions.addView(videosOnly, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button apply = actionButton("Apply filters");
        actions.addView(apply, new LinearLayout.LayoutParams(0, dp(42), 1));
        apply.setOnClickListener(v -> applyFilters());

        Button reload = actionButton("Reload");
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        reloadParams.leftMargin = dp(6);
        actions.addView(reload, reloadParams);
        reload.setOnClickListener(v -> reloadChannel());
        root.addView(actions);

        status = new TextView(this);
        status.setTextColor(0xFFADB8C8);
        status.setTextSize(11);
        status.setPadding(dp(3), dp(3), dp(3), dp(5));
        status.setText("Downloads are saved to Offline Library / Videos.");
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        web = new WebView(this);
        web.setBackgroundColor(BG);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        userAgent = settings.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new DownloadBridge(), "PWStudyBridge");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isTelegramPage(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isTelegramPage(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!isTelegramPage(url)) return;
                pageLoaded = true;
                status.setText("Loaded: " + channels[Math.max(0, selectedChannel)].title
                        + " • Save buttons appear on available video posts.");
                view.evaluateJavascript(filterScript(), value -> applyFilters());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    pageLoaded = false;
                    status.setText("Telegram page could not load. Check internet or channel availability.");
                }
            }
        });
        web.setDownloadListener((url, userAgent, disposition, mimeType, contentLength) ->
                queueDownload(url, null));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        AppAppearance.apply(this, root);

        channelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                if (position != selectedChannel && position >= 0 && position < channels.length) {
                    selectedChannel = position;
                    updateSubjects();
                    loadChannel();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        updateSubjects();
    }

    private ArrayAdapter<String> adapter(List<String> values) {
        ArrayAdapter<String> result = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        result.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return result;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(AppAppearance.accentColor(this)));
        return button;
    }

    private void updateSubjects() {
        if (selectedChannel < 0 || selectedChannel >= channels.length) return;
        List<String> subjects = new ArrayList<>();
        subjects.add("All subjects");
        for (String subject : channels[selectedChannel].subjects) subjects.add(subject);
        subjectSpinner.setAdapter(adapter(subjects));
        subjectSpinner.setSelection(0);
    }

    private void loadChannel() {
        if (selectedChannel < 0 || selectedChannel >= channels.length || web == null) return;
        pageLoaded = false;
        Channel channel = channels[selectedChannel];
        status.setText("Loading " + channel.title + "…");
        web.loadUrl("https://t.me/s/" + channel.username);
    }

    private void reloadChannel() {
        if (web == null || selectedChannel < 0) return;
        pageLoaded = false;
        status.setText("Reloading Telegram channel…");
        web.reload();
    }

    private boolean isTelegramPage(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme())
                && host != null && (host.equalsIgnoreCase("t.me")
                || host.toLowerCase(Locale.ROOT).endsWith(".t.me"));
    }

    private String[] subjectKeywords(String subject) {
        if ("Physics".equals(subject)) {
            return new String[]{"physics", "mechanics", "kinematics", "laws of motion",
                    "work energy", "gravitation", "electrostatics", "current electricity",
                    "magnetism", "optics", "thermodynamics"};
        }
        if ("Botany".equals(subject)) {
            return new String[]{"botany", "plant", "photosynthesis", "morphology",
                    "anatomy", "plant kingdom", "biological classification"};
        }
        if ("Inorganic Chemistry (IOC)".equals(subject)) {
            return new String[]{"inorganic", "ioc", "periodic table", "chemical bonding",
                    "coordination compounds", "p-block", "d-block", "s-block", "metallurgy"};
        }
        if ("Physical Chemistry".equals(subject)) {
            return new String[]{"physical chemistry", "mole concept", "thermodynamics",
                    "chemical equilibrium", "ionic equilibrium", "chemical kinetics",
                    "electrochemistry", "solutions", "redox"};
        }
        return new String[0];
    }

    private void applyFilters() {
        if (!pageLoaded || web == null) {
            status.setText("Wait for the Telegram channel to finish loading.");
            return;
        }
        try {
            String subject = String.valueOf(subjectSpinner.getSelectedItem());
            JSONObject filter = new JSONObject();
            filter.put("subject", subject);
            filter.put("teacher", teacherFilter.getText().toString().trim());
            filter.put("query", keywordFilter.getText().toString().trim());
            filter.put("videoOnly", videosOnly.isChecked());
            JSONArray keywords = new JSONArray();
            for (String keyword : subjectKeywords(subject)) keywords.put(keyword);
            filter.put("keywords", keywords);
            web.evaluateJavascript("window.PWStudyApplyFilters && window.PWStudyApplyFilters("
                    + filter.toString() + ");", null);
            status.setText("Filters applied • " + subject
                    + (teacherFilter.getText().length() > 0 ? " • " + teacherFilter.getText() : ""));
        } catch (Exception e) {
            status.setText("Could not apply filters: " + e.getMessage());
        }
    }

    private String filterScript() {
        return "(function(){"
                + "if(window.PWStudyFilterInstalled)return;"
                + "window.PWStudyFilterInstalled=true;"
                + "var css=document.createElement('style');"
                + "css.textContent='html,body{background:#05070c!important;color:#f4f7fb!important;}"
                + ".tgme_widget_message{background:#111a29!important;color:#f4f7fb!important;}"
                + ".tgme_widget_message_text{color:#f4f7fb!important;}"
                + ".pwstudy-save-btn{display:block;width:100%;margin:8px 0;padding:10px;"
                + "border:0;border-radius:8px;background:#246bc0;color:#fff;font-weight:700;}';"
                + "document.head.appendChild(css);"
                + "function getPosts(){return document.querySelectorAll('.tgme_widget_message_wrap');}"
                + "function getVideo(post){return post.querySelector('video');}"
                + "function decorate(){getPosts().forEach(function(post){"
                + "var video=getVideo(post);if(!video||post.querySelector('.pwstudy-save-btn'))return;"
                + "var button=document.createElement('button');button.type='button';"
                + "button.className='pwstudy-save-btn';button.textContent='Save video to Offline Library';"
                + "button.addEventListener('click',function(event){event.preventDefault();event.stopPropagation();"
                + "var source=video.currentSrc||video.src||'';"
                + "if(!source){var node=video.querySelector('source');if(node)source=node.src||'';}"
                + "var caption=post.querySelector('.tgme_widget_message_text');"
                + "var title=caption?(caption.innerText||caption.textContent||''):'Telegram video';"
                + "if(window.PWStudyBridge)window.PWStudyBridge.saveVideo(source,title.slice(0,100));"
                + "});post.appendChild(button);});}"
                + "window.PWStudyApplyFilters=function(f){window.PWStudyLastFilters=f||{};"
                + "var subject=(f.subject||'All subjects').toLowerCase();"
                + "var teacher=(f.teacher||'').trim().toLowerCase();"
                + "var query=(f.query||'').trim().toLowerCase();"
                + "var keywords=f.keywords||[];var videoOnly=!!f.videoOnly;var count=0;"
                + "getPosts().forEach(function(post){var text=(post.innerText||post.textContent||'').toLowerCase();"
                + "var hasVideo=!!post.querySelector('video');var show=true;"
                + "if(videoOnly&&!hasVideo)show=false;"
                + "if(teacher&&text.indexOf(teacher)<0)show=false;"
                + "if(query&&text.indexOf(query)<0)show=false;"
                + "if(subject!=='all subjects'&&keywords.length&&!keywords.some(function(k){return text.indexOf(k)>=0;}))show=false;"
                + "post.style.setProperty('display',show?'':'none','important');if(show)count++;});"
                + "var counter=document.getElementById('pwstudy-filter-count');"
                + "if(!counter){counter=document.createElement('div');counter.id='pwstudy-filter-count';"
                + "counter.style.cssText='color:#aebbd0;padding:8px;font:13px sans-serif;text-align:center';"
                + "document.body.insertBefore(counter,document.body.firstChild);}"
                + "counter.textContent=count+' matching Telegram post(s)';};"
                + "decorate();"
                + "if(document.body&&!window.PWStudyObserver){window.PWStudyObserver=new MutationObserver(function(){"
                + "decorate();if(window.PWStudyLastFilters)window.PWStudyApplyFilters(window.PWStudyLastFilters);"
                + "});window.PWStudyObserver.observe(document.body,{childList:true,subtree:true});}"
                + "})();";
    }

    private final class DownloadBridge {
        @JavascriptInterface
        public void saveVideo(String url, String title) {
            runOnUiThread(() -> queueDownload(url, title));
        }
    }

    private boolean isAllowedMediaUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        Uri uri = Uri.parse(value);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("t.me") || host.endsWith(".telegram-cdn.org")
                || host.equals("telegram-cdn.org") || host.equals("telesco.pe")
                || host.endsWith(".telesco.pe") || host.equals("telegram.org")
                || host.endsWith(".telegram.org");
    }

    private void queueDownload(String url, String title) {
        if (!isAllowedMediaUrl(url)) {
            Toast.makeText(this, "यह Telegram video link डाउनलोड के लिए उपलब्ध नहीं है।",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (downloadExecutor.isShutdown()) return;
        String safeTitle = title == null ? "" : title;
        status.setText("Saving video to Offline Library…");
        Toast.makeText(this, "Video download started", Toast.LENGTH_SHORT).show();
        downloadExecutor.execute(() -> downloadVideo(url, safeTitle));
    }

    private void downloadVideo(String url, String title) {
        HttpURLConnection connection = null;
        File temporary = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Referer", "https://t.me/");
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) connection.setRequestProperty("Cookie", cookies);
            connection.connect();

            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                throw new Exception("Telegram server response " + response);
            }
            if (!isAllowedMediaUrl(connection.getURL().toString())) {
                throw new Exception("Video redirected to an unsupported host");
            }

            String mime = connection.getContentType();
            String finalUrl = connection.getURL().toString();
            String extension = videoExtension(mime, finalUrl);
            if (extension == null) throw new Exception("The selected Telegram post is not a direct video file");

            File folder = new File(getExternalFilesDir(null), "PWStudy/Videos");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new Exception("Offline Library Videos folder could not be created");
            }
            String baseName = title.replaceAll("[^a-zA-Z0-9._ -]", "_")
                    .trim().replaceAll("\\s+", "_");
            if (baseName.length() > 48) baseName = baseName.substring(0, 48);
            if (baseName.isEmpty()) baseName = "Telegram_video";
            String stem = baseName + "_" + System.currentTimeMillis();
            File destination = new File(folder, stem + extension);
            temporary = new File(folder, stem + extension + ".part");

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
            }
            if (!temporary.renameTo(destination)) {
                throw new Exception("Downloaded file could not be moved into Offline Library");
            }
            File saved = destination;
            runOnUiThread(() -> {
                status.setText("Saved: " + saved.getName() + " • Offline Library / Videos");
                Toast.makeText(this, "Saved to Offline Library / Videos",
                        Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            if (temporary != null && temporary.exists()) temporary.delete();
            String message = e.getMessage() == null ? "Download failed" : e.getMessage();
            runOnUiThread(() -> {
                status.setText("Download failed: " + message);
                Toast.makeText(this, "Telegram download failed: " + message,
                        Toast.LENGTH_LONG).show();
            });
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String videoExtension(String mime, String url) {
        String lowerMime = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        String path = Uri.parse(url).getPath();
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lowerMime.startsWith("video/webm") || lowerPath.endsWith(".webm")) return ".webm";
        if (lowerMime.startsWith("video/quicktime") || lowerPath.endsWith(".mov")) return ".mov";
        if (lowerMime.startsWith("video/mp4") || lowerPath.endsWith(".mp4")
                || lowerPath.endsWith(".m4v")) return lowerPath.endsWith(".m4v") ? ".m4v" : ".mp4";
        if (lowerMime.startsWith("video/")) return ".mp4";
        return null;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("PWStudyBridge");
            web.stopLoading();
            web.destroy();
            web = null;
        }
        downloadExecutor.shutdown();
        super.onDestroy();
    }
}