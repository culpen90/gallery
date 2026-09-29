# Gallery private data protection

Gallery protects saved private application data with authenticated AES-256-GCM encryption and a non-exportable Android Keystore wrapping key. It requests StrongBox and falls back only to a hardware-backed trusted execution environment; software-only keys are rejected. Each record uses a fresh random data key and nonce. The record's purpose and envelope header are authenticated, so copying ciphertext between stores or altering bytes fails authentication.

Android 15 and newer additionally require the device to be unlocked for key use. This flag is deliberately omitted on Android 12–14 because those versions have documented key-loss bugs. Strong biometric or device-credential authentication gates private screens on every foreground session. That screen authentication is a separate control: the storage key does not require biometric approval for each individual cryptographic operation. Background downloads and opt-in diagnostics need bounded unattended operations while the device is unlocked.

## Protected persistence

- Settings, text-input history, conversation history, credentials, custom prompts, MCP authorization headers, skill indexes, cutout metadata, benchmark results, onboarding data, and notification schedules use encrypted Proto DataStore files in internal storage excluded from backups.
- Saved chat images/audio and cutout pictures use authenticated encrypted files in internal storage excluded from backups.
- Custom/imported skill sources, scripts, and assets are encrypted. WebView decrypts the explicitly loaded skill's resources into memory; it cannot serve arbitrary app-private files. Cookies, persistent browser storage, file/content access, caching, and WebView debugging are disabled.
- Download work inputs, including private URLs and tokens, are encrypted before WorkManager persists them. Upgrade cleanup cancels old download jobs, prunes their plaintext inputs, compacts SQLite, and truncates its WAL. Interrupted downloads can be restarted.
- Opt-in diagnostic logs, retained crash archives, and fatal markers are encrypted. Normal app startup captures no private diagnostics before authentication.
- Shared-image and clipboard caches store encrypted images. An explicitly granted recipient reads plaintext through a bounded read-only pipe; a stalled reader times out.

Migration creates ciphertext atomically, authenticates and verifies the replacement, then deletes the app-owned plaintext file. Existing ciphertext is authoritative. A failed authentication, unavailable key, or unreadable original keeps the existing files and blocks access; there is no automatic key reset or plaintext fallback. Dormant stores, media, and custom skill files are migrated before the main private UI appears.

## Access and network controls

Cloud backups and device transfers exclude all app storage domains. Main and diagnostics screens require owner authentication, relock on pause, and close private dialogs while locked. Screenshots, recent-app previews, third-party overlays, autofill, and content capture are restricted. Diagnostics is not exported to other applications and remains reachable through an authenticated recovery button if normal chat initialization fails. Notification contents are hidden on the lock screen.

Networking requires HTTPS and system certificate authorities. Downloads explicitly validate redirects, reject HTTP downgrades, and scope Hugging Face bearer tokens to the exact Hugging Face origin; credentials are removed after a redirect leaves that origin. MCP redirects are disabled to protect custom authentication headers. Telemetry is disabled before Firebase initializes and stays off until the user explicitly enables it. Legacy default-enabled analytics preferences require a fresh opt-in.

## Deliberate boundaries

Plaintext exists in memory during inference, display, and authorized tool execution. A compromised operating system, app process, signing key, or intentionally installed malicious skill can exceed the app's security boundary. HTTPS protects transport; a remote service receiving a tool request can read that request. Android's system trust store remains trusted. The format detects changes, truncation, and cross-purpose substitution, but cannot detect restoration of an older authentic snapshot without trusted monotonic state.

Native model weights and engine caches need ordinary files for native memory mapping and rely on Android filesystem encryption and application isolation. Camera capture uses a temporary native writable file descriptor. Android system notification/clipboard infrastructure and the selected sharing recipient necessarily receive authorized plaintext. Explicit diagnostic ZIP exports and pictures saved to the photo library are readable user-directed copies; the diagnostics screen states this before export. Interrupted diagnostic export caches are removed at startup.

Deleting an old plaintext file or compacting SQLite does not prove physical flash erasure and does not revoke a credential previously copied elsewhere. Uninstalling Gallery, clearing its storage, losing the device's Keystore key, or removing a secure device lock can make encrypted data unrecoverable. Automatic cloud recovery is disabled.

Wireless debugging and the authorized development computer remain part of the trust boundary. Disable wireless debugging and revoke unused paired computers when development testing is finished. Gallery does not change phone-wide settings or claim to protect other applications.

## Verification

Security tests cover encryption/decryption, fresh ciphertext, tampering with every envelope byte, wrong purposes/keys, truncation, plaintext rejection, migration, media restoration, path restrictions, atomic writes, redirect credential rules, sharing pipes, and authentication-session state. Device tests use a separate `.securitytest` application ID and synthetic data so they cannot clear or inspect the owner's real app data:

```sh
./gradlew -PisolatedSecurityTests=true :app:assembleDebug :app:assembleDebugAndroidTest
```

Build, lint, device results, and remaining manual checks are recorded in the task's verification report. Automated tests do not establish immunity to unknown attacks.

Android references: [Keystore](https://developer.android.com/privacy-and-security/keystore), [unlocked-device key restrictions](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setUnlockedDeviceRequired(boolean)), [backup and transfer behavior](https://developer.android.com/identity/data/autobackup).
