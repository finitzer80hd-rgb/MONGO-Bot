# Monopoly GO Assistant (Android)

Native Android starter app for local screen-text recognition and contextual suggestions. It does not tap the screen or play Monopoly GO automatically.

## Build
1. Install Android Studio and JDK 17.
2. Open this repository as a project.
3. Install Android SDK Platform 35 if prompted and allow Gradle sync.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
6. Install the APK on Android, open the app, press **Start screen monitoring**, and approve Android's screen-capture prompt.
7. Switch to Monopoly GO; return to the assistant to inspect recognized text and suggestions.

## Requirements
- Android Studio with Android Gradle Plugin 8.7.3 support
- JDK 17
- Android SDK 35
- Android 8.0+ (API 26+)

## Limitations
- This repository contains source code, not a prebuilt APK.
- The screen capture uses Android MediaProjection consent and ML Kit text recognition, processed on-device.
- OCR and keyword suggestions are heuristic and may miss graphics, board positions, dice totals, or game state.
- The app does not automate gameplay, interact with game servers, or upload captured frames.
