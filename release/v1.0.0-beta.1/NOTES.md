# Gallery 1.0.0 Beta 1

The first beta release of this fork brings chat, Agent Skills, Mobile Actions, and Audio Scribe into one conversation interface.

## What is included

- One chat screen with model selection and a compact menu.
- Compatible models decide when to answer directly, load an enabled Agent Skill, or use the existing phone controls. No manual action-mode selection is required.
- Phone tools for the flashlight, Wi-Fi settings, Maps, contact drafts, email drafts, calendar event drafts, and the current date and time.
- Voice chat from the microphone, plus explicit transcription through the attachment menu. Audio-capable models support recordings up to 30 seconds and WAV imports.
- Transcription disables tool execution, and audio intent is preserved when conversations are restored.
- A bundled model catalog for first launch when the remote catalog is unavailable. Catalog compatibility is independent of this fork's release numbering.

## Install

Download **gallery-1.0.0-beta.1.apk** from this release on an **Android 12 or newer** device, allow installation from the browser or file manager when Android asks, and open the APK. A compatible model must be downloaded or imported separately; model weights are not included.

This is an installable release-variant APK signed with the project's local Android test key. It is a beta, not a production-signed or Play Store release. It retains the package ID `com.google.aiedge.gallery`; an installation signed with another key cannot be updated with this APK. Back up any needed data before uninstalling a conflicting installation, since uninstalling deletes app data.

The app version is **1.0.0-beta.1**. Android's internal version code is **45** to allow upgrades from this checkout's earlier test builds.

## Beta limitations

- Agent Skills and phone controls require a model supported by the agent runtime. Voice and transcription require an audio-capable model.
- Contact, email, and calendar actions open drafts for review in the corresponding phone app. Availability depends on installed apps and Android permissions.
- Hugging Face OAuth credentials are not configured in this fork. In-app sign-in for gated model downloads is unavailable; use compatible models available without that login or import compatible model files obtained separately.
- Live model inference, microphone recognition quality, and successful actions on physical phones have not been verified for this beta.

## Verification

- 31 JVM tests passed with no failures or skipped tests.
- Full debug lint passed with zero errors (175 warnings remain); release fatal lint also passed.
- Debug, release, and instrumentation APK assemblies passed using JDK 21.
- The published release APK installed and launched on an Android 16 / API 36 emulator; both phone-tool instrumentation tests passed against that APK.
- APK version, Android 12 minimum, non-debuggable manifest, v2 signature, 16 KB ZIP alignment, archive integrity, bundled catalog, and third-party license resources were checked.

Use the attached `SHA256SUMS` to verify the APK download. Local emulator checks do not establish real-device model, microphone, or hardware-action behavior.
