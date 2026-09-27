/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import java.io.File
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

  @Test
  fun rotationBoundsAllChannelsTogetherAndRetainsTheNewestData() {
    val store = DiagnosticStore(temporary.newFolder(), maxFileBytes = 64, maxFiles = 3)
    repeat(100) { store.append(if (it % 2 == 0) "event" else "logcat", "Record $it") }
    val files = store.snapshot()

    assertEquals(3, files.size)
    assertTrue(files.values.all { it.size <= 64 })
    assertTrue(store.sizeBytes() <= 192)
    val text = files.values.joinToString("") { it.toString(Charsets.UTF_8) }
    assertTrue(text.endsWith("[logcat] Record 99\n"))
    assertFalse(text.contains("Record 0\n"))
    assertEquals(files.values.sumOf { it.size }.toLong(), store.sizeBytes())
  }

  @Test
  fun processRestartContinuesSequenceAndRotation() {
    val directory = temporary.newFolder()
    val first = DiagnosticStore(directory, maxFileBytes = 32, maxFiles = 2)
    first.append("event", "a".repeat(200))
    val previous = first.snapshot().keys.last()
    val restarted = DiagnosticStore(directory, maxFileBytes = 32, maxFiles = 2)
    restarted.append("event", "b".repeat(200))

    assertEquals(2, restarted.snapshot().size)
    assertTrue(restarted.snapshot().keys.first() > previous)
    assertTrue(restarted.sizeBytes() <= 64)
  }

  @Test
  fun unicodeCodePointsRemainDecodableAcrossEveryFileBoundary() {
    val store = DiagnosticStore(temporary.newFolder(), maxFileBytes = 19, maxFiles = 100)
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
    val store = DiagnosticStore(directory)
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
    assertThrows(IOException::class.java) { DiagnosticStore(link) }
  }

  @Test
  fun secretFilteringHappensBeforeAnyDiskWrite() {
    val directory = temporary.newFolder()
    val store = DiagnosticStore(directory)
    store.append("http", "Authorization: Bearer my-secret-authorization-value")

    assertTrue(directory.listFiles()!!.isNotEmpty())
    for (file in directory.listFiles()!!) {
      assertFalse(file.readText().contains("my-secret-authorization-value"))
      assertTrue(file.readText().contains("[REDACTED]"))
    }
  }

  @Test
  fun snapshotsRemainStableAndParallelWritesDoNotInterleaveRecords() {
    val store = DiagnosticStore(temporary.newFolder(), maxFileBytes = 8192, maxFiles = 10)
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
    val first = DiagnosticStore(directory, maxFileBytes = 1024, maxFiles = 5)
    first.append("old", "a".repeat(4500))
    val restarted = DiagnosticStore(directory, maxFileBytes = 64, maxFiles = 2)
    restarted.append("new", "after restart")

    assertTrue(restarted.snapshot().size <= 2)
    assertTrue(restarted.snapshot().values.all { it.size <= 64 })
    assertTrue(restarted.sizeBytes() <= 128)
  }
}
