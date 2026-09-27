# Gallery 1.0.0 Beta 4

Beta 4 makes Android beta problems easier to capture and report. Local diagnostics record while you use Gallery, preserve the latest recovered crash capture, and export a ZIP directly to your phone. The unified conversation, automatic Agent Skills and phone controls, voice-input fixes, and interface from earlier betas remain included.

## What changed

- **Settings → Beta diagnostics** shows recording status, retained storage, available app-log capture, and a live log viewer. Beta recording starts automatically and can be paused or cleared.
- Add a reproduction note and tap **Save ZIP** to choose a destination in Android's file picker. Export works while recording continues; there is no need to stop first.
- Capture includes app events, available app-process logcat and native runtime logs, lifecycle and inference metadata, memory and responsiveness observations, device/runtime information, and available Android app-exit/ANR details.
- Logs survive app restarts. On the next launch after a recorded fatal exception or an Android-reported crash/ANR, the preceding capture is preserved separately and included as `previous-crash.zip` in exports.
- Long-press the Gallery launcher icon and choose **Diagnostics** to open diagnostics without initializing chat. This provides a recovery route when a chat-screen problem prevents access to Settings.

See the [bug-reporting guide](https://github.com/culpen90/gallery/blob/v1.0.0-beta.4/Bug_Reporting_Guide.md) for instructions.

## Install

Download **gallery-1.0.0-beta.4.apk** from this release on an **Android 12 or newer** device, allow installation from your browser or file manager when Android asks, and open the APK. A compatible model must be downloaded or imported separately for chat; model weights are not included. Diagnostics can be opened without loading a model.

This installable release-variant APK uses the same local Android test signing key as beta 3, allowing an in-place update that preserves app data. It is not a production-signed or Play Store release. The package ID remains `com.google.aiedge.gallery`; installations signed with a different key cannot be updated with this APK.

The app version is **1.0.0-beta.4**, with Android internal version code **48**. Use the attached `SHA256SUMS` to verify the APK download.

## Retention and privacy

Logs rotate at **20 MiB**, plus **one** separately retained crash archive. Old logs rotate out; the live viewer, long records, and ANR traces are capped. Clear removes the retained in-app capture and recovery archive, but does not delete ZIPs already saved or shared.

Collection remains local and nothing is automatically uploaded. Common credential formats are masked on a best-effort basis. Existing app/native logs and issue notes can still contain personal conversation or tool text; review reports before sharing. Chat databases, model weights, photos, audio recordings, credential stores, and other apps' logs are not copied.

## Beta limitations

- App-process logcat and Android exit traces may be unavailable or incomplete on some phones. Busy logging can drop queued lines, and sudden kills or power loss can lose final queued or buffered records.
- Recording cannot continue while Android freezes or kills the process. Recovery requires the application process to start; native crash/ANR recovery also depends on Android's available exit records. Native tombstone protobuf files are not included. A full device-wide Android bug report remains a separate option.
- Main-thread stall observations are best effort, not Android-confirmed ANRs. Diagnostics does not fix the underlying crash or inference failure.
- Saved recordings remain playable after reopening a chat, but must be sent again for the model to hear them. Gemma-4-E2B-it can answer a spoken transcription request instead of returning a verbatim transcript; explicit transcription still disables tool execution.
- Voice and transcription require an audio-capable model. Skills and phone controls require a model supported by the agent runtime, installed phone apps, and appropriate permissions. Contact, email, and calendar actions open drafts for review.
- Hugging Face OAuth remains unconfigured in this fork. Use ungated models or import compatible files obtained separately. Other models, devices, connected tools, and physical phone actions need broader testing.

## Verification

- Beta 4 debug and release APKs assembled successfully. **80 JVM tests passed**, covering storage/rotation, restart persistence, Unicode boundaries, redaction, ZIP safety/content, and destination-stream failures alongside the existing app tests.
- **10 diagnostics instrumentation tests passed** on an Android 16 emulator, including app logcat, immediate export, pause/clear behavior, persistence, note restoration, live viewing, save success/failure/retry, and retaining dropped-line counts while paused.
- Debug lint passed with **0 errors and 200 warnings**.
- The release APK passed archive integrity, package/version, non-debuggable configuration, APK v2 signature, and 16 KiB ZIP-alignment checks. Its signing certificate matches beta 3, and the bundled third-party license resources were verified.
- The exact release APK was installed in place on a Samsung Galaxy S25 Ultra (SM-S938U) running Android 16. Normal chat opened with the existing Gemma model, and **Menu → Settings → Beta diagnostics** recorded app/native logs.
- On that release APK, **Save ZIP** used Android's file picker to save into Downloads while recording continued, and the success message appeared. The retrieved ZIP passed integrity checks and contained app logcat, version `1.0.0-beta.4` / code `48`, release-build/device metadata, and capture status showing active recording with no recorder error.

Earlier development-build checks also verified the issue note and enabled launcher shortcut.

Deliberate Java and native crashes on the emulator were also checked before the version update. Relaunch recovered the capture, an exported nested crash ZIP contained the fatal exception, and Android reported the native crash exit reason. These checks do not establish device-specific ANR behavior or a particular inference-engine failure.

These are local checks; no hosted CI result is claimed. Existing SDK/toolchain and deprecation warnings remain. Voice-answer accuracy and connected tool execution were not revalidated for this diagnostics change.
