# Building Audio Converter v1.5 from source

This is a source-only distribution. No APK or signing credential is provided.

## Prerequisites

- Android Studio with Android SDK Platform 35 and Build Tools 35
- JDK 17
- Gradle 8.9, or an Android Studio installation that can provision the compatible Gradle version
- Internet access for the first Gradle dependency download
- Android arm64-v8a device (Android 8.0 / API 26 or newer)

**Note:** The source snapshot does not include a Gradle wrapper. Install Gradle 8.9 or use Android Studio to import and configure the project.

## Android Studio

1. Clone or download this repository.
2. Open the repository folder as a project in Android Studio.
3. Allow Gradle to sync and install any missing Android SDK components.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)** to build a debug-signed APK.
5. For an independently signed release, select **Build > Generate Signed Bundle / APK**, choose **APK**, and create or select your own secure keystore.

## Command line

From the repository root, with JDK 17, Gradle 8.9 and Android SDK 35 configured:

```bash
gradle --no-daemon :app:assembleDebug
```

The debug APK should appear at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is automatically signed with the build machine's debug key. Never rely on a debug keystore for a permanent release signing identity.

For production distribution, create a private release keystore, use Android Studio to create a signed APK, and retain a secure backup. Do not commit keystores, passwords, or credentials.

## Known issue in v1.5

Screen orientation changes or split-screen resizing can cause the file/folder selection to disappear from the UI. Keep the tablet in landscape mode and avoid resizing while using the app.

## Notes

This Android app targets ARM64. Dependencies include a maintained FFmpeg Kit fork; verify upstream availability and applicable library and codec licenses when building and redistributing. The project has not been independently rebuilt or tested as part of the source publication.
