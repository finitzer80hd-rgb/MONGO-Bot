# Monopoly GO Assistant (Android)

Native Android app for local screen-text recognition and contextual suggestions, plus an optional user-triggered one-tap overlay. It does not play unattended or automatically loop gameplay actions.

## Build
1. Install Android Studio and JDK 17.
2. Open this repository as a project.
3. Install Android SDK Platform 35 if prompted and allow Gradle sync.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
6. Install the APK on Android and launch it.
7. For screen monitoring, press **Start screen monitoring** and approve Android's screen-capture prompt.
8. To enable the one-tap overlay, open Android **Settings > Accessibility > Downloaded/Installed apps** (wording varies by device), select **Tap Overlay Service** or **Monopoly GO Assistant**, and enable it.
9. Open Monopoly GO. A draggable **DRAG • TAP ONCE** pill appears. Drag it over a control and tap the pill to send one tap to that screen position. Press × to hide it; disable the accessibility service in Settings to turn it off completely.

## Requirements
- Android Studio compatible with Android Gradle Plugin 8.7.3
- JDK 17
- Android SDK 35
- Android 8.0+ (API 26+)

## Safety and limitations
- Accessibility permission is powerful. Enable it only for this app and disable it when not needed.
- The overlay does not infer button locations or choose game actions. It only performs one tap where you place it, after you touch the overlay.
- There is no unattended auto-play loop, dice farming, or server interaction.
- Screen capture uses Android MediaProjection consent and ML Kit text recognition, processed on-device.
- OCR suggestions are heuristic and may miss graphics, board positions, dice totals, or game state.
- This repository contains source code, not a prebuilt APK. Build and test on your device.
