// Modified for the Gallery Android fork (Beta 5).
/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.security.EncryptedPrivateFile
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticsState(
  val isRecording: Boolean = false,
  val sessionId: String = "",
  val storedBytes: Long = 0,
  val logcatStatus: String = "Stopped",
  val recentLines: List<String> = emptyList(),
  val lastError: String? = null,
  val hasRecoveredCrash: Boolean = false,
  val isPreparing: Boolean = true,
)

/** Local, bounded diagnostics. Never uploads or reads the chat/credential stores. */
object DiagnosticsRecorder {
  private const val RECOVERY_PURPOSE = "diagnostics/previous-crash.zip"
  private const val FATAL_PURPOSE = "diagnostics/fatal-marker.txt"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mutableState = MutableStateFlow(DiagnosticsState())
  val state = mutableState.asStateFlow()
  private val gate = Any()
  private val ingress = Any()
  private val controls = Mutex()
  private val exports = Mutex()
  private val records = Channel<Record>(1024)
  private val wakeups = Channel<Unit>(Channel.CONFLATED)
  private val dropped = AtomicLong()
  private val foregroundActivities = AtomicInteger()
  private var app: Application? = null
  private var store: DiagnosticStore? = null
  @Volatile private var epoch = 0L
  private var logcatEpoch = 0L
  private var process: java.lang.Process? = null
  private var exitCutoff = 0L
  private var initialized = false
  @Volatile private var ready = false
  @Volatile private var privateStorageUnlocked = false
  private val recent = ArrayDeque<String>()

  private data class Record(val epoch: Long, val category: String, val text: String)

  fun initialize(application: Application) {
    if (initialized) return
    initialized = true
    app = application
    val prefs = application.getSharedPreferences("beta_diagnostics", Context.MODE_PRIVATE)
    exitCutoff = prefs.getLong("exit_cutoff", System.currentTimeMillis())
    if (!prefs.contains("exit_cutoff")) prefs.edit().putLong("exit_cutoff", exitCutoff).apply()
    try {
      // Interrupted, explicitly requested exports are transient plaintext copies. They can be
      // removed without opening the vault or waiting for app authentication.
      cleanupStaleArchiveFiles(application)
      store = DiagnosticStore(File(application.noBackupFilesDir, "diagnostics"))
      mutableState.value =
        DiagnosticsState(
          isRecording = false,
          sessionId = UUID.randomUUID().toString(),
          storedBytes = store!!.sizeBytes(),
        )
    } catch (e: Exception) {
      mutableState.update {
        it.copy(
          isPreparing = false,
          sessionId = UUID.randomUUID().toString(),
          lastError = "Cannot open local diagnostics: ${e.javaClass.simpleName}",
        )
      }
    }
    scope.launch {
      controls.withLock {
        synchronized(gate) {
          ready = true
          mutableState.update { it.copy(isPreparing = false) }
        }
      }
      for (wake in wakeups) {
        // Dequeue and append share the snapshot lock; no in-flight record can evade export/clear.
        synchronized(gate) { drainPendingRecords() }
      }
    }
    // UI updates are throttled; a busy native logger must not recompose on every log line.
    scope.launch {
      while (true) {
        delay(1000)
        synchronized(gate) {
          if (!ready) return@synchronized
          if (state.value.isRecording) {
            // Paused exports still need the pending count until a loss notice can be recorded.
            val lost = dropped.getAndSet(0)
            if (lost > 0) append("recorder", "Dropped $lost lines: capture queue was full")
          }
          runCatching { store?.sizeBytes() ?: 0 }
            .onSuccess { size ->
              mutableState.update { it.copy(storedBytes = size, recentLines = recent.toList()) }
            }
        }
      }
    }
    installCrashHandler()
    observeLifecycle(application)
    startHealthMonitor()
    event(
      "session",
      "Recording initialized; session=${state.value.sessionId}; version=${BuildConfig.VERSION_NAME}; pid=${Process.myPid()}",
    )
  }

  /** Call on IO after strong app authentication, before displaying any retained diagnostics. */
  fun onUserUnlocked(context: Context) {
    kotlinx.coroutines.runBlocking {
      controls.withLock {
        val enabled: Boolean
        synchronized(gate) {
          val capture = store ?: DiagnosticStore(File(context.noBackupFilesDir, "diagnostics"))
          capture.migrateLegacyLogs()
          EncryptedPrivateFile.migrate(
            File(context.noBackupFilesDir, "diagnostics-previous-crash.zip"),
            recoveryFile(context), RECOVERY_PURPOSE,
          )
          EncryptedPrivateFile.migrate(
            File(context.noBackupFilesDir, "diagnostics-fatal.txt"),
            fatalMarker(context), FATAL_PURPOSE, maxBytes = 4096,
          )
          store = capture
          mutableState.update { it.copy(lastError = null) }
          if (!privateStorageUnlocked) recoverPreviousCrash(context)
          privateStorageUnlocked = true
          ready = true
          enabled = context.getSharedPreferences("beta_diagnostics", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)
          mutableState.update {
            it.copy(isRecording = enabled, isPreparing = false, storedBytes = capture.sizeBytes())
          }
        }
        if (enabled && process == null) startLogcat(Instant.now())
      }
    }
  }

  /** Enqueues metadata without disk IO on the caller (including the inference/UI threads). */
  fun event(category: String, message: String) {
    synchronized(ingress) {
      if (!state.value.isRecording) return
      if (!records.trySend(Record(epoch, category, "${Instant.now()} $message")).isSuccess)
        dropped.incrementAndGet()
      wakeups.trySend(Unit)
    }
  }

  fun setRecording(enabled: Boolean) {
    if (!ready || !privateStorageUnlocked) return
    scope.launch {
      controls.withLock {
        val captureSince = Instant.now()
        synchronized(gate) {
          if (store == null) return@launch
          if (state.value.isRecording == enabled) return@launch
          stopLogcat()
          synchronized(ingress) { mutableState.update { it.copy(isRecording = enabled) } }
          drainPendingRecords()
          runCatching { store?.sync() }
            .onFailure { if (it is Exception) reportError("Could not flush diagnostics", it) }
          app
            ?.getSharedPreferences("beta_diagnostics", Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("enabled", enabled)
            ?.apply()
          if (enabled)
            append(
              "session",
              "${Instant.now()} Recording started; session=${state.value.sessionId}; version=${BuildConfig.VERSION_NAME}; pid=${Process.myPid()}",
            )
        }
        if (enabled) startLogcat(captureSince)
      }
    }
  }

  /**
   * Clears persisted capture and advances the exit-report cutoff, so old crashes do not reappear.
   */
  fun clear() {
    if (!ready) return
    scope.launch {
      controls.withLock {
        synchronized(gate) {
          stopLogcat()
          synchronized(ingress) { epoch++ }
          try {
            store?.clear()
            app?.let { application ->
              cleanupStaleArchiveFiles(application)
              check(!recoveryFile(application).exists() || recoveryFile(application).delete()) {
                "Cannot remove crash recovery"
              }
              check(!fatalMarker(application).exists() || fatalMarker(application).delete()) {
                "Cannot remove crash marker"
              }
            }
            recent.clear()
            dropped.set(0)
            exitCutoff = System.currentTimeMillis()
            app
              ?.getSharedPreferences("beta_diagnostics", Context.MODE_PRIVATE)
              ?.edit()
              ?.putLong("exit_cutoff", exitCutoff)
              ?.apply()
            mutableState.update {
              it.copy(
                storedBytes = 0,
                recentLines = emptyList(),
                lastError = null,
                hasRecoveredCrash = false,
              )
            }
          } catch (e: Exception) {
            reportError("Could not clear diagnostics", e)
          }
        }
        if (state.value.isRecording) startLogcat()
      }
    }
  }

  private fun append(category: String, text: String) {
    try {
      store?.append(category, text)
      addRecent(category, text)
    } catch (e: Exception) {
      reportError("Could not write diagnostics (check free storage)", e)
    }
  }

  private fun addRecent(category: String, text: String) {
    recent.addLast(DiagnosticRedactor.redact("[$category] $text").take(4000))
    while (recent.size > 200) recent.removeFirst()
  }

  private fun reportError(message: String, error: Exception) {
    mutableState.update { it.copy(lastError = "$message: ${error.javaClass.simpleName}") }
  }

  private fun stopLogcat() {
    logcatEpoch++
    process?.destroy()
    process = null
    mutableState.update { it.copy(logcatStatus = "Stopped") }
  }

  private fun startLogcat(sinceTime: Instant = Instant.now()) {
    val (captureEpoch, captureLogcatEpoch) = synchronized(gate) { epoch to logcatEpoch }
    scope.launch {
      var child: java.lang.Process? = null
      try {
        // PID scope includes Java/Kotlin and native runtime threads, without READ_LOGS permission.
        // Start at now; never replay cleared or paused log-buffer contents.
        val since =
          DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
            .withZone(java.time.ZoneId.systemDefault())
            .format(sinceTime)
        val started =
          ProcessBuilder(
              "logcat",
              "--pid=${Process.myPid()}",
              "-b",
              "main,system,crash",
              "-v",
              "threadtime",
              "-T",
              since,
              "*:V",
            )
            .redirectErrorStream(true)
            .start()
        child = started
        synchronized(gate) {
          if (
            captureEpoch != epoch || captureLogcatEpoch != logcatEpoch || !state.value.isRecording
          ) {
            started.destroy()
            return@launch
          }
          process = started
          mutableState.update { it.copy(logcatStatus = "Waiting for app logs") }
        }
        started.inputStream.bufferedReader().use { reader ->
          while (true) {
            val line = reader.readLine() ?: break
            synchronized(gate) {
              if (
                captureEpoch != epoch ||
                  captureLogcatEpoch != logcatEpoch ||
                  !state.value.isRecording
              )
                return@launch
              if (!records.trySend(Record(captureEpoch, "logcat", line)).isSuccess)
                dropped.incrementAndGet()
              wakeups.trySend(Unit)
              if (line.contains(Process.myPid().toString()))
                mutableState.update { it.copy(logcatStatus = "Capturing app logs") }
            }
          }
        }
        synchronized(gate) {
          if (
            captureEpoch == epoch && captureLogcatEpoch == logcatEpoch && state.value.isRecording
          ) {
            mutableState.update { it.copy(logcatStatus = "Unavailable; app events still recorded") }
            append(
              "recorder",
              "${Instant.now()} logcat exited; native/legacy logs may be incomplete",
            )
          }
        }
      } catch (e: Exception) {
        synchronized(gate) {
          if (
            captureEpoch == epoch && captureLogcatEpoch == logcatEpoch && state.value.isRecording
          ) {
            mutableState.update { it.copy(logcatStatus = "Unavailable; app events still recorded") }
            append("recorder", "${Instant.now()} logcat unavailable: ${e.javaClass.simpleName}")
          }
        }
      } finally {
        child?.destroy()
      }
    }
  }

  private fun installCrashHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        synchronized(gate) {
          if (state.value.isRecording) {
            // Flush directly: the asynchronous queue may never run again after a fatal exception.
            drainPendingRecords()
            append(
              "fatal",
              "${Instant.now()} thread=${thread.name}\n${throwable.stackTraceToString().take(128 * 1024)}",
            )
            runCatching {
              store?.sync()
              app?.let { application ->
                EncryptedPrivateFile.write(
                  fatalMarker(application),
                  "${Instant.now()} ${throwable.javaClass.name}".toByteArray(),
                  FATAL_PURPOSE,
                )
              }
            }
          }
        }
      } finally {
        if (previous != null) previous.uncaughtException(thread, throwable)
        else {
          Process.killProcess(Process.myPid())
          kotlin.system.exitProcess(10)
        }
      }
    }
  }

  /** Call only under gate, before changing capture epoch, so Pause preserves accepted events. */
  private fun drainPendingRecords() {
    val pending = ArrayList<Record>()
    for (index in 0 until 1024) {
      val record = records.tryReceive().getOrNull() ?: break
      if (record.epoch == epoch) pending.add(record)
    }
    if (pending.isEmpty()) return
    try {
      store?.appendAll(pending.map { it.category to it.text })
      pending.forEach { addRecent(it.category, it.text) }
    } catch (e: Exception) {
      reportError("Could not write diagnostics (check free storage)", e)
    }
  }

  private fun observeLifecycle(application: Application) {
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
          event(
            "lifecycle",
            "${activity.javaClass.simpleName} created; restored=${savedInstanceState != null}",
          )

        override fun onActivityStarted(activity: Activity) {
          foregroundActivities.incrementAndGet()
          event("lifecycle", "${activity.javaClass.simpleName} started")
        }

        override fun onActivityResumed(activity: Activity) =
          event("lifecycle", "${activity.javaClass.simpleName} resumed")

        override fun onActivityPaused(activity: Activity) =
          event("lifecycle", "${activity.javaClass.simpleName} paused")

        override fun onActivityStopped(activity: Activity) {
          foregroundActivities.decrementAndGet()
          event("lifecycle", "${activity.javaClass.simpleName} stopped")
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) =
          event("lifecycle", "${activity.javaClass.simpleName} destroyed")
      }
    )
    application.registerComponentCallbacks(
      object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) =
          event(
            "configuration",
            "orientation=${newConfig.orientation}; fontScale=${newConfig.fontScale}; uiMode=${newConfig.uiMode}",
          )

        override fun onLowMemory() = event("memory", "System low-memory callback")

        override fun onTrimMemory(level: Int) = event("memory", "System trim-memory level=$level")
      }
    )
  }

  private fun startHealthMonitor() {
    val handler = Handler(Looper.getMainLooper())
    val pendingHeartbeat = AtomicLong()
    scope.launch {
      var stalled = false
      var lastSample = 0L
      while (true) {
        delay(2000)
        if (!state.value.isRecording || foregroundActivities.get() <= 0) {
          pendingHeartbeat.set(0)
          stalled = false
          continue
        }
        val now = SystemClock.uptimeMillis()
        val pending = pendingHeartbeat.get()
        if (pending == 0L) {
          if (stalled) event("responsiveness", "Main thread recovered")
          stalled = false
          pendingHeartbeat.set(now)
          handler.post { pendingHeartbeat.compareAndSet(now, 0) }
        } else if (!stalled && now - pending > 5000 && !Debug.isDebuggerConnected()) {
          stalled = true
          event(
            "responsiveness",
            "Possible main-thread stall (${now - pending} ms); not an OS-confirmed ANR\n${Looper.getMainLooper().thread.stackTrace.take(64).joinToString("\n")}",
          )
        }
        if (now - lastSample >= 30000) {
          lastSample = now
          runCatching { event("health", runtimeSnapshot(app!!).toString()) }
            .onFailure { event("health", "Sample unavailable: ${it.javaClass.simpleName}") }
        }
      }
    }
  }

  /**
   * Produces a self-contained archive in private cache. The UI chooses Save or Share explicitly.
   */
  suspend fun createExport(context: Context, note: String): File =
    withContext(Dispatchers.IO) {
      check(privateStorageUnlocked) { "Unlock private diagnostics before exporting" }
      exports.withLock {
        controls.withLock {
          val entries = linkedMapOf<String, ByteArray>()
          fun add(name: String, text: String) {
            entries[name] = DiagnosticRedactor.redact(text).toByteArray(Charsets.UTF_8)
          }
          val snapshot =
            synchronized(gate) {
              val capture = store ?: error("Diagnostics storage is unavailable")
              // Include all events already accepted before export, even if the background writer is
              // busy.
              drainPendingRecords()
              capture.snapshot()
            }
          snapshot.forEach { (name, bytes) -> entries["logs/$name"] = bytes }
          // A separate frozen copy protects the last crash while the tester keeps using the app.
          synchronized(gate) {
            val recovered = recoveryFile(context)
            if (recovered.isFile) entries["previous-crash.zip"] =
              EncryptedPrivateFile.read(recovered, RECOVERY_PURPOSE)
          }
          add("README.txt", EXPORT_README)
          add("issue-note.txt", note.take(16000))
          add("device-and-app.json", deviceSnapshot(context).toString(2))
          add("runtime-at-export.json", runtimeSnapshot(context).toString(2))
          add(
            "capture-status.json",
            JSONObject()
              .apply {
                put("exportedAt", Instant.now().toString())
                put("sessionId", state.value.sessionId)
                put("recording", state.value.isRecording)
                put("hasRecoveredCrash", state.value.hasRecoveredCrash)
                put("logcat", state.value.logcatStatus)
                put("lastError", state.value.lastError ?: JSONObject.NULL)
                put("retentionLimitBytes", 20L * 1024 * 1024)
                put("capturedBytes", snapshot.values.sumOf { it.size.toLong() })
                put("pendingDroppedLines", dropped.get())
                put("exitHistorySince", Instant.ofEpochMilli(exitCutoff).toString())
              }
              .toString(2),
          )
          collectExits(context, ::add)
          val directory =
            File(context.cacheDir, "diagnostics-exports").apply {
              check(isDirectory || mkdirs()) { "Cannot create export folder" }
            }
          // Keep a small number for share receivers; never delete the archive currently being
          // created.
          directory
            .listFiles()
            ?.filter { it.name.startsWith("gallery-diagnostics-") && it.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(2)
            ?.forEach { it.delete() }
          val stamp =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
              .withZone(ZoneOffset.UTC)
              .format(Instant.now())
          val output =
            File(
              directory,
              "gallery-diagnostics-$stamp-${UUID.randomUUID().toString().take(8)}.zip",
            )
          DiagnosticArchive.write(output, entries)
          output
        }
      }
    }

  private fun recoveryFile(context: Context) =
    File(context.noBackupFilesDir, "diagnostics-previous-crash.zip.enc")

  private fun fatalMarker(context: Context) =
    File(context.noBackupFilesDir, "diagnostics-fatal.txt.enc")

  /** Call only while controls excludes archive writers; process death can leave these behind. */
  private fun cleanupStaleArchiveFiles(context: Context) {
    for (directory in
      listOf(context.noBackupFilesDir, File(context.cacheDir, "diagnostics-exports"))) {
      if (!directory.exists()) continue
      val files = checkNotNull(directory.listFiles()) { "Cannot list diagnostic archive directory" }
      for (file in files) {
        if (
          ((file.name.startsWith("diagnostic-archive-") && file.name.endsWith(".zip.tmp")) ||
            (directory.name == "diagnostics-exports" &&
              file.name.startsWith("gallery-diagnostics-") &&
              file.extension == "zip")) &&
            !java.nio.file.Files.isDirectory(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
        ) {
          java.nio.file.Files.deleteIfExists(file.toPath())
        }
      }
    }
  }

  private fun recoverPreviousCrash(context: Context) {
    // Startup holds controls before exports are available, so no current archive can be removed.
    try {
      cleanupStaleArchiveFiles(context)
    } catch (e: Exception) {
      reportError("Could not remove interrupted diagnostic archives", e)
    }
    try {
      val prefs = context.getSharedPreferences("beta_diagnostics", Context.MODE_PRIVATE)
      val marker = fatalMarker(context)
      val enabled =
        prefs.getBoolean("enabled", false)
      if (!marker.exists() && !enabled) {
        // A paused session must not replace an existing capture with new OS crash/ANR reports.
        // Advance the recovery watermark so enabling recording later cannot resurrect those exits.
        prefs.edit().putLong("last_recovered_exit", System.currentTimeMillis()).apply()
        mutableState.update { it.copy(hasRecoveredCrash = recoveryFile(context).isFile) }
        return
      }
      val lastRecovered = maxOf(exitCutoff, prefs.getLong("last_recovered_exit", 0))
      val crash = runCatching {
        context
          .getSystemService(ActivityManager::class.java)
          .getHistoricalProcessExitReasons(context.packageName, 0, 10)
          .filter {
            it.timestamp > lastRecovered &&
              it.reason in
                setOf(
                  ApplicationExitInfo.REASON_CRASH,
                  ApplicationExitInfo.REASON_CRASH_NATIVE,
                  ApplicationExitInfo.REASON_ANR,
                )
          }
          .maxByOrNull { it.timestamp }
      }
        .getOrNull()
      if (marker.exists() || crash != null) {
        val entries = linkedMapOf<String, ByteArray>()
        store?.snapshot()?.forEach { (name, bytes) -> entries["logs/$name"] = bytes }
        fun add(name: String, value: String) {
          entries[name] = DiagnosticRedactor.redact(value).toByteArray()
        }
        add(
          "README.txt",
          "Recovered on the next app launch before recording resumed. This frozen archive preserves the latest crash capture until Clear logs or another crash.\n\n$EXPORT_README",
        )
        if (marker.exists()) {
          val bytes = EncryptedPrivateFile.read(marker, FATAL_PURPOSE, maxBytes = 4096)
          try { add("fatal-marker.txt", bytes.toString(Charsets.UTF_8)) }
          finally { bytes.fill(0) }
        }
        add("device-and-app.json", deviceSnapshot(context).toString(2))
        collectExits(context, ::add)
        val archive = DiagnosticArchive.encode(entries)
        try { EncryptedPrivateFile.write(recoveryFile(context), archive, RECOVERY_PURPOSE) }
        finally { archive.fill(0) }
        prefs
          .edit()
          .putLong("last_recovered_exit", crash?.timestamp ?: System.currentTimeMillis())
          .apply()
        marker.delete()
      }
      mutableState.update { it.copy(hasRecoveredCrash = recoveryFile(context).isFile) }
    } catch (e: Exception) {
      reportError("Crash recovery could not be frozen; retained logs are still available", e)
    }
  }

  private fun deviceSnapshot(context: Context): JSONObject =
    JSONObject().apply {
      put("package", context.packageName)
      put("versionName", BuildConfig.VERSION_NAME)
      put("versionCode", BuildConfig.VERSION_CODE)
      put("buildType", BuildConfig.BUILD_TYPE)
      put("manufacturer", Build.MANUFACTURER)
      put("model", Build.MODEL)
      put("device", Build.DEVICE)
      put("androidVersion", Build.VERSION.RELEASE)
      put("sdk", Build.VERSION.SDK_INT)
      put("securityPatch", Build.VERSION.SECURITY_PATCH)
      put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
      put("locale", context.resources.configuration.locales.toLanguageTags())
      put("fontScale", context.resources.configuration.fontScale)
      put("densityDpi", context.resources.displayMetrics.densityDpi)
      val info =
        context.packageManager.getPackageInfo(
          context.packageName,
          android.content.pm.PackageManager.GET_PERMISSIONS,
        )
      put("firstInstallTime", info.firstInstallTime)
      put("lastUpdateTime", info.lastUpdateTime)
      put(
        "permissions",
        JSONObject().apply {
          info.requestedPermissions?.forEach { permission ->
            put(
              permission,
              context.checkSelfPermission(permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
          }
        },
      )
    }

  private fun runtimeSnapshot(context: Context): JSONObject =
    JSONObject().apply {
      put("timestamp", Instant.now().toString())
      put("uptimeMs", SystemClock.uptimeMillis())
      put("processCpuTimeMs", Process.getElapsedCpuTime())
      val runtime = Runtime.getRuntime()
      put("javaHeapUsedBytes", runtime.totalMemory() - runtime.freeMemory())
      put("javaHeapMaxBytes", runtime.maxMemory())
      put("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize())
      put("storageFreeBytes", context.filesDir.usableSpace)
      val memory = ActivityManager.MemoryInfo()
      context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
      put("ramAvailableBytes", memory.availMem)
      put("ramTotalBytes", memory.totalMem)
      put("lowMemory", memory.lowMemory)
      val power = context.getSystemService(PowerManager::class.java)
      put("thermalStatus", power.currentThermalStatus)
      put("powerSaveMode", power.isPowerSaveMode)
      context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { battery ->
        put("batteryLevel", battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1))
        put("batteryScale", battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
        put("batteryTemperatureTenthsC", battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1))
        put("batteryStatus", battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
      }
      runCatching {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        put("networkAvailable", capabilities != null)
        put(
          "networkValidated",
          capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) ==
            true,
        )
        put("networkMetered", connectivity.isActiveNetworkMetered)
      }
    }

  private fun collectExits(context: Context, add: (String, String) -> Unit) {
    try {
      val exits =
        context
          .getSystemService(ActivityManager::class.java)
          .getHistoricalProcessExitReasons(context.packageName, 0, 10)
          .filter { it.timestamp > exitCutoff }
      val items = JSONArray()
      exits.forEachIndexed { index, exit ->
        val item =
          JSONObject().apply {
            put("timestamp", Instant.ofEpochMilli(exit.timestamp).toString())
            put("reason", exit.reason)
            put("status", exit.status)
            put("description", exit.description)
            put("importance", exit.importance)
            put("pssKb", exit.pss)
            put("rssKb", exit.rss)
          }
        // Native tombstones are protobuf, not text; include metadata rather than unredactable
        // blobs.
        if (exit.reason == ApplicationExitInfo.REASON_ANR) {
          runCatching {
            exit.traceInputStream?.use { stream ->
              val bytes = stream.readBytesUpTo(256 * 1024)
              add(
                "exits/anr-$index.txt",
                bytes.toString(Charsets.UTF_8) + "\n[Trace capped at 256 KiB]\n",
              )
              item.put("trace", "exits/anr-$index.txt")
            }
          }
            .onFailure { item.put("traceError", it.javaClass.simpleName) }
        }
        items.put(item)
      }
      add("process-exits.json", items.toString(2))
    } catch (e: Exception) {
      add("process-exits.json", JSONObject().put("unavailable", e.javaClass.simpleName).toString(2))
    }
  }

  private fun java.io.InputStream.readBytesUpTo(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
      val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
      if (count < 0) break
      output.write(buffer, 0, count)
    }
    return output.toByteArray()
  }

  private val EXPORT_README =
    """
    Gallery beta diagnostics

    All collection is local. Nothing is uploaded by the recorder.
    logs/: chronological rotating app events and app-process logcat (including native runtime logs).
    device-and-app.json: build, OS, device, permissions and display configuration; no device serial.
    runtime-at-export.json: memory, storage, battery, thermal, connectivity and CPU-time snapshot.
    process-exits.json: OS-provided recent app exits, including crashes/ANRs when available.
    exits/: available ANR text traces, capped at 256 KiB each.
    capture-status.json: capture state, limits and missing-source/error information.
    issue-note.txt: the tester's reproduction notes.
    previous-crash.zip: the latest recovered crash capture, when available; kept separately from rotation.

    Recording is opt-in and can be paused or cleared in Settings > Beta diagnostics.
    Logs survive app restarts as authenticated encrypted files in private no-backup storage.
    The most recent 20 MiB of log text are retained, plus small encryption envelopes.
    After a Java/native crash or ANR reported by Android, the next launch freezes the preceding logs
    before recording resumes. One additional recovery archive (up to about 23 MiB) is kept until clear
    or the next recovered crash. Stop is never required before exporting. Sudden kills/power loss may
    lose the final queued or buffered lines. A launch-loop that prevents Settings opening may require
    a system bug report; do not uninstall or clear app storage before recovering logs.
    Old records rotate out; individual long records and the live viewer are truncated.
    A busy capture queue can drop lines; this is counted in recorder events/status.
    Health is sampled every 30 seconds while an activity is visible. Main-thread stalls over 5 seconds
    are best-effort observations, not OS-confirmed ANRs. Background/frozen/killed processes cannot record.
    Logcat is restricted to this app process and may be unavailable on some phones. This archive is
    not a full Android system bug report. Native crash details and OS exit traces may be unavailable.
    A full system bug report requires Android Developer options > Take bug report (or adb bugreport).

    Common credential formats are masked on a best-effort basis before storage/export. Logs and notes
    can still contain personal conversation/tool text from existing app/native logging. Review before
    sharing. Chat databases, model weights, photos, audio, credential stores, and other apps' logs are
    not copied. This explicitly exported ZIP is plaintext. Saved/shared ZIP copies remain wherever
    you put them after clearing in-app logs. Anyone who can open that ZIP can read its contents.
    """
      .trimIndent()
}
