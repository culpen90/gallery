# Capture an Android beta diagnostic report

Gallery's built-in diagnostics record locally while you use the app. Beta builds start recording automatically, so you can capture a problem without setting up a computer or enabling Android Developer options.

## Save the app's report to your phone

1. Reproduce the problem, then open **Settings → Beta diagnostics**. You can also long-press the Gallery icon on your home screen and choose **Diagnostics**; this opens diagnostics without initializing the chat screen.
2. Check the recording and app-log status. The live viewer shows recent events as you use the app. Recording can stay on during export; stopping it first is unnecessary.
3. Add an issue note describing what you tried, what happened, what you expected, and approximately when the problem occurred. Include the model name if relevant.
4. Tap **Save ZIP**. In Android's file picker, choose **Downloads** or another folder on your phone and save the file. You can open the saved ZIP from your phone's Files app.
5. Review the contents before attaching the ZIP to a bug report. Gallery does not automatically upload or send diagnostics anywhere.

**If the app crashes before you can export:** reopen Gallery, then save the ZIP. Recorded logs are kept across app restarts. After a recorded fatal exception or an Android-reported crash/ANR, the next launch preserves the preceding capture in a separate `previous-crash.zip` inside the export before starting new recording. The Diagnostics launcher shortcut also works when a problem in chat prevents you from reaching Settings. Do not uninstall Gallery or clear its Android app storage before recovering the logs.

The latest crash archive is retained separately, so ordinary use after reopening does not rotate it away. Recovery still depends on the process starting successfully and, for native crashes/ANRs, Android making an exit record available. Sudden process kills or power loss can lose final queued or buffered lines. If neither launch route works, collect the supplemental Android system report below.

## What the ZIP contains

- App events and available app-process logcat output, including native runtime logs.
- App/device/build information and granted permissions, without a device serial number.
- Memory, free storage, battery, thermal, connectivity, and CPU-time snapshots.
- App lifecycle events, recorded errors, and best-effort observations of main-thread stalls. A stall observation is not an Android-confirmed ANR.
- Recent app process-exit reasons and available Android ANR traces.
- Capture status, source errors or dropped-line counts, and your issue note.
- A separate `previous-crash.zip` when the latest crash capture was recovered.

The live viewer shows only a recent excerpt; the ZIP includes the retained capture. Logs rotate at **20 MiB total**, plus **one** separately retained crash archive. Older logs rotate out; long individual records and ANR traces are capped. Very busy logging can drop queued lines, and the report records those drops. Android can restrict app-process logcat or omit crash traces; diagnostics displays source availability. Recording cannot continue while Android freezes or kills the process.

Common credential formats are masked on a **best-effort** basis before logs are stored or exported. Existing app or native logs and issue notes may still contain personal conversation or tool text. The exporter does not copy chat databases, model weights, photos, audio recordings, credential stores, or other apps' logs.

Use the recording control to pause collection. **Clear logs** deletes the retained app capture and recovered crash archive. ZIPs you already saved or shared remain in their chosen locations.

## Supplemental full Android system report

The app ZIP contains information available to Gallery. A full Android system report can provide additional system-service, native crash, or device-wide information when an app report is insufficient. It requires Android Developer options or `adb` and can contain broader personal information.

### On your phone

1. Open Android **Settings → About phone** and tap **Build number** seven times to enable Developer options. Some phones place Build number under **Software information**.
2. Immediately after the problem, open **Developer options → Take bug report**. Developer options may be under **System**.
3. Choose **Full report**, then **Report**.
4. Wait for the **Bug report captured** notification, open it, and save the ZIP using Android's share sheet. Menu names and available options vary by phone.
5. Review the report before attaching it to your issue or sharing a download link.

### From a computer with ADB

With USB debugging enabled and the phone connected:

```shell
# Save a full system report to this computer.
adb bugreport gallery-system-bugreport.zip

# If multiple devices are connected, choose the intended device.
adb devices
adb -s <device_serial> bugreport gallery-system-bugreport.zip
```

On devices that expose saved reports through `/bugreports/`, you can also list or copy an existing report:

```shell
adb shell ls /bugreports/
adb pull /bugreports/<bug_report_filename.zip>
```

Inside a system-report ZIP, `bugreport-[...].txt` contains system logcat and diagnostic output from Android services. This is a separate report from Gallery's built-in app ZIP; providing both can help investigate device-level failures.
