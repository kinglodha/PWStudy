package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Crash-tolerant, per-lecture playback progress for local video files.
 *
 * Progress is independent of Activity recreation and never modifies or copies
 * the user's study video. The preference key is a stable hash of the media URI;
 * the raw URI (which can contain personal folder names) is not stored.
 *
 * A completed lecture is cleared so that the next opening starts at the
 * beginning. We use apply() to avoid blocking the main thread on disk writes.
 */
final class LectureProgressStore {
    private static final String PREFERENCES = "pwstudy_lecture_progress_v1";
    private static final String KEY_PREFIX = "position_";
    private static final long MIN_RESUME_MS = 3_000L;
    private static final long END_MARGIN_MS = 8_000L;

    private LectureProgressStore() { }

    /** Return the last useful resume position, or zero for a new lecture. */
    static long read(Context context, Uri mediaUri) {
        String key = key(mediaUri);
        if (key == null) return 0L;
        long position = preferences(context).getLong(key, 0L);
        return Math.max(0L, position);
    }

    /**
     * Save only a meaningful position. Small initial positions do not overwrite
     * an older useful checkpoint until the lecture really begins playing.
     *
     * A completed (or nearly finished) lecture must not reopen near its end.
     * Unknown duration is allowed; it is common for streaming-backed URI
     * providers or during initial preparation.
     */
    static void save(Context context, Uri mediaUri, long positionMs, long durationMs) {
        String key = key(mediaUri);
        if (key == null || positionMs < MIN_RESUME_MS) return;
        if (durationMs > 0L && durationMs != Long.MAX_VALUE
                && positionMs >= Math.max(0L, durationMs - END_MARGIN_MS)) {
            preferences(context).edit().remove(key).apply();
            return;
        }
        preferences(context).edit().putLong(key, positionMs).apply();
    }

    /** An explicit restart or natural completion discards only this lecture. */
    static void clear(Context context, Uri mediaUri) {
        String key = key(mediaUri);
        if (key != null) preferences(context).edit().remove(key).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String key(Uri mediaUri) {
        if (mediaUri == null) return null;
        String canonical = mediaUri.normalizeScheme().toString();
        if (canonical.isEmpty()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(KEY_PREFIX.length() + value.length * 2);
            hex.append(KEY_PREFIX);
            for (byte b : value) {
                int unsigned = b & 0xff;
                hex.append(Character.forDigit(unsigned >>> 4, 16));
                hex.append(Character.forDigit(unsigned & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ignored) {
            // SHA-256 is required in every supported Android API level.
            // Fail closed rather than falling back to raw URI preference keys.
            return null;
        }
    }
}
