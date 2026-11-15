package com.study.pwjarvis.telegram;

import android.os.Handler;
import android.os.Looper;

import org.drinkless.tdlib.JsonClient;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** One process-wide TDLib receiver; all callbacks are delivered on the main thread. */
final class TdJsonBridge {
    interface Listener { void onTdUpdate(JSONObject update); }
    interface Result { void onResult(JSONObject value); }

    private static TdJsonBridge instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, Result> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile WeakReference<Listener> listener = new WeakReference<>(null);
    private volatile JSONObject authorization;
    private final int clientId;

    static synchronized TdJsonBridge get() {
        if (instance == null) instance = new TdJsonBridge();
        return instance;
    }

    private TdJsonBridge() {
        // No account data, messages, codes, or TDLib requests are written to app logs.
        JsonClient.execute("{\"@type\":\"setLogVerbosityLevel\",\"new_verbosity_level\":0}");
        JsonClient.execute("{\"@type\":\"setLogStream\",\"log_stream\":{\"@type\":\"logStreamEmpty\"}}");
        clientId = JsonClient.createClientId();
        Thread receiver = new Thread(this::receiveLoop, "pwstudy-tdlib-receiver");
        receiver.setDaemon(true);
        receiver.start();
        try {
            send(new JSONObject().put("@type", "getOption").put("name", "version"), null);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start TDLib", e);
        }
    }

    void attach(Listener target) {
        listener = new WeakReference<>(target);
        JSONObject current = authorization;
        if (current != null) main.post(() -> {
            Listener active = listener.get();
            if (active == target) active.onTdUpdate(current);
        });
    }

    void detach(Listener target) {
        if (listener.get() == target) listener = new WeakReference<>(null);
    }

    void send(JSONObject request, Result callback) {
        String requestId = null;
        try {
            if (callback != null) {
                String id = Long.toString(sequence.incrementAndGet());
                requestId = id;
                request.put("@extra", id);
                pending.put(id, callback);
                main.postDelayed(() -> {
                    Result timedOut = pending.remove(id);
                    if (timedOut != null) timedOut.onResult(error());
                }, 30_000);
            }
            JsonClient.send(clientId, request.toString());
        } catch (Exception | LinkageError e) {
            Result failed = requestId == null ? callback : pending.remove(requestId);
            if (failed != null) main.post(() -> failed.onResult(error()));
        }
    }

    private void receiveLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            String response = JsonClient.receive(10.0);
            if (response == null) continue;
            try {
                JSONObject event = new JSONObject(response);
                if (event.optInt("@client_id", clientId) != clientId) continue;
                String id = event.optString("@extra", "");
                Result callback = id.isEmpty() ? null : pending.remove(id);
                if (callback != null) main.post(() -> callback.onResult(event));
                if (event.optString("@type").equals("updateAuthorizationState")) authorization = event;
                if (event.optString("@type").startsWith("update")) main.post(() -> {
                    Listener active = listener.get();
                    if (active != null) active.onTdUpdate(event);
                });
            } catch (Exception ignored) {
                // Never log unexpected TDLib payloads: they may contain private messages.
            }
        }
    }

    private static JSONObject error() {
        try { return new JSONObject().put("@type", "error"); }
        catch (Exception impossible) { return new JSONObject(); }
    }
}
