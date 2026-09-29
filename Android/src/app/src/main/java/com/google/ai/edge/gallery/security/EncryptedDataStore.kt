package com.google.ai.edge.gallery.security

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.google.ai.edge.gallery.BenchmarkResultsSerializer
import com.google.ai.edge.gallery.CutoutsSerializer
import com.google.ai.edge.gallery.SettingsSerializer
import com.google.ai.edge.gallery.SkillsSerializer
import com.google.ai.edge.gallery.UserDataSerializer
import com.google.ai.edge.gallery.customtasks.agentchat.McpServersSerializer
import com.google.ai.edge.gallery.notifications.ScheduledNotificationsSerializer
import com.google.ai.edge.gallery.ui.common.onboarding.OnboardingSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import kotlinx.coroutines.runBlocking

/** No plaintext fallback: authentication failures preserve the file and fail the read. */
internal class EncryptedSerializer<T>(
  private val delegate: Serializer<T>,
  private val purpose: String,
  private val encrypt: (ByteArray, String) -> ByteArray = PrivateDataEncryption::encrypt,
  private val decrypt: (ByteArray, String) -> ByteArray = PrivateDataEncryption::decrypt,
) : Serializer<T> {
  override val defaultValue: T get() = delegate.defaultValue

  override suspend fun readFrom(input: InputStream): T {
    val plaintext = try {
      decrypt(input.readBytes(), purpose)
    } catch (failure: GeneralSecurityException) {
      throw IOException("Cannot authenticate private storage", failure)
    }
    try {
      return delegate.readFrom(ByteArrayInputStream(plaintext))
    } finally {
      plaintext.fill(0)
    }
  }

  override suspend fun writeTo(t: T, output: OutputStream) {
    val plaintext = ByteArrayOutputStream().also { delegate.writeTo(t, it) }.toByteArray()
    try {
      output.write(encrypt(plaintext, purpose))
    } finally {
      plaintext.fill(0)
    }
  }
}

/** Migrates once into a separate non-backup file before DataStore can read or write it. */
object EncryptedDataStore {
  /** Also protects dormant stores that no screen has opened during this session. */
  fun migrateAll(context: Context) {
    migrate(context, context.dataStoreFile("settings.pb"), SettingsSerializer, "datastore/settings.pb")
    migrate(context, context.dataStoreFile("user_data.pb"), UserDataSerializer, "datastore/user_data.pb")
    migrate(context, context.dataStoreFile("cutouts.pb"), CutoutsSerializer, "datastore/cutouts.pb")
    migrate(context, context.dataStoreFile("skills.pb"), SkillsSerializer, "datastore/skills.pb")
    migrate(context, context.dataStoreFile("benchmark_results.pb"), BenchmarkResultsSerializer, "datastore/benchmark_results.pb")
    migrate(context, context.dataStoreFile("mcp_servers.pb"), McpServersSerializer, "datastore/mcp_servers.pb")
    migrate(context, context.dataStoreFile("onboarding_data.pb"), OnboardingSerializer, "datastore/onboarding_data.pb")
    migrate(context, File(context.filesDir, "scheduled_notifications.pb"), ScheduledNotificationsSerializer, "datastore/scheduled_notifications.pb")
  }

  fun <T> create(context: Context, legacyFile: File, serializer: Serializer<T>): DataStore<T> {
    val purpose = "datastore/${legacyFile.name}"
    return DataStoreFactory.create(
      serializer = EncryptedSerializer(serializer, purpose),
      produceFile = { migrate(context, legacyFile, serializer, purpose) },
    )
  }

  @Synchronized
  private fun <T> migrate(context: Context, legacy: File, serializer: Serializer<T>, purpose: String): File {
    val directory = File(context.noBackupFilesDir, "private_datastore")
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create private storage" }
    val destination = File(directory, "${legacy.name}.enc")
    val atomic = AtomicFile(destination)
    if (destination.exists() || File(destination.path + ".bak").exists()) {
      // Verify the authoritative encrypted file before removing a leftover legacy copy.
      if (legacy.exists()) {
        val plaintext = PrivateDataEncryption.decrypt(atomic.readFully(), purpose)
        try { runBlocking { serializer.readFrom(ByteArrayInputStream(plaintext)) } }
        finally { plaintext.fill(0) }
        check(legacy.delete()) { "Cannot remove legacy private data" }
      }
      return destination
    }
    if (!legacy.exists()) return destination
    val plaintext = legacy.readBytes()
    try {
      runBlocking { serializer.readFrom(ByteArrayInputStream(plaintext)) }
      val envelope = PrivateDataEncryption.encrypt(plaintext, purpose)
      val output = atomic.startWrite()
      try {
        output.write(envelope)
        atomic.finishWrite(output)
      } catch (failure: Throwable) {
        atomic.failWrite(output)
        throw failure
      }
      val verified = PrivateDataEncryption.decrypt(atomic.readFully(), purpose)
      try { check(verified.contentEquals(plaintext)) { "Private data migration failed verification" } }
      finally { verified.fill(0) }
      check(legacy.delete()) { "Cannot remove legacy private data" }
      return destination
    } finally {
      plaintext.fill(0)
    }
  }
}
