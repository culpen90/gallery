# Gallery 1.0.0 Beta 3

Beta 3 redesigns the Android app to make everyday chat, model setup, and settings easier to use. Text, voice, Agent Skills, and phone controls still share one conversation, with the model choosing the relevant tools automatically.

## What changed

- A consistent visual design with warm ivory and teal in light mode, deep green and mint in dark mode, clearer typography, and comfortable touch targets.
- A cleaner chat screen with editable starter prompts, grouped attachment and microphone controls, readable messages, and improved recording and playback controls.
- A searchable model library with All, On device, and Imported filters, visible capabilities and download state, and refreshed discovery and import screens.
- Clearer first-model setup, download progress, and error states. A dedicated model-selector row keeps navigation and chat actions easy to reach.
- Refreshed history, settings, model configuration, skills, connected tools, notifications, onboarding, and benchmark screens, including improved scrolling and accessibility labels.
- Matching launcher and splash-screen colors.

The voice-input and saved-history fixes from beta 2 remain included.

## Install

Download **gallery-1.0.0-beta.3.apk** from this release on an **Android 12 or newer** device, allow installation from the browser or file manager when Android asks, and open the APK. Download or import a compatible model separately; model weights are not included.

This installable release-variant APK uses the same local Android test signing key as beta 2, allowing an in-place update that preserves app data. It is not a production-signed or Play Store release. The package ID remains `com.google.aiedge.gallery`; installations signed with a different key cannot be updated with this APK.

The app version is **1.0.0-beta.3**, with Android internal version code **47**.

## Beta limitations

- Saved recordings remain playable after reopening a chat, but the runtime cannot restore their audio into the model's context. Send a recording again when you want the model to discuss it.
- With Gemma-4-E2B-it, transcription can answer a spoken question instead of returning a verbatim transcript. Explicit transcription continues to disable tool execution.
- Voice and transcription require an audio-capable model. Agent Skills and phone controls require a model supported by the agent runtime.
- Contact, email, and calendar actions open drafts for review in the corresponding phone app. Availability depends on installed apps and Android permissions.
- Hugging Face OAuth credentials are not configured in this fork. Use models available without gated sign-in or import compatible model files obtained separately.
- Other models, devices, connected tool execution, and physical phone actions need broader testing. This redesign does not change inference or transcription behavior.

## Verification

- 57 JVM tests passed, and 6 Android instrumentation tests passed, including starter-prompt interactions and a narrow layout with enlarged text.
- Debug and release builds succeeded. Full debug lint reported 0 errors and 193 warnings.
- The APK passed archive, package/version, 16 KB alignment, and APK signature checks. Its signing certificate matches beta 2.
- Android 16 emulator checks covered model browsing, empty states, navigation, settings, and light/dark appearance.
- On a Samsung Galaxy S25 Ultra over wireless debugging, the redesign was checked with an existing model: starter draft editing, a live text response, history, new chat, microphone recording/stop/discard, skills, and settings. These interaction checks preceded the beta 3 version-number change; the beta 3 package was subsequently installed as an in-place update and launched.
- The recording was not submitted during this redesign check; spoken-answer accuracy and connected tool execution were not revalidated. No hosted CI result is claimed; GitHub currently lists no active workflows for this fork.

Use the attached `SHA256SUMS` to verify the APK download.
