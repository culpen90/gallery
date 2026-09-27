# Gallery — Android Beta

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android beta](https://img.shields.io/badge/Android-1.0.0--beta.2-blue)](https://github.com/culpen90/gallery/releases/tag/v1.0.0-beta.2)

This is an independent fork of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery). The Android beta brings chat, Agent Skills, phone controls, and Audio Scribe into one conversation. **1.0.0-beta.2** fixes voice input and removes the extra “Talk to the model” message from audio turns. The language model decides in the background whether to reply directly, load a relevant skill, or use a supported phone control.

**[Download the Android beta APK](https://github.com/culpen90/gallery/releases/download/v1.0.0-beta.2/gallery-1.0.0-beta.2.apk)** · [Release notes](https://github.com/culpen90/gallery/releases/tag/v1.0.0-beta.2) · [Android guide](Android/README.md)

The beta requires Android 12 or later and a compatible on-device model. It is distributed from this repository's GitHub releases. The original app's store listings and screenshots below describe upstream releases.

## Android features

- **One conversation:** Start in chat, choose a model from the top bar, and find history, settings, and model management nearby.
- **Automatic Agent Skills:** Enabled skills and connected tools remain available to compatible models. Manage them through the composer's **+** menu.
- **Mobile Actions:** Ask for the flashlight, Maps, Wi-Fi settings, or contact, email, and calendar drafts. Android permissions still apply; drafts open in the phone's apps for you to review and complete.
- **Audio Scribe:** Talk to an audio-capable model, request a transcript, or import a WAV file. Recordings support up to 30 seconds per message. Explicit transcription disables tool execution.
- **On-device models:** Download or import compatible models using the existing Gallery infrastructure. A bundled catalog keeps model setup available when the online catalog and cache cannot be loaded. Skills, phone controls, and audio depend on the selected model's capabilities.

Voice chat has been checked on a physical phone with Gemma-4-E2B-it. Saved recordings remain playable after reopening a chat, but must be sent again for the model to hear them. Transcription with this model can answer a spoken question instead of transcribing it; see the [beta limitations](https://github.com/culpen90/gallery/releases/tag/v1.0.0-beta.2).

Inference runs on the device. Model downloads and network-connected skills or tools can use the internet. Hugging Face OAuth is not configured in this fork's beta, so gated downloads require configuration; see the [development notes](DEVELOPMENT.md). Local model import remains available.

## Install the Android beta

1. Download [gallery-1.0.0-beta.2.apk](https://github.com/culpen90/gallery/releases/download/v1.0.0-beta.2/gallery-1.0.0-beta.2.apk) on an Android 12+ device.
2. Open the APK and allow installation from your browser or file manager when Android prompts you.
3. Open Gallery and choose or import a compatible model. See the [Android guide](Android/README.md) for voice input, transcription, skills, and phone actions.

## Original Google AI Edge Gallery

This fork retains the upstream project's Android foundation, model infrastructure, and Apache 2.0 license. The upstream app has its own releases and feature layout. Its iOS and macOS distributions are separate from this Android beta; the unified Android interface described above does not apply to those apps.

| **Original Android app** | **Original iOS app** | **Original macOS app** |
| :--- | :--- | :--- |
| <a href='https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery'><img alt='Get it on Google Play' height="120" src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/></a> | <a href="https://apps.apple.com/us/app/google-ai-edge-gallery/id6749645337?itscg=30200&itsct=apps_box_badge&mttnsubad=6749645337" style="display: inline-block;"> <img src="https://toolbox.marketingtools.apple.com/api/v2/badges/download-on-the-app-store/black/en-us?releaseDate=1771977600" alt="Download on the App Store" style="width: 244px; height: 88px; vertical-align: middle; object-fit: contain;" /></a> | <a href="https://dl.google.com/google-ai-edge-gallery/macos/dmg/GoogleAIEdgeGallery-0.1.0.dmg"><img alt='Download for macOS' width="257" height="97" src="https://github.com/user-attachments/assets/29c70795-93b3-4e8b-8752-0cad4e413182" /></a> |

For upstream releases and cross-platform documentation, visit the [original repository](https://github.com/google-ai-edge/gallery) and [upstream wiki](https://github.com/google-ai-edge/gallery/wiki).


## Upstream app preview

These screenshots show the original app and its separate task screens, rather than this fork's unified Android interface.

<img width="480" alt="01" src="https://github.com/user-attachments/assets/a809ad78-aef4-4169-91ee-de7213cbb3bd" />
<img width="480" alt="02" src="https://github.com/user-attachments/assets/1effd10d-f45a-4f7b-9435-f50f1bdd36b6" />
<img width="480" alt="03" src="https://github.com/user-attachments/assets/e5089e41-2c18-4fbe-9011-ebe9e5a02044" />
<img width="480" alt="04" src="https://github.com/user-attachments/assets/0f39d3ed-7403-4606-a7c6-b2c7e51ba6c1" />
<img width="480" alt="05" src="https://github.com/user-attachments/assets/8c229e96-b598-4735-9f60-e96907e1d5d5" />
<img width="480" alt="06" src="https://github.com/user-attachments/assets/ac9fb77b-81de-4197-9ed3-f6fe58290b3e" />
<img width="480" alt="07" src="https://github.com/user-attachments/assets/bc86ba07-2eaf-49b1-980f-8a87a85c596f" />
<img width="480" alt="08" src="https://github.com/user-attachments/assets/1ccf3c95-a195-4a38-ad53-4b9c7b8b3c50" />

## 🛠️ Technology Highlights

*   **Google AI Edge:** Core APIs and tools for on-device ML.
*   **LiteRT:** Lightweight runtime for optimized model execution.
*   **Hugging Face Integration:** For model discovery and download.

## ⌨️ Development

Check out the [development notes](DEVELOPMENT.md) for instructions about how to build the app locally.

## 🤝 Feedback

This is an **experimental Beta release**, and your input is crucial!

*   🐞 **Found a bug in this fork?** [Report it here!](https://github.com/culpen90/gallery/issues/new?assignees=&labels=bug&template=bug_report.md&title=%5BBUG%5D)
*   💡 **Have an idea for this fork?** [Suggest a feature!](https://github.com/culpen90/gallery/issues/new?assignees=&labels=enhancement&template=feature_request.md&title=%5BFEATURE%5D)

## 📄 License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## 🔗 Useful Links

*   [**Upstream Project Wiki**](https://github.com/google-ai-edge/gallery/wiki)
*   [Hugging Face LiteRT Community](https://huggingface.co/litert-community)
*   [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
*   [Google AI Edge Documentation](https://ai.google.dev/edge)
