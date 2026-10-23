# DOCSY Installation & Sideload Guide (INSTALL.md)

This guide documents the procedures for signing, installing, upgrading, and handling Play Protect when sideloading Docsy.

---

## 1. Release Signing & Key Management

### Dedicated Keystore Configuration
Docsy requires strict cryptographic signing for all release builds:
- **Keystore File**: `docsy-release-key.jks`
- **Configuration File**: `keystore.properties` (must remain private and uncommitted)
- **Signature Schemes**: v1 (Jar Signature), v2 (Full APK Signature), and v3/v4 schemes are strictly enabled.

### keystore.properties Format
Place `keystore.properties` in the project root:
```properties
storeFile=docsy-release-key.jks
storePassword=YOUR_SECURE_STORE_PASSWORD
keyAlias=docsy
keyPassword=YOUR_SECURE_KEY_PASSWORD
```

### Critical Rules for Upgrades
1. **Never Change or Lose the Signing Key**: Android enforces package signature continuity. If an APK is signed with a different key, Android will refuse to update the existing installation, requiring a complete uninstall which wipes local databases.
2. **Back Up Your Key**: Keep an encrypted, offline backup of `docsy-release-key.jks` and `keystore.properties`.
3. **Monotonic Version Code**: Every subsequent update must increment `versionCode` (e.g., 2 -> 3 -> 4). Android Package Manager strictly rejects APKs with equal or lower version codes than what is currently installed.

---

## 2. Play Protect Sideload Instructions

### Why Play Protect Flags Sideloaded APKs
Google Play Protect is designed to scan APKs downloaded from outside the Google Play Store. Because Docsy accesses user documents (`MANAGE_EXTERNAL_STORAGE`), contacts (`READ_CONTACTS`), and SMS (`READ_SMS`) entirely on-device, and is not distributed through Google Play:
- **No code or manifest flag can unilaterally whitelist an app with Play Protect.**
- Google Play Protect assigns trust to apps based on developer reputation within the Play Console and widespread install volume.

### Step-by-Step Installation Steps on Device
When installing the APK via a file manager, browser, or USB adb:
1. Tap the downloaded `.apk` file (e.g. `Docsy-full-release.apk`).
2. If prompted by Android with **"Install unknown apps"**, toggle **"Allow from this source"** for your file manager or browser.
3. If Google Play Protect presents the warning **"Blocked by Play Protect"** or **"Unrecognized app"**:
   - Tap **"More details"** (small text below the warning).
   - Tap **"Install anyway"**.
4. When prompted **"Send app for scanning?"**, tap **"Scan app"** or **"Don't send"** per your preference (scanning verifies to Google that the app contains no malware).
5. Open Docsy and complete onboarding.

---

## 3. Product Flavor Selection

Docsy provides three distinct build flavors:

| Flavor | Target Audience / Use Case | Included Capabilities |
|---|---|---|
| **full** | Full personal on-device assistant | Complete device reading: Files & PDFs, Photos, Videos, SMS, Call Logs, Contacts, Steps, Telemetry |
| **noSmsCalls** | Users testing with lighter permission profile | Files, Media, Contacts, Steps, Telemetry (Excludes `READ_SMS` and `READ_CALL_LOG`) |
| **filesOnly** | Maximum restricted environment | Files via Storage Access Framework / MediaStore only (Excludes SMS, Call Log, and `MANAGE_EXTERNAL_STORAGE`) |

---

## 4. Verification

After generating a release APK, verify its certificate and signing schemes:
```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/full/release/app-full-release.apk
```
Ensure that:
- `Signer #1 certificate DN` matches your Docsy release key.
- `Verified using v1 scheme (JAR signing): true`
- `Verified using v2 scheme (APK Signature Scheme v2): true`
- `Verified using v3 scheme (APK Signature Scheme v3): true`
