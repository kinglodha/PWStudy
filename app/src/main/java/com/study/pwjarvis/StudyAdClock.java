package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Persistent foreground-use clock for the PWStudy interstitial policy.
 *
 * No timer or background scheduler ever opens ads. The clock only determines
 * eligibility; the UI must separately declare a natural study break.
 */
public final class StudyAdClock {
    public static final long MIN_ACTIVE_GAP_MS = 60L * 60L * 1000L;
    public static final int DAILY_INTERSTITIAL_LIMIT = 4;
    private static final String PREFS = "pwstudy_ad_clock_v1";
    private static final String TOTAL = "total_foreground_ms";
    private static final String LAST_SHOWN = "last_ad_foreground_ms";
    private static final String DAY = "last_ad_local_day";
    private static final String COUNT = "daily_ad_count";

    private final SharedPreferences prefs;
    private long totalForegroundMs;
    private long startedAtElapsedMs = -1L;
    private long lastShownForegroundMs;
    private String localDay;
    private int dailyCount;

    public StudyAdClock(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        totalForegroundMs = Math.max(0L, prefs.getLong(TOTAL, 0L));
        lastShownForegroundMs = Math.max(0L, prefs.getLong(LAST_SHOWN, 0L));
        localDay = prefs.getString(DAY, "");
        dailyCount = Math.max(0, prefs.getInt(COUNT, 0));
    }

    public synchronized void onAppForeground() {
        if (startedAtElapsedMs < 0L) startedAtElapsedMs = SystemClock.elapsedRealtime();
    }

    public synchronized void onAppBackground() {
        if (startedAtElapsedMs < 0L) return;
        totalForegroundMs = activeMs();
        startedAtElapsedMs = -1L;
        prefs.edit().putLong(TOTAL, totalForegroundMs).apply();
    }

    private long activeMs() {
        if (startedAtElapsedMs < 0L) return totalForegroundMs;
        // elapsedRealtime is monotonic even if the wall clock is manually changed.
        return totalForegroundMs +
                Math.max(0L, SystemClock.elapsedRealtime() - startedAtElapsedMs);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void rollLocalDayIfNeeded() {
        String today = today();
        if (!today.equals(localDay)) {
            localDay = today;
            dailyCount = 0;
            prefs.edit().putString(DAY, localDay).putInt(COUNT, dailyCount).apply();
        }
    }

    public synchronized boolean canShowInterstitial() {
        rollLocalDayIfNeeded();
        long sinceLastAd = Math.max(0L, activeMs() - lastShownForegroundMs);
        return dailyCount < DAILY_INTERSTITIAL_LIMIT && sinceLastAd >= MIN_ACTIVE_GAP_MS;
    }

    /** Call only from the SDK's onAdShowedFullScreenContent callback. */
    public synchronized void onInterstitialShown() {
        rollLocalDayIfNeeded();
        lastShownForegroundMs = activeMs();
        dailyCount++;
        prefs.edit()
                .putLong(LAST_SHOWN, lastShownForegroundMs)
                .putString(DAY, localDay)
                .putInt(COUNT, dailyCount)
                .putLong(TOTAL, totalForegroundMs)
                .apply();
    }

    public synchronized int remainingToday() {
        rollLocalDayIfNeeded();
        return Math.max(0, DAILY_INTERSTITIAL_LIMIT - dailyCount);
    }

    public synchronized long activeMinutesUntilNextAd() {
        long remaining = MIN_ACTIVE_GAP_MS - Math.max(0L, activeMs() - lastShownForegroundMs);
        return Math.max(0L, (remaining + 59999L) / 60000L);
    }
}
