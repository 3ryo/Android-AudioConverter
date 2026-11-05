# Audio Converter v1.5 — source release

Initial public source release based on the tested v1.5 code snapshot.

## Features
- Batch audio conversion
- OPUS / M4A / FLAC to MP3 up to 320 kbps
- OPUS / M4A / MP3 / FLAC to 16-bit or 24-bit WAV
- Android document-picker output including supported removable storage
- Offline conversion

## Known issue
**Orientation / resizing:** Selected files or folders may disappear when the device rotates or split-screen changes the app's orientation or dimensions. **Workaround:** keep the tablet in landscape orientation, and reselect files after an unintended resize.

## Distribution
Source code only. No prebuilt APK and no signing key. Follow [BUILDING.md](BUILDING.md).

The orientation-lock and conversion-controls-lock improvements are being developed after v1.5 and are not included in this release.
