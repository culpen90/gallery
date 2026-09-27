# Beta 4 diagnostics

Use **Settings → Beta diagnostics** or long-press Gallery's launcher icon and select
**Diagnostics**. Beta builds record locally by default. The panel shows capture status,
retained size, a live excerpt, an optional reproduction note, pause/clear controls, and a
system document picker for saving a ZIP. Recording can continue during export.

See the [bug-reporting guide](../Bug_Reporting_Guide.md) for tester instructions and the
separate full Android system-report workflow.

## Capture and recovery

- App-process logcat includes available Kotlin/Java and native runtime logs. Structured
  events cover navigation, lifecycle, model/session operations, inference timing and
  outcome, tools, imports, and downloads without adding prompt/response bodies.
- Foreground health sampling records memory, storage, battery, thermal and connectivity
  state. The responsiveness monitor records possible main-thread stalls, not confirmed ANRs.
- Ten rotating UTF-8 files retain at most 20 MiB in private no-backup storage, plus one
  separate crash archive (up to about 23 MiB). Writers close files continuously; the fatal handler drains accepted events and forces writes
  before delegating to Android's existing crash handler.
- On relaunch, a recorded fatal marker or an Android-reported Java/native crash or ANR
  freezes the preceding capture into one separate recovery archive before normal writes
  resume. The exported ZIP includes that archive as `previous-crash.zip`.
- The standalone diagnostics activity avoids chat/model initialization. It cannot recover
  from failures that prevent the application process itself from starting.
- Common credential formats are masked on a best-effort basis before storage/export.
  Existing logs and issue notes can contain personal text. Nothing is automatically uploaded. App databases,
  model binaries, images, audio and credential stores are not copied.

Logcat/OS exit traces are best effort. Sudden process kills or power loss can lose final
queued/buffered lines. Native tombstone protobuf files are not included. Capture storage
is bounded, and old logs rotate; a full device-wide log requires Android's system report.

## Validation on 2026-09-27

The exact Beta 4 version (`1.0.0-beta.4`, Android version code `48`) passed these local checks:

- Debug and release APKs assembled; **80 JVM tests passed**. Diagnostics tests cover
  rotation, restart persistence, Unicode boundaries, redaction, archive paths/contents,
  and destination-stream failures.
- **10 diagnostics instrumentation tests passed** on an Android 16 emulator, including
  app logcat capture, immediate export, burst-then-pause behavior, clear, persistence,
  note restoration, the live viewer, save success/failure/retry, and retaining dropped-line
  counts while paused.
- `:app:lintDebug` passed with **0 errors and 200 warnings**.
- The release APK's package/version, minimum Android 12 requirement, non-debuggable
  configuration, archive integrity, APK v2 signature, 16 KiB ZIP alignment, and bundled
  third-party license resources were verified. Its signing certificate matches beta 3.
- Installed the exact release APK in place on a Samsung Galaxy S25 Ultra (SM-S938U,
  Android 16). Normal chat opened with its existing Gemma model, and
  **Menu → Settings → Beta diagnostics** captured app/native logs.
- On that release APK, saved a ZIP into Downloads through the Android document picker
  while recording continued and verified the visible success message. The retrieved ZIP
  passed integrity checks and included actual app logcat. Its metadata identified version
  `1.0.0-beta.4`, code `48`, build type `release`, SM-S938U, and SDK 36. Capture status
  reported recording enabled, app logs being captured, and no recorder error.

Earlier development-build checks before the version update:

- Verified the exported issue note and an enabled launcher shortcut on the same phone.
- Deliberately crashed only the emulator app with `am crash`; verified the fatal marker,
  persisted stack, recovered UI notice, and a saved outer ZIP containing a readable
  `previous-crash.zip` with the fatal exception.
- Sent SIGABRT only to the emulator app; verified relaunch recovery and Android's native
  crash exit reason (5). This validates native process-death recovery, not a specific
  inference-engine failure or device-specific ANR behavior.

These are local checks; no hosted CI result is claimed. Existing SDK/toolchain and
deprecation warnings remain. See the
[Beta 4 release notes](../release/v1.0.0-beta.4/NOTES.md) for installation and limitations.
