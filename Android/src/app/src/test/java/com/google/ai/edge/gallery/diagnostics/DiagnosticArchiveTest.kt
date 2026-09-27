/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticArchiveTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test
  fun zipContainsExactlyTheProvidedEntriesAndBytes() {
    val output = File(temporary.root, "report.zip")
    val entries =
      linkedMapOf(
        "README.txt" to "Diagnostics report 🎙️".toByteArray(),
        "logs/diagnostic-00000000000000000000.log" to byteArrayOf(0, 1, 127, -1),
      )
    DiagnosticArchive.write(output, entries)

    ZipFile(output).use { zip ->
      assertEquals(entries.keys.toList(), zip.entries().asSequence().map { it.name }.toList())
      for ((name, bytes) in entries) {
        assertArrayEquals(bytes, zip.getInputStream(zip.getEntry(name)).use { it.readBytes() })
      }
    }
    assertEquals(listOf("report.zip"), temporary.root.list()!!.toList())
  }

  @Test
  fun rejectsUnsafeNamesBeforeReplacingAnExistingExport() {
    val output = temporary.newFile("report.zip").apply { writeText("existing report") }
    for (name in
      listOf(
        "../secret",
        "/absolute",
        "logs/../../secret",
        "a\\b",
        "a//b",
        "a/./b",
        "C:/a",
        "a\u0000b",
      )) {
      assertThrows(IllegalArgumentException::class.java) {
        DiagnosticArchive.write(output, mapOf(name to byteArrayOf(1)))
      }
      assertEquals("existing report", output.readText())
    }
    assertEquals(1, temporary.root.listFiles()!!.size)
  }

  @Test
  fun replacesOldExportWithACompleteNewZip() {
    val output = temporary.newFile("report.zip").apply { writeText("previous report") }
    DiagnosticArchive.write(output, mapOf("new.txt" to "new contents".toByteArray()))

    ZipFile(output).use { zip ->
      assertEquals(1, zip.size())
      assertEquals(
        "new contents",
        zip.getInputStream(zip.getEntry("new.txt")).bufferedReader().use { it.readText() },
      )
    }
    assertFalse(temporary.root.list()!!.any { it.endsWith(".tmp") })
  }

  @Test
  fun refusesToWriteThroughAnOutputSymlink() {
    val outside = temporary.newFile("private.txt").apply { writeText("keep") }
    val output = File(temporary.root, "report.zip")
    Files.createSymbolicLink(output.toPath(), outside.toPath())

    assertThrows(IOException::class.java) { DiagnosticArchive.write(output, emptyMap()) }
    assertEquals("keep", outside.readText())
  }
}
