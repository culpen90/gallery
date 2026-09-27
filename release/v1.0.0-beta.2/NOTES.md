# Gallery 1.0.0 Beta 2

Beta 2 fixes voice input so audio turns send the recording and any text you entered without adding a generated “Talk to the model” message to the conversation.

## Fixes

- Microphone recordings and imported WAV audio reach the model with the selected voice-chat or transcription intent.
- Audio turns preserve genuine typed prompts and the selected intent in saved history. Older generated audio labels no longer appear as user-written messages.
- Recorded WAV files use the correct RIFF size.
- Reopening an audio conversation restores written context and keeps recordings available for playback. A notice explains when a recording must be sent again for the model to hear it.
- Synchronous inference failures are handled as chat errors instead of crashing the app.

The unified chat, Agent Skills, Mobile Actions, and model management from beta 1 remain available.

## Install

Download **gallery-1.0.0-beta.2.apk** from this release on an **Android 12 or newer** device, allow installation from the browser or file manager when Android asks, and open the APK. A compatible model must be downloaded or imported separately; model weights are not included.

This installable release-variant APK uses the same local Android test signing key as beta 1, allowing an in-place update from that release. It is not a production-signed or Play Store release. The package ID remains `com.google.aiedge.gallery`; installations signed with a different key cannot be updated with this APK.

The app version is **1.0.0-beta.2**, with Android internal version code **46**.

## Beta limitations

- The current runtime cannot restore saved audio into the model's context after reopening a chat. Recordings remain playable, but send one again if you want the model to discuss it.
- Transcription with Gemma-4-E2B-it can answer a spoken question instead of returning a verbatim transcript. This behavior is not fixed in beta 2. Explicit transcription continues to disable tool execution.
- Voice and transcription require an audio-capable model. Agent Skills and phone controls require a model supported by the agent runtime.
- Contact, email, and calendar actions open drafts for review in the corresponding phone app. Availability depends on installed apps and Android permissions.
- Hugging Face OAuth credentials are not configured in this fork. Use compatible models available without gated sign-in or import compatible model files obtained separately.
- Physical-device voice checks covered Gemma-4-E2B-it on one Samsung Galaxy S25 Ultra. Other models, devices, and physical phone actions need broader testing.

## Verification

- 57 JVM tests passed with no failures, errors, or skipped tests.
- Debug and release APKs assembled successfully. Full debug lint and release fatal lint passed with no errors.
- The release APK passed archive, alignment, package/version, and APK v2 signature checks. Physical-device checks below used the same audio fixes before the beta 2 version-number update.
- On a Samsung Galaxy S25 Ultra, an imported recording asking seven plus five received twelve, and a live microphone recording asking nine plus four received thirteen.
- Reopening an audio conversation displayed the saved-audio notice, and a subsequent written question received a response without crashing the app.
- The transcription limitation above was reproduced on the same physical device.

Use the attached `SHA256SUMS` to verify the APK download.
