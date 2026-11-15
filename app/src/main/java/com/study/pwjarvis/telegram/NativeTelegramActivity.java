package com.study.pwjarvis.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.study.pwjarvis.PdfActivity;
import com.study.pwjarvis.PlayerActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * EXPERIMENTAL: Native TDLib client for the preconfigured study channels.
 * Not a production release: see README for missing sponsored-message support and test gates.
 */
public class NativeTelegramActivity extends Activity implements TdJsonBridge.Listener {
    private static final class Channel {
        final String name;
        final String link;
        final boolean invite;
        Channel(String name, String link, boolean invite) { this.name = name; this.link = link; this.invite = invite; }
    }

    /** Only the app's non-exported caller supplies the existing seven study channel links. */
    public static final String EXTRA_CHANNEL_NAMES = "study_channel_names";
    public static final String EXTRA_CHANNEL_LINKS = "study_channel_links";
    private Channel[] channels;

    private static final class PendingOpen {
        final String kind;
        final long chatId;
        PendingOpen(String kind, long chatId) { this.kind = kind; this.chatId = chatId; }
    }
    private final Map<Integer, PendingOpen> pendingDownloads = new HashMap<>();
    private final Set<Long> allowedChatIds = new HashSet<>();
    private final Set<Long> displayedMessages = new HashSet<>();
    private TdJsonBridge client;
    private PrivateTdStore store;
    private JSONObject currentAuthorization;
    private LinearLayout root;
    private LinearLayout content;
    private TextView status;
    private long currentChat;
    private long oldestMessage;
    private JSONArray sponsored = new JSONArray();
    private int sponsorCursor;
    private int sponsorInterval;
    private int ordinarySinceSponsor;
    private boolean historyLoading;
    private boolean historyEnded;
    private boolean sponsorLoading;
    private Button olderButton;
    private Button sponsorRetry;
    private boolean foreground;
    private int requestGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Prevent login fields and channel contents from appearing in task snapshots/screenshots.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new PrivateTdStore(this);
        buildScreen("Telegram study channels");
        channels = readChannelCatalog();
        if (channels == null) {
            status.setText("The seven study channels are not configured in this build.");
            return;
        }
        try {
            client = TdJsonBridge.get();
            client.attach(this);
            status.setText("Connecting to Telegram…");
        } catch (LinkageError | RuntimeException e) {
            status.setText("Native Telegram library could not start. This build is incomplete.");
        }
    }

    @Override protected void onResume() { super.onResume(); foreground = true; }
    @Override protected void onPause() { foreground = false; super.onPause(); }
    @Override protected void onDestroy() {
        if (client != null && currentChat != 0) client.send(req("closeChat", "chat_id", currentChat), null);
        requestGeneration++;
        if (client != null) client.detach(this);
        super.onDestroy();
    }

    @Override public void onTdUpdate(JSONObject update) {
        String type = update.optString("@type");
        if ("updateAuthorizationState".equals(type)) {
            currentAuthorization = update.optJSONObject("authorization_state");
            showAuthorization();
        } else if ("updateFile".equals(type)) {
            JSONObject file = update.optJSONObject("file");
            if (file != null) processFile(file);
        }
    }

    private void showAuthorization() {
        if (currentAuthorization == null) return;
        String state = currentAuthorization.optString("@type");
        if ("authorizationStateWaitTdlibParameters".equals(state)) {
            try {
                JSONObject credentials = store.read();
                if (credentials == null) showApiCredentials(); else initialize(credentials);
            } catch (Exception e) {
                showMessage("Secure session storage is unavailable. Do not delete the existing app data.");
            }
        } else if ("authorizationStateWaitPhoneNumber".equals(state)) {
            showSingleInput("Telegram phone number", "+91…", InputType.TYPE_CLASS_PHONE,
                    "setAuthenticationPhoneNumber", "phone_number");
        } else if ("authorizationStateWaitCode".equals(state)) {
            showSingleInput("Telegram verification code", "Code", InputType.TYPE_CLASS_NUMBER,
                    "checkAuthenticationCode", "code");
        } else if ("authorizationStateWaitPassword".equals(state)) {
            showSingleInput("Telegram two-step password", "Password",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    "checkAuthenticationPassword", "password");
        } else if ("authorizationStateWaitEmailAddress".equals(state)) {
            showSingleInput("Telegram login email", "Email address",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    "setAuthenticationEmailAddress", "email_address");
        } else if ("authorizationStateWaitEmailCode".equals(state)) {
            showEmailCode();
        } else if ("authorizationStateReady".equals(state)) {
            showChannels();
        } else if ("authorizationStateWaitOtherDeviceConfirmation".equals(state)) {
            showMessage("Telegram requires confirmation from another signed-in device.");
        } else if ("authorizationStateWaitPremiumPurchase".equals(state)) {
            showMessage("Telegram requires an additional login step that this prototype does not support.");
        } else if ("authorizationStateWaitRegistration".equals(state)) {
            showMessage("This prototype supports an existing Telegram account; account registration is not available.");
        } else if ("authorizationStateClosed".equals(state)) {
            showMessage("Telegram session closed. Restart PWStudy to reconnect.");
        } else {
            showMessage("Connecting to Telegram…");
        }
    }

    private void showApiCredentials() {
        buildScreen("Telegram app setup");
        status.setText("Enter the API ID and API hash from your own Telegram application. They stay on this phone.");
        EditText id = input("API ID", InputType.TYPE_CLASS_NUMBER);
        EditText hash = input("API hash", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(id);
        content.addView(hash);
        addButton("Continue", () -> {
            try {
                int apiId = Integer.parseInt(id.getText().toString().trim());
                String apiHash = hash.getText().toString().trim();
                if (apiId <= 0 || !apiHash.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException();
                JSONObject credentials = store.writeCredentials(apiId, apiHash);
                hash.setText("");
                initialize(credentials);
            } catch (Exception e) {
                status.setText("Check the API ID and API hash, or secure storage availability.");
            }
        });
    }

    private void initialize(JSONObject credentials) {
        File database = store.databaseDirectory();
        File files = store.filesDirectory();
        if ((!database.exists() && !database.mkdirs()) || (!files.exists() && !files.mkdirs())) {
            showMessage("Telegram storage is unavailable.");
            return;
        }
        status.setText("Starting secure Telegram session…");
        JSONObject parameters = req("setTdlibParameters",
                "database_directory", database.getAbsolutePath(),
                "files_directory", files.getAbsolutePath(),
                "database_encryption_key", credentials.optString("db_key"),
                "use_test_dc", false,
                "use_file_database", true,
                "use_chat_info_database", true,
                "use_message_database", true,
                "use_secret_chats", false,
                "api_id", credentials.optInt("api_id"),
                "api_hash", credentials.optString("api_hash"),
                "system_language_code", Locale.getDefault().getLanguage(),
                "device_model", Build.MODEL,
                "system_version", Build.VERSION.RELEASE,
                "application_version", "PWStudy prototype");
        client.send(parameters, response -> {
            if (isError(response) && isAuthorizationState("authorizationStateWaitTdlibParameters")) {
                showApiCredentials();
                status.setText("Telegram could not initialize. Check the API credentials.");
            }
        });
    }

    private void showSingleInput(String title, String hint, int kind, String method, String field) {
        buildScreen(title);
        status.setText("Enter this information only on your phone.");
        EditText value = input(hint, kind);
        content.addView(value);
        String expectedState = currentAuthorization.optString("@type");
        addButton("Continue", () -> {
            String text = value.getText().toString().trim();
            if (text.isEmpty()) { status.setText("Enter the requested information."); return; }
            value.setText("");
            status.setText("Checking…");
            client.send(req(method, field, text), response -> {
                if (isError(response) && isAuthorizationState(expectedState)) {
                    status.setText("Telegram could not verify that information. Try again.");
                }
            });
        });
    }

    private void showEmailCode() {
        buildScreen("Telegram email code");
        status.setText("Enter the code sent to your email.");
        EditText code = input("Email code", InputType.TYPE_CLASS_NUMBER);
        content.addView(code);
        addButton("Continue", () -> {
            String value = code.getText().toString().trim();
            if (value.isEmpty()) return;
            code.setText("");
            client.send(req("checkAuthenticationEmailCode", "code",
                    req("emailAddressAuthenticationCode", "code", value)), response -> {
                if (isError(response) && isAuthorizationState("authorizationStateWaitEmailCode"))
                    status.setText("Email code could not be verified. Try again.");
            });
        });
    }

    private void showChannels() {
        if (currentChat != 0) client.send(req("closeChat", "chat_id", currentChat), null);
        currentChat = 0;
        requestGeneration++;
        buildScreen("Seven study channels");
        status.setText("Choose a channel available to your Telegram account.");
        for (Channel channel : channels) addButton(channel.name, () -> resolve(channel));
    }

    private void resolve(Channel channel) {
        int generation = ++requestGeneration;
        status.setText("Opening " + channel.name + "…");
        JSONObject request = channel.invite
                ? req("checkChatInviteLink", "invite_link", channel.link)
                : req("searchPublicChat", "username", channel.link);
        client.send(request, response -> {
            if (generation != requestGeneration) return;
            if (isError(response)) { status.setText("Channel could not be opened. The link or membership may have changed."); return; }
            long id = channel.invite ? response.optLong("chat_id") : response.optLong("id");
            if (id != 0) {
                allowedChatIds.add(id);
                showHistory(channel, id);
            } else if (channel.invite && !response.isNull("subscription_info")) {
                status.setText("This channel requires a subscription; joining is not supported in this prototype.");
            } else if (channel.invite) {
                boolean approval = response.optBoolean("creates_join_request", false);
                new AlertDialog.Builder(this).setTitle(channel.name)
                        .setMessage(approval ? "Send a request to join this channel?" : "Join this channel?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(approval ? "Request" : "Join", (dialog, which) -> {
                            client.send(req("joinChatByInviteLink", "invite_link", channel.link), joined -> {
                                if (generation != requestGeneration) return;
                                long joinedId = joined.optLong("id");
                                if (joinedId != 0) {
                                    allowedChatIds.add(joinedId);
                                    showHistory(channel, joinedId);
                                } else status.setText("Joining may require approval, or the link may have expired.");
                            });
                        }).show();
            }
        });
    }

    private void showHistory(Channel channel, long id) {
        if (!allowedChatIds.contains(id)) return;
        currentChat = id;
        oldestMessage = 0;
        displayedMessages.clear();
        int generation = ++requestGeneration;
        buildScreen(channel.name);
        addButton("Back to channels", this::showChannels);
        status.setText("Loading messages…");
        client.send(req("openChat", "chat_id", id), null);
        sponsored = new JSONArray();
        sponsorCursor = 0;
        sponsorInterval = 0;
        ordinarySinceSponsor = 0;
        historyLoading = false;
        historyEnded = false;
        sponsorLoading = false;
        olderButton = null;
        sponsorRetry = null;
        // Load sponsored messages before history: don't silently bypass them on an error.
        loadSponsors(id, generation);
    }

    private void loadSponsors(long id, int generation) {
        if (generation != requestGeneration || id != currentChat || sponsorLoading) return;
        sponsorLoading = true;
        if (sponsorRetry != null) sponsorRetry.setEnabled(false);
        status.setText("Loading messages…");
        client.send(req("getChatSponsoredMessages", "chat_id", id), response -> {
            if (generation != requestGeneration || id != currentChat) return;
            if (isError(response)) {
                // The API applies only to channels and bots. Verify that this is
                // a non-channel group before continuing without sponsored items.
                client.send(req("getChat", "chat_id", id), chat -> {
                    if (generation != requestGeneration || id != currentChat) return;
                    sponsorLoading = false;
                    JSONObject type = chat.optJSONObject("type");
                    String kind = type == null ? "" : type.optString("@type");
                    boolean ordinaryGroup = "chatTypeBasicGroup".equals(kind)
                            || ("chatTypeSupergroup".equals(kind) && type.has("is_channel")
                            && !type.optBoolean("is_channel"));
                    if (ordinaryGroup) {
                        removeSponsorRetry();
                        loadHistory(id, generation);
                    } else {
                        status.setText("Channel messages could not be loaded. Try again.");
                        showSponsorRetry(id, generation);
                    }
                });
                return;
            }
            sponsorLoading = false;
            removeSponsorRetry();
            JSONArray results = response.optJSONArray("messages");
            sponsored = results == null ? new JSONArray() : results;
            sponsorInterval = Math.max(0, response.optInt("messages_between", 0));
            loadHistory(id, generation);
        });
    }

    private void showSponsorRetry(long id, int generation) {
        if (sponsorRetry == null) {
            sponsorRetry = new Button(this);
            sponsorRetry.setText("Retry");
            sponsorRetry.setAllCaps(false);
            sponsorRetry.setOnClickListener(view -> loadSponsors(id, generation));
            content.addView(sponsorRetry);
        }
        sponsorRetry.setEnabled(true);
    }

    private void removeSponsorRetry() {
        if (sponsorRetry != null) content.removeView(sponsorRetry);
        sponsorRetry = null;
    }

    private void loadHistory(long id, int generation) {
        if (id != currentChat || !allowedChatIds.contains(id) || historyLoading || historyEnded) return;
        historyLoading = true;
        if (olderButton != null) olderButton.setEnabled(false);
        client.send(req("getChatHistory", "chat_id", id, "from_message_id", oldestMessage,
                "offset", 0, "limit", 40, "only_local", false), response -> {
            if (generation != requestGeneration || id != currentChat) return;
            historyLoading = false;
            if (isError(response)) {
                status.setText("Messages could not be loaded. Try again.");
                showOlderButton(id, generation, "Retry");
                return;
            }
            if (olderButton != null) content.removeView(olderButton);
            JSONArray messages = response.optJSONArray("messages");
            if (messages == null || messages.length() == 0) { finishHistory(id); return; }
            int newMessages = 0;
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.optJSONObject(i);
                if (message == null) continue;
                long messageId = message.optLong("id");
                oldestMessage = messageId;
                if (displayedMessages.add(messageId)) {
                    newMessages++;
                    addMessage(message, id);
                    ordinarySinceSponsor++;
                    if (sponsorInterval > 0 && ordinarySinceSponsor >= sponsorInterval
                            && sponsorCursor < sponsored.length()) {
                        addSponsored(sponsored.optJSONObject(sponsorCursor++), id);
                        ordinarySinceSponsor = 0;
                    }
                }
            }
            if (newMessages == 0) {
                finishHistory(id);
            } else {
                status.setText("Showing available channel messages.");
                showOlderButton(id, generation, "Older messages");
            }
        });
    }

    private void finishHistory(long id) {
        historyEnded = true;
        if (olderButton != null) content.removeView(olderButton);
        olderButton = null;
        if (sponsorInterval == 0 && sponsorCursor == 0 && sponsored.length() > 0)
            addSponsored(sponsored.optJSONObject(sponsorCursor++), id);
        status.setText("No more messages.");
    }

    private void showOlderButton(long id, int generation, String label) {
        if (olderButton == null) {
            olderButton = new Button(this);
            olderButton.setAllCaps(false);
            olderButton.setOnClickListener(view -> loadHistory(id, generation));
        } else {
            content.removeView(olderButton);
        }
        olderButton.setText(label);
        olderButton.setEnabled(true);
        content.addView(olderButton);
    }

    private void addSponsored(JSONObject item, long chatId) {
        if (item == null) return;
        JSONObject body = item.optJSONObject("content");
        JSONObject sponsor = item.optJSONObject("sponsor");
        String type = body == null ? "" : body.optString("@type");
        String text = body == null ? "" : formatted(body.optJSONObject("messageText".equals(type) ? "text" : "caption"));
        String label = item.optBoolean("is_recommended") ? "Recommended" : "Sponsored";
        String extra = item.optString("additional_info", "");
        String sponsorInfo = sponsor == null ? "" : sponsor.optString("info", "");
        if (!"messageText".equals(type)) text += "\n[Sponsored media: preview not implemented in this prototype]";
        TextView displayed = new TextView(this);
        displayed.setText(label + " · " + item.optString("title", "") + "\n" + text
                + (sponsorInfo.isEmpty() ? "" : "\n" + sponsorInfo)
                + (extra.isEmpty() ? "" : "\n" + extra));
        displayed.setPadding(0, 16, 0, 16);
        content.addView(displayed);
        long messageId = item.optLong("message_id");
        final boolean[] counted = {false};
        Runnable markIfFullyVisible = () -> {
            if (counted[0] || !foreground || currentChat != chatId || messageId == 0) return;
            Rect visible = new Rect();
            if (displayed.getHeight() > 0 && displayed.getGlobalVisibleRect(visible)
                    && visible.height() >= displayed.getHeight()) {
                counted[0] = true;
                client.send(req("viewMessages", "chat_id", chatId,
                        "message_ids", new JSONArray().put(messageId), "source", JSONObject.NULL,
                        "force_read", false), null);
            }
        };
        displayed.getViewTreeObserver().addOnScrollChangedListener(markIfFullyVisible::run);
        displayed.getViewTreeObserver().addOnGlobalLayoutListener(markIfFullyVisible::run);
        String url = sponsor == null ? "" : sponsor.optString("url", "");
        if (!url.isEmpty()) addButton(item.optString("button_text", "Open sponsor"), () -> {
            Uri target = Uri.parse(url);
            if (!"https".equalsIgnoreCase(target.getScheme())) {
                status.setText("This sponsored link type is not supported by the prototype.");
                return;
            }
            new AlertDialog.Builder(this).setTitle("Open sponsored link?")
                    .setMessage("The link will open outside PWStudy.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, target));
                            client.send(req("clickChatSponsoredMessage", "chat_id", chatId,
                                    "message_id", messageId, "is_media_click", false,
                                    "from_fullscreen", false), null);
                        } catch (Exception ignored) { status.setText("No app can open this link."); }
                    }).show();
        });
    }

    private Channel[] readChannelCatalog() {
        ArrayList<String> names = getIntent().getStringArrayListExtra(EXTRA_CHANNEL_NAMES);
        ArrayList<String> links = getIntent().getStringArrayListExtra(EXTRA_CHANNEL_LINKS);
        if (names == null || links == null || names.size() != 7 || links.size() != 7) return null;
        Channel[] result = new Channel[7];
        for (int i = 0; i < 7; i++) {
            String name = names.get(i);
            String link = links.get(i);
            if (name == null || link == null) return null;
            Uri uri = Uri.parse(link);
            if (name.trim().isEmpty() || !"https".equalsIgnoreCase(uri.getScheme())
                    || !("t.me".equalsIgnoreCase(uri.getHost()) || "telegram.me".equalsIgnoreCase(uri.getHost()))) return null;
            String segment = uri.getLastPathSegment();
            if (segment == null || segment.isEmpty()) return null;
            boolean invite = segment.startsWith("+");
            if (!invite && !segment.matches("[A-Za-z0-9_]{5,32}")) return null;
            result[i] = new Channel(name, invite ? uri.toString() : segment, invite);
        }
        return result;
    }

    private void addMessage(JSONObject message, long chatId) {
        JSONObject body = message.optJSONObject("content");
        if (body == null) return;
        String type = body.optString("@type");
        if ("messageText".equals(type)) {
            addText(formatted(body.optJSONObject("text")));
        } else if ("messageVideo".equals(type)) {
            JSONObject video = body.optJSONObject("video");
            JSONObject file = video == null ? null : video.optJSONObject("video");
            String caption = formatted(body.optJSONObject("caption"));
            if (!caption.isEmpty()) addText(caption);
            if (file != null) addButton("Open video", () -> download(file.optInt("id"), "video", chatId));
        } else if ("messageDocument".equals(type)) {
            JSONObject document = body.optJSONObject("document");
            JSONObject file = document == null ? null : document.optJSONObject("document");
            String filename = document == null ? "" : document.optString("file_name");
            String mime = document == null ? "" : document.optString("mime_type");
            String caption = formatted(body.optJSONObject("caption"));
            if (!caption.isEmpty()) addText(caption);
            String lowerName = filename.toLowerCase(Locale.ROOT);
            if (file != null && (lowerName.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(mime))) {
                addButton("Open PDF: " + filename, () -> download(file.optInt("id"), "pdf", chatId));
            } else if (file != null && (mime.toLowerCase(Locale.ROOT).startsWith("video/")
                    || lowerName.endsWith(".mp4") || lowerName.endsWith(".m4v"))) {
                addButton("Open video: " + filename, () -> download(file.optInt("id"), "video", chatId));
            } else if (!filename.isEmpty()) addText(filename);
        }
    }

    private void download(int id, String kind, long chatId) {
        if (id <= 0 || chatId != currentChat || !allowedChatIds.contains(chatId)) return;
        pendingDownloads.put(id, new PendingOpen(kind, chatId));
        status.setText("Downloading " + kind + " for in-app viewing…");
        client.send(req("downloadFile", "file_id", id, "priority", 16,
                "offset", 0, "limit", 0, "synchronous", false), response -> {
            if (isError(response)) {
                pendingDownloads.remove(id);
                status.setText("File could not be downloaded.");
            } else processFile(response);
        });
    }

    private void processFile(JSONObject file) {
        int id = file.optInt("id");
        PendingOpen pending = pendingDownloads.get(id);
        JSONObject local = file.optJSONObject("local");
        if (pending == null || local == null || !local.optBoolean("is_downloading_completed")) return;
        pendingDownloads.remove(id);
        if (!foreground || pending.chatId != currentChat) {
            status.setText("Download complete. Tap the file again to open it.");
            return;
        }
        try {
            File path = new File(local.optString("path")).getCanonicalFile();
            File dir = store.filesDirectory().getCanonicalFile();
            if (!path.getPath().startsWith(dir.getPath() + File.separator) || !path.isFile()) throw new IllegalStateException();
            Intent intent = new Intent(this, "pdf".equals(pending.kind) ? PdfActivity.class : PlayerActivity.class);
            intent.putExtra("file_path", path.getAbsolutePath());
            startActivity(intent);
        } catch (Exception e) {
            status.setText("Downloaded file is unavailable to the in-app viewer.");
        }
    }

    private void buildScreen(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(padding, padding, padding, padding);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(22);
        root.addView(heading);
        status = new TextView(this);
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private EditText input(String hint, int type) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(type);
        field.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        return field;
    }
    private void showMessage(String message) { buildScreen("Telegram study channels"); status.setText(message); }
    private void addButton(String title, Runnable task) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(view -> task.run());
        content.addView(button);
    }
    private void addText(String text) {
        if (text == null || text.isEmpty()) return;
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, 12, 0, 12);
        content.addView(view);
    }
    private static String formatted(JSONObject text) { return text == null ? "" : text.optString("text", ""); }
    private boolean isAuthorizationState(String expected) {
        return currentAuthorization != null && expected.equals(currentAuthorization.optString("@type"));
    }
    private static boolean isError(JSONObject value) { return "error".equals(value.optString("@type")); }
    private static JSONObject req(String type, Object... fields) {
        try {
            JSONObject request = new JSONObject().put("@type", type);
            for (int i = 0; i < fields.length; i += 2) request.put((String) fields[i], fields[i + 1]);
            return request;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid Telegram request", e); }
    }
    @Override public void onBackPressed() {
        if (currentChat != 0) showChannels(); else super.onBackPressed();
    }
}
