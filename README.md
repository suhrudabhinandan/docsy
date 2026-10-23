# Docsy

Docsy is an Android app for organizing and understanding documents on-device. The app includes document scanning and extraction, local search and retrieval, reading progress, and a Compose-based interface.

## Build

Open this project directory in [Android Studio](https://developer.android.com/studio) and sync the Gradle project. The app module uses JDK 17 and Android SDK 36.1.

Select one of the app's distribution variants in Android Studio:

- `full`
- `noSmsCalls`
- `filesOnly`
- `offline`

The variants use different manifests and permission sets. Review the applicable manifest under `app/src/` before installing a build.

## Tests

Run the unit tests for the selected variant from Android Studio's Gradle tool window. For example, the `full` variant uses the `testFullDebugUnitTest` task.

## Release signing

Release builds require a local `keystore.properties` file and signing key. See [INSTALL.md](INSTALL.md) for setup and verification instructions. Keep signing credentials private; local environment files and signing keys are excluded by `.gitignore`.
