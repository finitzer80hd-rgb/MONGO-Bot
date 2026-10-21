# Monopoly GO Assistant (Android)

Native Android app for local screen-text recognition, contextual suggestions, a user-triggered one-tap overlay, and an optional conservative accessibility auto mode.

## Build
1. Install Android Studio and JDK 17.
2. Open this repository as a project.
3. Install Android SDK Platform 35 if prompted and allow Gradle sync.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
6. Install the APK on Android and launch it.
7. For screen monitoring, press **Start screen monitoring** and approve Android's screen-capture prompt.
8. To enable the overlay, open Android **Settings > Accessibility > Downloaded/Installed apps** (wording varies by device), select **Tap Overlay Service** or **Monopoly GO Assistant**, and enable it.
9. Open Monopoly GO. The overlay has **DRAG • TAP ONCE**, **AUTO: OFF**, and close controls. The one-tap helper works where you place it. To try automatic actions, tap **AUTO: OFF** to enable the label-based auto mode; tap again to disable it immediately.

## Auto mode: current scope
- Auto mode is OFF by default and is explicitly toggled by the user.
- It only attempts visible accessibility nodes labeled exactly (or with simple prefixes/suffixes) **roll**, **continue**, **claim**, **collect**, **next**, **done**, **skip**, or **close**.
- It avoids purchase/spend/upgrade/offer controls by using a conservative allowlist, rate-limits taps, and waits for visible accessibility text to change before considering another action.
- Android accessibility must expose the game's controls as text and clickable nodes. If Monopoly GO draws the controls inside a game canvas, the service may not see them; in that case auto mode will do nothing. It is not yet verified as full hands-free gameplay and needs real-device testing.
- Stop auto mode by tapping **AUTO: ON** again, closing the overlay, or disabling the accessibility service in Android Settings.

## Requirements
- Android Studio compatible with Android Gradle Plugin 8.7.3
- JDK 17
- Android SDK 35
- Android 8.0+ (API 26+)

## Safety and limitations
- Accessibility permission is powerful. Enable it only for this app and disable it when not needed.
- Screen capture uses Android MediaProjection consent and ML Kit text recognition, processed on-device.
- OCR suggestions are heuristic and may miss graphics, board positions, dice totals, and game state.
- This repository contains source code, not a prebuilt APK. Build and test on your device.
