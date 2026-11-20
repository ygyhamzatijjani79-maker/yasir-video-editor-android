# MC2 ISLAMIC TV — Android

An offline-first Android video-template editor project.

## Current development status

This repository contains a native Android/Jetpack Compose project scaffold, Home/Editor UI, an A/B/C template preview, an initial Media3 export compositor that burns a green/gold MC2 frame with description and teacher name into the MP4, timestamp-aware extra text overlays, an adjustable brightness/lighting control and crop amount applied during export, a fix to persist crop in the project model, and safer MediaStore export that publishes only complete MP4 files and removes partial outputs on failure, local recent-project metadata, imported-video copies in app-private project folders, project-folder deletion, safe FileProvider sharing, additional text clips with editable start/end seconds, persistence for those clips, and an initial MP4 source-transcode path using AndroidX Media3 Transformer.

**This is not yet the completed editor and no APK has been verified yet.** The additional text clips now have editable start/end time fields and are saved with the project, but the timeline is not yet a complete visual multi-track editor with scrubbing and drag-to-trim. The export compositor now draws an initial 16:9 green/gold A/B/C template layer and includes description, teacher name, fixed MC2 ISLAMIC TV branding, timed extra text clips, and the saved brightness adjustment. This is a first programmatic interpretation, not an exact recreation of the supplied ornate artwork. Exact supplied artwork integration, drag-to-trim timeline, device/build verification, and full quality testing remain outstanding. Rendering overlays requires video re-encoding, so export speed and preservation of source frame rate/resolution depend on device codecs and must be verified on real devices. Do not treat this version as production-ready.

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
