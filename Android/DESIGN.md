# Gallery interface redesign

The Android interface keeps one conversation for text, voice, images, skills, and phone actions. The model still chooses the relevant tools automatically.

## Design system

- Warm ivory surfaces and a restrained teal accent in light mode; deep green-black surfaces and mint accents in dark mode.
- System typography, clear heading levels, consistent 20–28dp containers, and 48dp action targets.
- A separate model-selector row prevents navigation, history, and model settings from overlapping.
- Matching launcher and launch-screen colors.

## Everyday flows

- **Start:** A model setup card explains the download and its size. Download progress and failures appear with the existing download controls.
- **Chat:** Starter cards fill an editable draft. The composer groups attachments, microphone, and send; written replies use readable spacing. Audio recording and playback retain their existing behavior.
- **Models:** Search the model library, filter by on-device or imported models, browse Hugging Face, and import from a local file or URL. Capabilities and download state remain visible.
- **History:** Saved conversations show dates; new chat and deletion controls have clear labels and confirmation.
- **Settings:** Appearance, analytics, model-download access, and legal information have separate sections. Access tokens are masked while entering them.
- **Skills and connections:** Search, enable, import, and configure tools in matching sheets. Skills remain automatically available to the model.
- **Notifications:** Scheduled reminders have readable grouping, local date/time, and an explanatory empty state.

## Validation

The redesign includes Compose instrumentation tests for starter draft callbacks, disabled suggestions, and scrolling at 320dp width with 2× font size. Existing unit tests cover conversation ownership, routing, audio inputs/history, and transcription policy. Validated on September 27, 2026:

- Debug and release builds succeeded; full debug lint completed with 0 errors and 193 warnings.
- All 57 unit tests and 6 Android instrumentation tests passed.
- Android 16 emulator: model library and on-device empty state, navigation menu, settings, and light/dark appearance.
- Samsung Galaxy S25 Ultra over wireless ADB: existing model loaded, starter draft editing, a live GPU response (2 + 2 → 4), saved history, new chat, microphone recording/stop/discard, skills, and settings.
- The final Beta 3 release APK was installed with `adb install -r`, preserving the existing app data. APK signature verification and 16 KB zip alignment passed.

APK: `src/app/build/outputs/apk/release/app-release.apk`

SHA-256 (1.0.0-beta.3, build 47): `785a694d4d52c447617be1a8c6f9f64b0f13383c7a9b710214e0e684870d2a3d`

Hosted CI, paid/gated downloads, connected tool execution, and spoken-answer accuracy were not revalidated for this visual change. Microphone UI was exercised without submitting its recording.

The redesign does not change model inference implementations, network-tool configuration, or the existing model-dependent transcription limitations documented in the Android README.
