/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.BuildConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests: run on a test install; the clear test intentionally removes its captured logs. */
@RunWith(AndroidJUnit4::class)
class DiagnosticsRecorderTest {
  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val exports = mutableListOf<File>()
  private var initiallyRecording = false

  @Before
  fun startRecording() {
    DiagnosticsRecorder.initialize(application)
    await("Recorder initialization") {
      !DiagnosticsRecorder.state.value.isPreparing &&
        DiagnosticsRecorder.state.value.sessionId.isNotBlank()
    }
    initiallyRecording = DiagnosticsRecorder.state.value.isRecording
    DiagnosticsRecorder.setRecording(true)
    await("Recorder and native capture startup") {
      val state = DiagnosticsRecorder.state.value
      state.isRecording && state.logcatStatus != "Stopped"
    }
  }

  @After
  fun restoreRecording() {
    DiagnosticsRecorder.setRecording(initiallyRecording)
    await("Recording preference restoration") {
      DiagnosticsRecorder.state.value.isRecording == initiallyRecording
    }
    exports.forEach { it.delete() }
  }

  @Test
  fun exportContainsRedactedAppEventsAndDeviceHealthMetadata() {
    val marker = "app-event-${UUID.randomUUID()}"
    val secret = "hf_InstrumentationSecret123456789"
    DiagnosticsRecorder.event("instrumentation", "$marker Authorization: Bearer $secret")
    val entries = export("Reproduction note $marker; api_key=$secret")
    val logs = logs(entries)
    assertTrue("Accepted app event missing from export", logs.contains(marker))
    assertTrue("Expected credential masking", logs.contains("[REDACTED]"))
    assertFalse("Credential leaked into capture", logs.contains(secret))
    assertFalse(
      "Credential leaked into issue note",
      entries.getValue("issue-note.txt").contains(secret),
    )
    assertTrue(entries.getValue("issue-note.txt").contains(marker))
    assertTrue(entries.containsKey("README.txt"))
    assertTrue(entries.containsKey("process-exits.json"))

    val device = JSONObject(entries.getValue("device-and-app.json"))
    assertEquals(application.packageName, device.getString("package"))
    assertEquals(BuildConfig.VERSION_NAME, device.getString("versionName"))
    assertTrue(device.getInt("sdk") >= 31)
    assertTrue(device.getString("model").isNotBlank())
    assertTrue(device.has("permissions"))

    val health = JSONObject(entries.getValue("runtime-at-export.json"))
    assertTrue(health.getLong("javaHeapUsedBytes") > 0)
    assertTrue(health.getLong("ramTotalBytes") > 0)
    assertTrue(health.getLong("storageFreeBytes") >= 0)
    assertTrue(health.has("thermalStatus"))
    assertTrue(health.has("processCpuTimeMs"))

    val status = JSONObject(entries.getValue("capture-status.json"))
    assertTrue(status.getBoolean("recording"))
    assertEquals(20L * 1024 * 1024, status.getLong("retentionLimitBytes"))
    assertTrue(status.getLong("capturedBytes") > 0)
    assertTrue(status.getString("sessionId").isNotBlank())
  }

  @Test
  fun actualProcessLogcatIsCapturedAndRedactedWhenPlatformAllowsIt() {
    assumeTrue(
      "Device does not provide app-process logcat",
      !DiagnosticsRecorder.state.value.logcatStatus.startsWith("Unavailable"),
    )
    val marker = "native-log-${UUID.randomUUID()}"
    val secret = "hf_NativeCaptureSecret123456789"
    Log.i("GalleryDiagnosticsTest", "$marker token=$secret")
    await("Generated Android Log.i line in live capture") {
      val state = DiagnosticsRecorder.state.value
      state.recentLines.any { it.contains("[logcat]") && it.contains(marker) } ||
        state.logcatStatus.startsWith("Unavailable")
    }
    assumeTrue(
      "Device stopped providing app-process logcat",
      !DiagnosticsRecorder.state.value.logcatStatus.startsWith("Unavailable"),
    )
    val capture = logs(export())
    assertTrue(capture.contains(marker))
    assertFalse(capture.contains(secret))
    assertTrue(capture.contains("[REDACTED]"))
    assertEquals("Capturing app logs", DiagnosticsRecorder.state.value.logcatStatus)
  }

  @Test
  fun pauseRejectsNewEventsAndClearRemovesRetainedLogs() {
    assertFalse(DiagnosticsRecorder.state.value.isPreparing)
    val burstId = UUID.randomUUID().toString()
    val before = (0 until 30).map { "before-pause-$burstId-event-$it-end" }
    val during = "during-pause-${UUID.randomUUID()}"
    // Pause immediately after the burst: there is deliberately no export or writer barrier here.
    before.forEach { DiagnosticsRecorder.event("instrumentation", it) }
    DiagnosticsRecorder.setRecording(false)
    await("Paused recorder") {
      !DiagnosticsRecorder.state.value.isRecording &&
        DiagnosticsRecorder.state.value.logcatStatus == "Stopped"
    }
    DiagnosticsRecorder.event("instrumentation", during)
    Log.i("GalleryDiagnosticsTest", during)
    val paused = export()
    val pausedLogs = logs(paused)
    before.forEach { marker ->
      assertTrue("Accepted pre-pause event missing: $marker", pausedLogs.contains(marker))
    }
    assertFalse(pausedLogs.contains(during))
    assertFalse(JSONObject(paused.getValue("capture-status.json")).getBoolean("recording"))

    DiagnosticsRecorder.clear()
    await("Capture files cleared") {
      val state = DiagnosticsRecorder.state.value
      state.storedBytes == 0L &&
        state.recentLines.isEmpty() &&
        File(application.noBackupFilesDir, "diagnostics").listFiles()?.none {
          it.name.startsWith("diagnostic-") && it.extension == "log"
        } == true
    }
    val cleared = export()
    assertFalse(cleared.keys.any { it.startsWith("logs/") })
    assertFalse(cleared.containsKey("previous-crash.zip"))
    assertFalse(DiagnosticsRecorder.state.value.hasRecoveredCrash)
    assertFalse(DiagnosticsRecorder.state.value.isRecording)
  }

  @Test
  fun pausePreservesPendingDroppedLinesUntilRecordingResumes() {
    DiagnosticsRecorder.setRecording(false)
    await("Paused recorder") {
      !DiagnosticsRecorder.state.value.isRecording &&
        DiagnosticsRecorder.state.value.logcatStatus == "Stopped"
    }
    // Inject the result of a full queue, without flooding the phone or racing its disk speed.
    val dropped =
      DiagnosticsRecorder::class.java.getDeclaredField("dropped").run {
        isAccessible = true
        get(DiagnosticsRecorder) as AtomicLong
      }
    val expectedLost = 321L
    val previousDropped = dropped.getAndSet(expectedLost)
    try {
      // Cross at least one real one-second UI refresh while collection is paused.
      SystemClock.sleep(1500)
      val paused = export()
      assertEquals(
        expectedLost,
        JSONObject(paused.getValue("capture-status.json")).getLong("pendingDroppedLines"),
      )

      DiagnosticsRecorder.setRecording(true)
      await("Pending loss notice after recording resumes") {
        DiagnosticsRecorder.state.value.recentLines.any {
          it.contains("Dropped $expectedLost lines: capture queue was full")
        }
      }
      val resumed = export()
      assertTrue(logs(resumed).contains("Dropped $expectedLost lines: capture queue was full"))
      assertEquals(
        0L,
        JSONObject(resumed.getValue("capture-status.json")).getLong("pendingDroppedLines"),
      )
    } finally {
      dropped.set(previousDropped)
    }
  }

  @Test
  fun privateCaptureFilesSurviveStoreRecreationOnDevice() {
    val directory = File(application.noBackupFilesDir, "diagnostics-test-${UUID.randomUUID()}")
    try {
      val marker = "retained-${UUID.randomUUID()}"
      DiagnosticStore(directory).apply {
        append("test", "$marker token=private-test-value")
        sync()
      }
      val reopened = DiagnosticStore(directory)
      val recovered = reopened.snapshot().values.joinToString("") { it.toString(Charsets.UTF_8) }
      assertTrue(recovered.contains(marker))
      assertTrue(recovered.contains("[REDACTED]"))
      assertFalse(recovered.contains("private-test-value"))
      reopened.append("test", "after recreation")
      assertTrue(
        reopened.snapshot().values.any { it.toString(Charsets.UTF_8).contains("after recreation") }
      )
    } finally {
      directory.deleteRecursively()
    }
  }

  private fun export(note: String = ""): Map<String, String> {
    val archive = runBlocking { DiagnosticsRecorder.createExport(application, note) }
    exports.add(archive)
    assertTrue("Export archive should be nonempty", archive.length() > 0)
    return ZipFile(archive).use { zip ->
      zip
        .entries()
        .asSequence()
        .filter { !it.isDirectory }
        .associate { entry ->
          entry.name to
            if (entry.name.endsWith(".zip")) "[binary archive]"
            else zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
    }
  }

  private fun logs(entries: Map<String, String>): String =
    entries.filterKeys { it.startsWith("logs/") }.values.joinToString("\n")

  private fun await(label: String, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + 15_000
    while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
    assertTrue("Timed out: $label. State: ${DiagnosticsRecorder.state.value}", condition())
  }
}
