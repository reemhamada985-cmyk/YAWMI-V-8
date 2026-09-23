YAWMY Android v1.0.8

This project is designed to build on GitHub Actions without Android Studio.

Important:
- The app is offline-first. Required Quran, hadith, azkar, stories, tafseer, names and Mushaf page assets are downloaded during the CI build and copied inside the APK.
- The project uses local WebViewAssetLoader content at https://appassets.androidplatform.net/assets/.
- The 604-page Madinah Mushaf is bundled as compact PNG pages from tarekeldeeb/madina_images (w1024).
- Prayer notifications use Android Notification Channels and POST_NOTIFICATIONS.
- Prayer timing uses SCHEDULE_EXACT_ALARM when granted, with an inexact fallback otherwise.
- Adhan playback uses a local audio resource in a foreground mediaPlayback service.

GitHub build:
1. Upload the project to a new repository.
2. Open Actions.
3. Run "Build YAWMY APK v1.0.8".
4. Download the artifact "YAWMY-debug-apk-v1.0.8".

The APK size is intentionally determined by the real packaged resources; the build does not add dummy padding.
