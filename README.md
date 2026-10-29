# Bhagavad Geeta

A scripture reading app built with Kotlin Multiplatform and Compose Multiplatform. The shared reading experience targets Android and iOS, with platform-specific integrations for storage, audio, notifications, and system UI.

## Features

- Browse the Bhagavad Gita's 18 chapters and read verse text, transliteration, word meanings, translations, and commentaries.
- See a daily featured verse.
- Play bundled verse recitations.
- Save bookmarks and highlights, and resume reading progress.
- Adjust text size, choose a reading mode, and switch between light and dark themes.
- Schedule reading reminders.
- Read content offline; chapter data and audio are bundled with the app.

## Project layout

- `shared/` — shared Compose UI, scripture content, and common logic.
  - `src/commonMain/` — shared UI, repository, models, and bundled content.
  - `src/androidMain/` and `src/iosMain/` — platform implementations.
- `androidApp/` — Android application entry point and resources.
- `iosApp/` — Xcode project and SwiftUI entry point.
- `tools/import_gita_content.py` — utility for importing scripture content.

## Requirements

- Android Studio with the Android SDK configured for this project.
- A compatible JDK and the Gradle version specified by the included wrapper.
- Xcode for building and running the iOS app on macOS.

## Build and run

From this directory, build the Android debug app with:

```bash
./gradlew :androidApp:assembleDebug
```

Open `iosApp/iosApp.xcodeproj` in Xcode to build and run the iOS app. Select an available iOS simulator or a configured device.

## Scripture content

The chapter catalog and chapter JSON files are under `shared/src/commonMain/composeResources/files/content/`. Recitation audio is stored under `shared/src/commonMain/composeResources/files/verse_recitation/`. The content importer is in `tools/`; review the source and expected input format before using it to update bundled data.

## Notes

Reading content and recitation audio are packaged with the app. Notifications are scheduled locally by the platform implementations. No external content service is required for the bundled reading experience.
