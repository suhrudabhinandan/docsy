# Docsy Installation & Play Protect Guide

Docsy is a **100% offline, privacy-first on-device personal assistant**. It does not declare the `INTERNET` permission, makes zero network calls, and sends no data anywhere.

---

## Why Google Play Protect Warns on Sideloading

When you install an APK directly via USB, Bluetooth, or file manager (sideloading) rather than downloading it from the Google Play Store, Google Play Protect displays a warning because the app is signed with a developer key not yet registered on Google's Play Console app scanning registry.

---

## Installation Steps (Phone Sideloading)

### Method 1: Bypassing the Warning (Quickest)
1. Tap the Docsy APK file on your device to open the Android Package Installer.
2. When the **Google Play Protect** warning popup appears, tap **"More details"**.
3. Tap **"Install anyway (unsafe)"**.
4. Docsy will install and launch cleanly.

### Method 2: Allowing Google Play Scan
1. In the Play Protect popup, tap **"Send app for scanning"**.
2. Google Play Protect will analyze the APK binary (verifying zero network activity) and complete installation.

### Method 3: Temporarily Disabling Play Protect Scanning
1. Open the **Google Play Store** app.
2. Tap your **Profile icon** (top right) -> **Play Protect** -> **Settings gear icon** (top right).
3. Toggle off **"Scan apps with Play Protect"**.
4. Install Docsy, then toggle Play Protect back on.

---

## Product Flavors Available

To test Play Protect heuristic responses against specific Android permission sets, Docsy provides three signed release build variants:

1. **`full` (`app-full-release.apk`)**: Full features including on-device SMS, Call Logs, Contacts, Device Info, Health/Steps, and All-Files indexing.
2. **`noSmsCalls` (`app-noSmsCalls-release.apk`)**: Excludes `READ_SMS` and `READ_CALL_LOG` permissions.
3. **`filesOnly` (`app-filesOnly-release.apk`)**: Excludes SMS, Call Logs, `MANAGE_EXTERNAL_STORAGE`, and `QUERY_ALL_PACKAGES` (uses standard MediaStore & SAF only). **Play Store compatible candidate.**

---

## Signing Integrity
- All release builds are signed using the official Docsy Release Key with v1, v2, and v3 signature schemes enabled.
- Every update preserves the same signing key and increments `versionCode` (e.g. `versionCode 5` for version `1.0.4`).
