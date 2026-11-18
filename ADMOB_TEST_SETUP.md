# PWStudy AdMob test integration

Development branch: `pwstudy-admob-test-2026-11-18`. This integration uses **Google's public sample App ID and test ad units exclusively**. No real advertising income is possible from this build.

## Schedule and placement

- Home contains an adaptive test banner in a dedicated slot above bottom navigation.
- Interstitial eligibility requires **60 total minutes of app-foreground use** since the prior displayed interstitial. First interstitial likewise requires 60 minutes.
- No more than **4 interstitials per local calendar day**. Saved locally with SharedPreferences.
- Ad requests do **not** interrupt a PDF or video. A ready interstitial may be shown only when MainActivity resumes following an actual PdfActivity or PlayerActivity session.
- First launch, returning from device background, tapping settings, Telegram views, and normal home browsing **never trigger full-screen ads**.
- If an interstitial is not loaded or is stale when the natural break occurs, that break is skipped; the app never delays or blocks study.
- Running in the background does not increase the required active-use counter.
- An ad display is counted only in `onAdShowedFullScreenContent`, not on a failed request.
- Failed network/consent/SDK initialization is non-blocking, leaving study screens usable.
- UMP requests up-to-date consent information on launch and exposes a privacy options link from Settings when a form is required.

## Compatibility / dependencies

The Android app keeps `minSdk 23`. The Google Mobile Ads **legacy SDK 25.4.0** is intentionally selected because 25.5.0+ requires `minSdk 24`. UMP SDK version 4.0.0 handles eligible consent flows. New integrations should re-evaluate the preferred Next-Gen SDK and compatibility before deployment.

## Manual validation checklist

1. Run both `gradle assembleDebug` and `gradle -PpwstudyPreview assembleDebug`.
2. Open Home after first install. Expect a TEST banner when ads/consent/network are available. No launch interstitial.
3. Open PDF and video, pan/zoom/play/rotate; no full-screen ad during any active study screen.
4. Return Home before 60 foreground minutes; no interstitial.
5. After 60 foreground minutes, return Home from an actual PDF/video session; if a fresh test interstitial is loaded, show exactly once.
6. Close the interstitial; return to reading and back within another 60 active minutes; no interstitial.
7. After four successful shows in one local calendar day, no more full-screen ads that day.
8. Lock/background the phone and verify its inactive duration does not advance the 60-minute count.
9. Verify home banner does not cover bottom navigation or offline study content, and navigation/settings continue working.
10. Test consent required/not required and offline/no network cases on a device. Verify Privacy choices in Settings when required.

## Real AdMob launch prerequisites

1. Owner creates and verifies a Google AdMob account (Google account, identity and payment information).
2. Register PWStudy as an Android app using its **actual production package name**, `com.study.pwjarvis`.
3. Create **banner** and **interstitial** ad units and obtain the production **App ID** and **Ad Unit IDs**.
4. Replace the sample App ID in AndroidManifest.xml and both sample ad unit constants in `StudyAdManager.java`. Do this only in a separately reviewed, production-ready change; leave this test branch test-only.
5. Configure user messages in AdMob Privacy & messaging (and age-related data policies as appropriate), a published privacy policy, accurate Play Store data-safety and advertising disclosures.
6. Set up an accessible developer website with `app-ads.txt` if required, then complete AdMob app readiness and verification. Configure account-level frequency caps as defense in depth.
7. Verify rights to monetize the app's **own UI and tools** rather than unlicensed third-party Telegram/lecture/PDF content.
8. Test using Google's sample units or registered test devices before any production traffic. Do not click your own live ads.

**APK size:** Not set or padded to a numeric target; record real debug/preview APK file sizes from GitHub Actions output.
