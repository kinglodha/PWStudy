package com.study.pwjarvis;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Counts only time with a PWStudy Activity on screen. It also records a genuine
 * study-to-home transition; simply waking/launching the app is NOT a break.
 */
public final class PWStudyApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private StudyAdClock adClock;
    private int startedActivities;
    private int startedStudyActivities;
    private boolean returnedFromStudy;

    @Override public void onCreate() {
        super.onCreate();
        adClock = new StudyAdClock(this);
        registerActivityLifecycleCallbacks(this);
    }

    public StudyAdClock adClock() { return adClock; }

    public boolean hasOpenStudyScreen() { return startedStudyActivities > 0; }

    public boolean consumeStudyBreak() {
        boolean wasStudying = returnedFromStudy;
        returnedFromStudy = false;
        return wasStudying;
    }

    @Override public void onActivityStarted(Activity activity) {
        if (startedActivities++ == 0) adClock.onAppForeground();
        if (activity instanceof PdfActivity || activity instanceof PlayerActivity) {
            startedStudyActivities++;
            returnedFromStudy = true;
        }
    }

    @Override public void onActivityStopped(Activity activity) {
        if (activity instanceof PdfActivity || activity instanceof PlayerActivity) {
            startedStudyActivities = Math.max(0, startedStudyActivities - 1);
            if (startedStudyActivities == 0) StudyAdManager.get().onStudyScreenStopped();
        }
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0) adClock.onAppBackground();
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
