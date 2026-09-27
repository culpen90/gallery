# Gallery Android — 1.0.0-beta.3

This is beta 3 of the [culpen90/gallery fork](https://github.com/culpen90/gallery), built on [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery). Download the **[installable APK](https://github.com/culpen90/gallery/releases/download/v1.0.0-beta.3/gallery-1.0.0-beta.3.apk)** or read the **[beta release notes](https://github.com/culpen90/gallery/releases/tag/v1.0.0-beta.3)**. Android 12 or later is required.

Open the APK on your device and allow installation from your browser or file manager if Android asks. Choose or import a compatible on-device model after opening Gallery. This guide covers the fork's Android interface; upstream store releases, iOS, and macOS have their own interfaces and documentation.

Beta 3 redesigns the interface with warm light and dark themes, clearer navigation, editable starter prompts, and a simpler composer. The model library now offers search and filters for all, on-device, and imported models. History, settings, skills, connected tools, notifications, and setup screens share the same visual style. See the [design and validation notes](DESIGN.md).

The audio fixes from beta 2 remain included: your recording and any text you actually enter are sent together without an extra generated chat bubble.

The app opens into one conversation. Type a message or tap the microphone to talk. The language model decides in the background whether to answer directly, load a relevant Agent Skill, or call a built-in phone control. There is no separate action mode to choose.

- **Agent Skills:** Enabled skills remain available automatically. Manage them, import skills, or configure connected tools from the composer's **+** menu.
- **Mobile Actions:** Ask naturally to turn the flashlight on or off, open Wi-Fi settings, find a place in Maps, or prepare a contact, email, or calendar event. Android permissions are requested when needed. Drafts open in the appropriate phone app for you to review and save or send.
- **Audio Scribe:** Tap the microphone for voice chat. For a transcript, use **+ → Record a transcript** or **Transcribe a WAV**, or change the intent on an attached recording. Each message can include one recording of up to 30 seconds; another recording can be sent on the next turn. Explicit transcription runs with tool execution disabled.
- **Models:** Use the chat's model selector or **Menu → Models**. The app prefers a model with Agent Skills support; ordinary chat models are still usable. Voice and transcription require an audio-capable model, while skills and phone controls require a model supported by the agent runtime.
- **History and settings:** Chat history stays in the top bar. Switching models starts a new conversation and keeps previous chats in history. App settings, model management, and scheduled notifications live in the menu.

The existing model download, import, and on-device inference infrastructure is retained. A bundled catalog (from `model_allowlists/1_0_19.json`) provides model setup if the versioned online catalog and local cache are unavailable. Hugging Face OAuth is not configured in this beta, so gated model downloads require the configuration described in the [development notes](../DEVELOPMENT.md). Local model import remains available.

## Audio behavior and limitations

During beta 2 validation, voice chat was checked with Gemma-4-E2B-it on a Samsung Galaxy S25 Ultra: an imported recording and a live microphone recording both received answers to the spoken questions.

Saved recordings remain available to play after reopening a chat. The current runtime cannot restore their audio into the model's conversation context, so the app shows a notice to send a recording again when you want to discuss it. Written conversation context is restored.

Transcription remains model-dependent. With Gemma-4-E2B-it, a request to transcribe a spoken question can produce an answer to that question instead of the exact words. Transcription mode still disables tool execution.

Inference runs on the device; model downloads and network-connected skills or tools can use the internet. Other models, devices, and physical phone actions need broader testing during this beta.
