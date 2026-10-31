PW JARVIS STUDY - SOURCE UPDATE 0.2

इस ZIP में app के नए source files हैं।

सुरक्षा:
- पहले से मौजूद app_backup_20261031_1146 फ़ोल्डर को न हटाएँ।
- बदलाव pwstudy-v1 branch में ही करें।
- यह ZIP root project files को नहीं बदलती; केवल app के source/config files देती है।

इंस्टॉल:
1. ZIP को Termux के Download folder में रखें।
2. Termux में चलाएँ:
   cd ~/PWStudy-New
   unzip -o /sdcard/Download/PWStudy-New-Files.zip
3. फिर जाँचें:
   git status --short
4. Build GitHub Actions से करें या सही Gradle/Android SDK environment में करें।

महत्वपूर्ण सीमाएँ:
- Website के सामान्य HTTP/HTTPS download links को ऐप के Offline Library में सेव किया जाता है।
- कुछ वेबसाइट downloads (जैसे DRM-protected video, encrypted streams, blob URLs या login-restricted content) वेब पेज/सर्वर की पाबंदियों के कारण उपलब्ध नहीं होंगे।
- Video quality selector अभी नहीं जोड़ा गया है; लोकल वीडियो में एक ही encoded quality होती है।
- PDF viewer में page navigation और pinch zoom उपलब्ध हैं।
