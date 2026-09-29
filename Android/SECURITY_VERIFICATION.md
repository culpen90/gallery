# Gallery security verification — September 29, 2026

The hardened release is installed on the owner's Samsung SM-S938U running Android 16/API 36 through the existing wireless debugging connection. The upgrade retained the installed application's signing certificate and existing data. No phone-wide security settings were changed.

## Verified results

| Check | Evidence/result |
| --- | --- |
| Local unit tests | 104 tests, zero failures/errors/skips |
| Build | Debug, release, and instrumentation APKs built successfully with the final sources |
| Release lint | Zero errors; 205 warnings remain |
| Real-device security tests | 18 passed in the separate `com.google.aiedge.gallery.securitytest` install, using synthetic data |
| Device test coverage | Hardware-backed key use, authenticated encryption/tamper rejection, verified DataStore migration, encrypted audio restoration, legacy cutout/audio migration, path restrictions, encrypted sharing provider, private skill resources and WebView isolation, encrypted diagnostics/redaction/export |
| Signing/packaging | APK signature verified and 16 KiB alignment verified; installed and built release hashes match |
| Existing-data upgrade | Owner authenticated and confirmed Gallery opened normally; the cold-start private UI is gated behind successful eager migrations |
| Background protection | Owner confirmed that sending Gallery to the background and reopening it required authentication again |
| Screenshot protection | Runtime window flags `0x81812100` contain `FLAG_SECURE` (`0x2000`) |
| Production shell access | ADB `run-as` rejected the app because the installed release is not debuggable |
| Private diagnostics activity | Direct shell launch was rejected because the activity is not exported |
| Backup/network policy | Final merged release manifest: backup false, debuggable false, cleartext false, explicit system-CA network policy; backup and transfer rules exclude all nine domains |
| Cleanup | Both temporary `.securitytest` packages were uninstalled; the owner's normal Gallery package remains installed |
| Source hygiene | `git diff --check` passed |

Pre-version-update security upgrade APK SHA-256 (the subsequent Beta 5 APK has a different hash):

```text
a199689126fb187036662d2a1dd97d0545e5d03e6c74aea8b7308bf31626057d
```

Signing certificate SHA-256 (matching the existing installation):

```text
f316b684e87b4df6deb4c9fc987e530e7c3fae9810e6a3371b0cc0ea05f179f1
```

Build invocation:

```sh
./gradlew -PisolatedSecurityTests=true :app:testDebugUnitTest :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:lintRelease
```

Device test selection:

```text
PrivateDataEncryptionDeviceTest
PrivateImageProviderTest
PrivateChatMediaTest
PrivateSkillFilesTest
DiagnosticsRecorderTest
```

The test app is isolated because diagnostics tests deliberately clear their own synthetic captures. They did not read or clear the owner's conversations, recordings, credentials, or diagnostic history.

## Limits of this evidence

This verifies the specified protections and upgrade on this phone; it does not prove immunity to every attack. The release retains the development signing certificate for an in-place upgrade. The development computer, signing key, Android OS, and authorized debugging connection remain trusted. Hardware key access requires an unlocked device on Android 15+, while the app's foreground biometric/PIN gate is a separate control rather than a per-operation hardware authentication requirement.

Tests verify HTTPS redirect/credential policy and private resource isolation; they do not prove the behavior of every remote MCP server or imported script. Broader inference, native camera capture, every document-picker/process-recreation path, and every downloaded model were not re-exercised by this security test selection. Existing readable exports, historical backups, and previously copied credentials cannot be made private retroactively by this upgrade. Native model files/caches and transient camera output retain the platform boundaries described in [SECURITY.md](SECURITY.md).

The initial hardening checks above preceded the Beta 5 version update. Versioned build and packaging evidence is recorded in the [Beta 5 release notes](../release/v1.0.0-beta.5/NOTES.md), and its final APK hash is recorded in [SHA256SUMS](../release/v1.0.0-beta.5/SHA256SUMS).

## Final Beta 5 build

The final `1.0.0-beta.5` / version code `49` APK passed archive integrity, package/version metadata, APK v2 signing, 16 KiB ZIP alignment, release manifest policy, and third-party license resource checks. The published Beta 4 APK was downloaded and its checksum verified; its signer matches Beta 5. A fresh JDK 21 build passed 106 unit tests and both debug/release lint checks, with zero errors and 205 warnings each:

```sh
./gradlew -PisolatedSecurityTests=true :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --max-workers=2
```

The final versioned APK SHA-256 is:

```text
fb5e1adac49a0d9dea9e301891ffb69b38f6d9432ec70824f61c7cdfc0884219
```

Wireless debugging was reconnected for a Beta 5 isolated test rerun. An initial attempt reached the device's automatic lock and Keystore correctly rejected key use. With the phone unlocked, the rerun exposed a diagnostics pause timeout: each queued log line performed hardware cryptography and synchronized disk writes. The bounded encrypted batching fix passed two new unit regression tests; the previously failing pause/clear test passed on the phone in 1.885 seconds. The full 18-test security selection then passed in 36.08 seconds with `1.0.0-beta.5` / version code `49`. The separate test packages were removed afterward. Source attribution comments were added after this functional rerun; they do not change executable behavior.

The earlier hardened APK's owner-confirmed migration and relocking remain the manual runtime evidence above.
