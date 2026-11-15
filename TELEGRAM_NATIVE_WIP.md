# PWStudy native Telegram proof of concept — NOT RELEASE READY

This is isolated work in the pwstudy-telegram-native-wip branch, not an integrated or functioning app release. It uses the official TDLib JSONJava interface pinned at `c8828d50f5eac2fc7089ce1089d7ce9095c61b5e`; no Telegram WebView and no Telegram API credentials are included. The seven channel names and existing links must be passed privately by the non-exported in-app caller through two `ArrayList<String>` intent extras named by `NativeTelegramActivity.EXTRA_CHANNEL_NAMES` and `EXTRA_CHANNEL_LINKS`. They must be exactly seven `https://t.me` or `https://telegram.me` links. The new files deliberately do not hardcode the private links.

## What exists in the prototype

- `NativeTelegramActivity.java`: app setup, phone/code/email/password state screens; seven-link validation; resolve public username or inspect private invite; user-confirmed join/request; load channel history; basic text, PDF and video handling, including video documents sent with a video MIME type or MP4/M4V filename; request a TDLib download and pass its internal-file path to existing `PdfActivity` or `PlayerActivity`.
- `PrivateTdStore.java`: AES-GCM envelope encrypted with an Android Keystore key for application credentials and random TDLib database key. The envelope and TDLib directory use `getNoBackupFilesDir()`; the app does not transmit these values to GitHub or print Telegram payloads to logs.
- `TdJsonBridge.java`: a single ordered receiver, main-thread delivery, callbacks associated with JSON `@extra`, an explicit timeout error callback, and TDLib logging disabled.
- `JsonClient.java`: unmodified official binding from the pinned commit, including its Boost license notice.
- Sponsored messages: fetch the official list before history, show text/title and sponsored/recommended label, send `viewMessages` only when the entire text view is visible, and let the user explicitly confirm opening HTTPS sponsor URLs outside PWStudy before sending `clickChatSponsoredMessage`. When the spacing is zero, it appends one sponsored message after history is exhausted. A sponsorship-request error only bypasses sponsorship after TDLib identifies the chat as a non-channel group; otherwise the user can retry. This is partial, not complete sponsored-message support.
- `ci/build_tdlib.sh`: candidate CI build script. This has not run and must not replace a working workflow without review.

## How to integrate for a test build (not yet performed)

1. The prototype sources are present under `app/src/main/java` on this work-in-progress branch; declare `.telegram.NativeTelegramActivity` with `android:exported="false"` in the manifest.
2. The existing in-app Telegram entry screen should pass the existing seven names and URLs in the matching intent extras to this non-exported Activity. Do not put private links into new documentation, logs or public commits. Remove the WebView entry only after the native path has been validated; retain other app functionality.
3. Install `gperf`, `php`, `perl`, `make`, `ninja`, JDK 17, Android SDK platform 34, CMake 3.22.1, and NDK 28.2.13676358 on GitHub Actions. Run `ci/build_tdlib.sh` before `gradle assembleDebug`, and use AGP 8.5.1+ (current project uses 8.6.1). Pin all actions and verify third-party source archive hashes for a real build. `build-openssl.sh` still downloads an OpenSSL tag: verify its provenance and immutable content before release.
4. Verify all four `.so` files are packaged, are 16-KB page compatible, and load on supported ABIs. The candidate uses NDK r28's 16-KB defaults; this needs actual ELF/APK checks. See https://developer.android.com/guide/practices/page-sizes.
5. Obtain the app's own Telegram `api_id` and `api_hash` at https://my.telegram.org. Enter them only on the phone. Never send the verification code, 2FA password, session data, or API hash in chat or public GitHub.

## Verification already done

- Java source syntax parsed with the JDK compiler API.
- Java compiled against hand-written minimal Android/org.json stubs. This catches Java type/syntax mistakes only; it is **not** an Android SDK, Gradle, native binary, APK, runtime or security test.
- `bash -n` checked the candidate build script.
- Host-stub storage checks verify that an existing database or backup is not mistaken for a fresh install; actual Android `AtomicFile` backup recovery and Keystore operation remain untested.

## Known blockers before replacing the existing client

1. CI build, APK packaging, JNI loading and Android runtime have not been run. A real Android device/emulator and user account are required to test initial login, retained session, each invite/membership, history, media download, error handling and revocation.
2. Current GitHub Actions creates a different debug signing key on each run. A durable signing strategy and safe migration are needed to retain the existing app/session/offline data. Do not tell the user to uninstall.
3. Sponsored-message support is incomplete: media rendering, reporting, non-HTTPS/Telegram sponsored destinations, media presentation, exact sponsorship visibility/timing across pagination, and all required interactions need implementation and device validation. The seven-channel-only design must be reviewed against Telegram's API terms: https://core.telegram.org/api/terms.
4. More real Telegram authorization states, retries, account logout/recovery, connectivity errors, scoped lifecycle/cancellation, unsupported message types, media progress/cancel, background handling and accessible UI remain to be built/tested. In particular this prototype cannot register a new Telegram account or handle every extra confirmation/purchase state.
5. End-to-end tests must verify TDLib's actual JSON schema at the pinned revision and proper read/view reporting. See https://core.telegram.org/tdlib/getting-started. The user may be asked for verification again if Telegram revokes or expires the session.
6. All channels must actually be accessible to the signed-in account; private invitations may expire, be revoked, require approval, or be paid. The prototype does not automatically join channels or purchase subscriptions.
7. The prototype's simplified UI is not intended for deployment. Security/privacy review and careful handling of protected content are required before production.
