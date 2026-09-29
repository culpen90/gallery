// Modified for the Gallery Android fork (Beta 5).
/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import com.google.ai.edge.gallery.security.EnvelopeEncryption
import java.io.File
import javax.crypto.spec.SecretKeySpec
import java.security.GeneralSecurityException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticStoreTest {
  @get:Rule val temporary = TemporaryFolder()
  private val cipher = EnvelopeEncryption { SecretKeySpec(ByteArray(32) { it.toByte() }, "AES") }

  private fun newStore(directory: File, maxFileBytes: Long = 64 * 1024, maxFiles: Int = 320) =
    DiagnosticStore(directory, maxFileBytes, maxFiles, cipher::encrypt, cipher::decrypt)

  @Test
  fun burstUsesBoundedHardwareOperationsAndPreservesRedactedRecords() {
    val directory = temporary.newFolder()
    var encryptionCalls = 0
    val store = DiagnosticStore(directory, encrypt = { bytes, purpose ->
      encryptionCalls++
      cipher.encrypt(bytes, purpose)
    }, decrypt = cipher::decrypt)
    store.append("existing", "retained before the burst")
    encryptionCalls = 0
    val records = (0 until 100).map { "event" to "burst-$it café 🎙️ token=hf_PrivateTestSecret12345" }
    store.appendAll(records)
    assertTrue("A burst must not wrap a separate hardware key per line", encryptionCalls <= 2)
    val reopened = newStore(directory)
    val text = reopened.snapshot().values.joinToString("") { it.toString(Charsets.UTF_8) }
    val expected = "[existing] retained before the burst\n" +
      records.joinToString("") { "[${it.first}] ${DiagnosticRedactor.redact(it.second)}\n" }
    assertEquals(expected, text)
    assertFalse(text.contains("hf_PrivateTestSecret12345"))
    assertTrue(directory.listFiles()!!.all { file ->
      !file.readBytes().toString(Charsets.UTF_8).contains("burst-")
    })
  }

  @Test
  fun batchPreservesUnicodeAcrossChunkBoundariesAndOversizedRecords() {
    val directory = temporary.newFolder()
    val store = newStore(directory, maxFileBytes = 19, maxFiles = 100)
    val records = listOf(
      "a" to "héllo 世界 🎙️ 😀".repeat(10),
      "b" to "tiny",
      "c" to "last café",
    )
    store.appendAll(records)
    val files = newStore(directory, maxFileBytes = 19, maxFiles = 100).snapshot()
    assertTrue(files.values.all { it.size <= 19 })
    val text = files.values.joinToString("") {
      Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(it)).toString()
    }
    assertEquals(records.joinToString("") { "[${it.first}] ${it.second}\n" }, text)
  }

  @Test
  fun migratesLegacyLogsAndNeverFallsBackAfterTampering() {
    val directory = temporary.newFolder()
    val legacy = File(directory, "diagnostic-00000000000000000001.log").apply {
      writeText("existing conversation diagnostic")
    }
    val store = newStore(directory)
    store.migrateLegacyLogs()
    assertFalse(legacy.exists())
    val encrypted = File(directory, "${legacy.name}.enc")
    assertFalse(encrypted.readBytes().toString(Charsets.UTF_8).contains("existing conversation"))
    assertEquals("existing conversation diagnostic", store.snapshot().values.single().toString(Charsets.UTF_8))
    val bytes = encrypted.readBytes()
    bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
    encrypted.writeBytes(bytes)
    legacy.writeText("must never be selected")
    assertThrows(GeneralSecurityException::class.java) { store.snapshot() }
    assertThrows(GeneralSecurityException::class.java) { store.migrateLegacyLogs() }
    assertTrue(legacy.exists())
  }

  @Test
  fun truncatedCiphertextFailsInsteadOfReturningPartialDiagnosticText() {
    val directory = temporary.newFolder()
    val store = newStore(directory)
    store.append("test", "private conversation")
    val encrypted = directory.listFiles()!!.single()
    encrypted.writeBytes(encrypted.readBytes().dropLast(1).toByteArray())
    assertThrows(GeneralSecurityException::class.java) { store.snapshot() }
  }

  @Test
  fun rotationBoundsAllChannelsTogetherAndRetainsTheNewestData() {
    val store = newStore(temporary.newFolder(), maxFileBytes = 64, maxFiles = 3)
    repeat(100) { store.append(if (it % 2 == 0) "event" else "logcat", "Record $it") }
    val files = store.snapshot()

    assertEquals(3, files.size)
    assertTrue(files.values.all { it.size <= 64 })
    assertTrue(store.sizeBytes() <= 3 * (64 + 93))
    val text = files.values.joinToString("") { it.toString(Charsets.UTF_8) }
    assertTrue(text.endsWith("[logcat] Record 99\n"))
    assertFalse(text.contains("Record 0\n"))
    assertEquals(files.values.sumOf { it.size }.toLong() + files.size * 93, store.sizeBytes())
  }

  @Test
  fun processRestartContinuesSequenceAndRotation() {
    val directory = temporary.newFolder()
    val first = newStore(directory, maxFileBytes = 32, maxFiles = 2)
    first.append("event", "a".repeat(200))
    val previous = first.snapshot().keys.last()
    val restarted = newStore(directory, maxFileBytes = 32, maxFiles = 2)
    restarted.append("event", "b".repeat(200))

    assertEquals(2, restarted.snapshot().size)
    assertTrue(restarted.snapshot().keys.first() > previous)
    assertTrue(restarted.sizeBytes() <= 2 * (32 + 93))
  }

  @Test
  fun unicodeCodePointsRemainDecodableAcrossEveryFileBoundary() {
    val store = newStore(temporary.newFolder(), maxFileBytes = 19, maxFiles = 100)
    val original = "héllo 世界 🎙️ 😀".repeat(30)
    store.append("audio", original)
    val files = store.snapshot()
    val text =
      files.values.joinToString("") {
        Charsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(it))
          .toString()
      }

    assertEquals("[audio] $original\n", text)
    assertTrue(files.values.all { it.size <= 19 })
  }

  @Test
  fun clearAndSnapshotTouchOnlyOwnedFilesAndNeverFollowSymlinks() {
    val directory = temporary.newFolder()
    val unrelated = File(directory, "notes.txt").apply { writeText("keep notes") }
    val outside = temporary.newFile().apply { writeText("private outside content") }
    val link = File(directory, "diagnostic-00000000000000000099.log")
    Files.createSymbolicLink(link.toPath(), outside.toPath())
    val store = newStore(directory)
    store.append("event", "public event")

    assertEquals(1, store.snapshot().size)
    assertFalse(store.snapshot().values.any { it.toString(Charsets.UTF_8).contains("private") })
    store.clear()
    assertTrue(store.snapshot().isEmpty())
    assertEquals("keep notes", unrelated.readText())
    assertEquals("private outside content", outside.readText())
    assertFalse(Files.isSymbolicLink(link.toPath()))
  }

  @Test
  fun refusesASymlinkAsTheStoreDirectory() {
    val directory = temporary.newFolder()
    val link = File(temporary.root, "linked-directory")
    Files.createSymbolicLink(link.toPath(), directory.toPath())
    assertThrows(IOException::class.java) { newStore(link) }
  }

  @Test
  fun secretFilteringHappensBeforeAnyDiskWrite() {
    val directory = temporary.newFolder()
    val store = newStore(directory)
    store.append("http", "Authorization: Bearer my-secret-authorization-value")

    assertTrue(directory.listFiles()!!.isNotEmpty())
    assertTrue(store.snapshot().values.single().toString(Charsets.UTF_8).contains("[REDACTED]"))
    for (file in directory.listFiles()!!) {
      assertFalse(file.readText().contains("my-secret-authorization-value"))
      assertFalse(file.readText().contains("[REDACTED]"))
    }
  }

  @Test
  fun snapshotsRemainStableAndParallelWritesDoNotInterleaveRecords() {
    val store = newStore(temporary.newFolder(), maxFileBytes = 8192, maxFiles = 10)
    store.append("event", "before")
    val before = store.snapshot().values.single().copyOf()
    val snapshot = store.snapshot()
    val executor = Executors.newFixedThreadPool(4)
    try {
      val workers =
        (0 until 4).map { worker ->
          executor.submit { repeat(100) { store.append("worker", "$worker:$it") } }
        }
      workers.forEach { it.get() }
    } finally {
      executor.shutdownNow()
    }
    val lines = store.snapshot().values.joinToString("") { it.toString(Charsets.UTF_8) }.lines()

    assertEquals(before.toList(), snapshot.values.single().toList())
    assertEquals(400, lines.count { it.startsWith("[worker] ") })
    repeat(4) { worker -> repeat(100) { assertTrue(lines.contains("[worker] $worker:$it")) } }
  }

  @Test
  fun smallerRetentionSettingsBoundFilesFromPreviousProcess() {
    val directory = temporary.newFolder()
    val first = newStore(directory, maxFileBytes = 1024, maxFiles = 5)
    first.append("old", "a".repeat(4500))
    val restarted = newStore(directory, maxFileBytes = 64, maxFiles = 2)
    restarted.append("new", "after restart")

    assertTrue(restarted.snapshot().size <= 2)
    assertTrue(restarted.snapshot().values.all { it.size <= 64 })
    assertTrue(restarted.sizeBytes() <= 2 * (64 + 93))
  }
}
