# Android Audio Converter

Offline audio converter for Android tablets and ARM64 devices.

## Version 1.5 — source release

This repository contains a clean source-code snapshot of **Audio Converter v1.5**, separate from the private development repository. No development history, prebuilt APK, or signing keys are included.

### Features
- OPUS / M4A / FLAC to MP3 (up to 320 kbps)
- OPUS / M4A / MP3 / FLAC to WAV (16-bit or 24-bit PCM)
- Batch file and folder selection
- Output through Android Storage Access Framework, including accessible microSD locations
- Offline conversion

### Known issue: orientation and resizing
Changing orientation or entering split-screen mode can cause selected files or folders to disappear from the interface. **Workaround:** keep the tablet in landscape orientation and avoid resizing while converting.

Orientation Lock and Conversion Control Lock are planned improvements, **not features of v1.5**.

### Build
See [BUILDING.md](BUILDING.md). This is a **source-only** release; users build and sign their own APKs.

### Requirements
Android 8.0+ (API 26), ARM64 (arm64-v8a), Java 17, Android SDK 35, Gradle 8.9.

### Licensing
This project depends on AndroidX, Material Components and a maintained FFmpeg Kit fork. Review the licenses and notices for those libraries and codecs before distributing binaries. No project-wide license is granted by this repository yet; contact the repository owner for permission beyond GitHub's default viewing/forking permissions.
