# PW Jarvis Study App — शुरुआती Android प्रोजेक्ट

यह पहला starter build है, तैयार APK नहीं।

## अभी शामिल
- PW Jarvis वेबसाइट के लिए WebView स्क्रीन
- WebView में JavaScript और session cookies
- अलग Offline Library स्क्रीन
- Media3/ExoPlayer आधारित वीडियो प्लेयर की नींव
- PDF खोलने पर अलग PDF Activity का शुरुआती ढाँचा
- दो Launcher Activity entries (होम स्क्रीन पर दो आइकन का परीक्षण)

## अभी अधूरा
- असली डाउनलोड मैनेजर और Subject/Chapter tagging
- पूरा PDF page renderer, zoom और bookmarks
- वेबसाइट के वीडियो लिंक का वैध/अनुमत download integration
- वेबसाइट के login/CAPTCHA flow की device पर जाँच
- दोनों icons के अलग नाम/आइकन assets
- Android SDK/Gradle से APK build और device testing

## जरूरी
- Login/CAPTCHA को bypass नहीं किया जाता। WebView केवल वेबसाइट के वैध session को बनाए रखने की कोशिश करता है।
- Protected/DRM वीडियो डाउनलोड नहीं होंगे; केवल वेबसाइट/प्रदाता द्वारा अनुमत direct downloads जोड़े जाएँगे।
- Android 15 target compatibility और storage permission की अंतिम जाँच build के समय करनी होगी।
