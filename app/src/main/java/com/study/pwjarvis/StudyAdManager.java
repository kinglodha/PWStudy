package com.study.pwjarvis;

import android.app.Activity;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.lang.ref.WeakReference;

/**
 * Test-only AdMob integration. All display decisions are made on the home screen,
 * and only after returning from an actual PDF or local video study session.
 * Never display a full-screen ad on a timer, launch, PDF, player or Telegram screen.
 */
public final class StudyAdManager {
    // Official Google Android test identifiers. No live monetization until replaced.
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TAG = "PWStudyAds";
    private static final long MAX_AD_AGE_MS = 55L * 60L * 1000L;

    private static final StudyAdManager INSTANCE = new StudyAdManager();
    public static StudyAdManager get() { return INSTANCE; }
    private StudyAdManager() {}

    private boolean consentStarted;
    private boolean initializationStarted;
    private boolean adsReady;
    private boolean interstitialLoading;
    private boolean interstitialShowing;
    private boolean homeResumed;
    private boolean awaitingStudyStop;
    private ConsentInformation consent;
    private InterstitialAd interstitial;
    private long interstitialLoadedAt;
    private AdView banner;
    private WeakReference<MainActivity> home = new WeakReference<>(null);
    private WeakReference<FrameLayout> bannerSlot = new WeakReference<>(null);

    /** Called once per cold app launch. UMP checks consent at each launch. */
    public void initialize(MainActivity activity) {
        home = new WeakReference<>(activity);
        if (consentStarted) return;
        consentStarted = true;
        consent = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(activity, parameters,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        activity, error -> {
                            if (error != null) Log.w(TAG, "Consent form: " + error.getMessage());
                            initializeSdkWhenAllowed(activity);
                        }),
                error -> {
                    Log.w(TAG, "Consent update: " + error.getMessage());
                    // UMP permits using previously valid consent, but never bypass it.
                    initializeSdkWhenAllowed(activity);
                });
    }

    private void initializeSdkWhenAllowed(MainActivity activity) {
        if (consent == null || !consent.canRequestAds() || initializationStarted) return;
        initializationStarted = true;
        android.content.Context app = activity.getApplicationContext();
        new Thread(() -> MobileAds.initialize(app, status -> activity.runOnUiThread(() -> {
            adsReady = true;
            MainActivity current = home.get();
            if (current != null && !current.isFinishing() && !current.isDestroyed()) {
                createHomeBanner(current);
                preloadInterstitial(current);
            }
        })), "pwstudy-admob-init").start();
    }

    /** Reserve only space actually used by the home banner; never overlay study UI. */
    public void attachHomeBanner(MainActivity activity, FrameLayout slot) {
        destroyBanner();
        home = new WeakReference<>(activity);
        bannerSlot = new WeakReference<>(slot);
        if (adsReady) createHomeBanner(activity);
    }

    private void createHomeBanner(MainActivity activity) {
        FrameLayout slot = bannerSlot.get();
        if (!adsReady || slot == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (banner != null) return;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int widthDp = Math.max(1, Math.round(metrics.widthPixels / metrics.density) - 34);
        AdView view = new AdView(activity);
        view.setAdUnitId(TEST_BANNER);
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
        view.setAdListener(new AdListener() {
            @Override public void onAdFailedToLoad(LoadAdError error) {
                Log.w(TAG, "Home test banner unavailable: " + error.getMessage());
            }
        });
        banner = view;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        slot.addView(view, lp);
        view.loadAd(new AdRequest.Builder().build());
    }

    private void destroyBanner() {
        if (banner == null) return;
        banner.destroy();
        ViewGroup parent = (ViewGroup) banner.getParent();
        if (parent != null) parent.removeView(banner);
        banner = null;
    }

    public void onHomePaused() {
        homeResumed = false;
        awaitingStudyStop = false;
        if (banner != null) banner.pause();
    }

    public void onHomeDestroyed(MainActivity activity) {
        if (home.get() == activity) {
            homeResumed = false;
            awaitingStudyStop = false;
            destroyBanner();
            home.clear();
            bannerSlot.clear();
        }
    }

    /** Safe display is possible ONLY if the home resumed after a real study screen. */
    public void onHomeResumed(MainActivity activity, boolean returnedFromStudy) {
        homeResumed = true;
        if (returnedFromStudy) awaitingStudyStop = true;
        if (banner != null) banner.resume();
        // Activity.onResume can precede the old player's onStop. Wait for it.
        if (awaitingStudyStop) onStudyScreenStopped();
        else if (adsReady) preloadInterstitial(activity);
    }

    /** Called after the study Activity has really stopped and released playback. */
    public void onStudyScreenStopped() {
        if (!homeResumed || !awaitingStudyStop) return;
        MainActivity activity = home.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            awaitingStudyStop = false;
            return;
        }
        if (((PWStudyApplication) activity.getApplication()).hasOpenStudyScreen()) return;
        awaitingStudyStop = false;
        if (!adsReady) return;
        if (canShowNow(activity)) showAtStudyBreak(activity);
        else preloadInterstitial(activity);
    }

    private boolean canShowNow(MainActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed() || interstitialShowing) return false;
        if (!((PWStudyApplication) activity.getApplication()).adClock().canShowInterstitial()) return false;
        // Ads become stale after one hour; don't show an expired preload.
        return interstitial != null &&
                SystemClock.elapsedRealtime() - interstitialLoadedAt < MAX_AD_AGE_MS;
    }

    private void showAtStudyBreak(MainActivity activity) {
        InterstitialAd loaded = interstitial;
        if (loaded == null) return;
        interstitial = null;
        interstitialShowing = true;
        loaded.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() {
                ((PWStudyApplication) activity.getApplication()).adClock().onInterstitialShown();
            }
            @Override public void onAdDismissedFullScreenContent() {
                interstitialShowing = false;
                if (!activity.isFinishing()) preloadInterstitial(activity);
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                interstitialShowing = false;
                Log.w(TAG, "Test interstitial failed to show: " + error.getMessage());
                if (!activity.isFinishing()) preloadInterstitial(activity);
            }
        });
        loaded.show(activity);
    }

    /** Prepare ads ahead of a *future* break, never show upon load completion. */
    private void preloadInterstitial(MainActivity activity) {
        if (!adsReady || interstitialLoading || interstitialShowing ||
                activity.isFinishing() || activity.isDestroyed()) return;
        if (interstitial != null) {
            if (SystemClock.elapsedRealtime() - interstitialLoadedAt < MAX_AD_AGE_MS) return;
            interstitial = null;
        }
        if (((PWStudyApplication) activity.getApplication()).adClock().remainingToday() == 0) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, TEST_INTERSTITIAL, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(InterstitialAd loaded) {
                        interstitialLoading = false;
                        interstitial = loaded;
                        interstitialLoadedAt = SystemClock.elapsedRealtime();
                        // Intentionally do not show until the NEXT natural break.
                    }
                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        interstitialLoading = false;
                        interstitial = null;
                        Log.w(TAG, "Test interstitial unavailable: " + error.getMessage());
                    }
                });
    }

    /** In Settings, a privacy choice is available when UMP requires one. */
    public void openPrivacyOptions(Activity activity) {
        if (consent == null ||
                consent.getPrivacyOptionsRequirementStatus()
                        != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Toast.makeText(activity, "No ad privacy choices are required currently", Toast.LENGTH_SHORT).show();
            return;
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            if (error != null) {
                Toast.makeText(activity, "Privacy options unavailable. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
