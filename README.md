# MC2 ISLAMIC TV — Android

An offline-first Android video-template editor project.

## Current development status

This repository now contains the initial native Android/Jetpack Compose project scaffold, a first Home/Editor UI pass, local recent-project metadata, video selection, and an initial MP4 source-transcode path using AndroidX Media3 Transformer.

**This is not yet the completed editor and no APK has been verified yet.** The timeline rows are currently a UI foundation; independent editable clip timing, full template/video compositing, crop and lighting effects, complete text overlay rendering, robust deletion of app-managed media, and quality/device test coverage still need implementation and verification. The initial export currently transcodes the selected source video and does not yet burn the MC2 template and text layers into the exported picture. Do not treat this version as production-ready.

## Product requirements

- App name: **MC2 ISLAMIC TV**
- Offline editing and local project storage
- Home screen with recent projects, create, open, delete, and share
- Editor with source video in region A, description in region B, teacher name in region C
- Fixed channel branding: MC2 ISLAMIC TV
- Independent timeline tracks for video, description, teacher name, and additional text
- MP4 export to `Movies/MC2 ISLAMIC TV`
- Preserve source quality by default where device support allows

## Build a debug APK

The GitHub Actions workflow `.github/workflows/android.yml` builds `app-debug.apk` on pushes to `main` and manual workflow dispatch. When a build succeeds, download the artifact named `MC2-ISLAMIC-TV-debug-apk` from the workflow run's Artifacts section.

Local requirements: JDK 17, Android SDK platform 35, Android Build Tools 35.0.0, Gradle 8.9.

## Important export note

Fast, loss-minimizing export depends on the Android device's hardware codecs, input codec, resolution, frame rate, and available memory. The final compositor must be tested on real Android devices before promising performance or claiming that all overlays and template elements are included in an MP4.
