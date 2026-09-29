/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedPrivateFileTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test
  fun publishesOnlyCompleteEnvelopeAndRemovesTemporaryFiles() {
    val directory = temporary.newFolder()
    val target = File(directory, "private.enc")
    val first = ByteArray(512) { it.toByte() }
    val second = ByteArray(1024) { (it xor 0x5a).toByte() }
    EncryptedPrivateFile.writeEnvelope(target, first)
    EncryptedPrivateFile.writeEnvelope(target, second)
    assertArrayEquals(second, target.readBytes())
    assertEquals(listOf("private.enc"), directory.listFiles()!!.map { it.name })
  }

  @Test
  fun refusesSymlinkDestinationWithoutOverwritingOtherFile() {
    val original = temporary.newFile().apply { writeText("keep unrelated file") }
    val link = File(temporary.newFolder(), "private.enc")
    Files.createSymbolicLink(link.toPath(), original.toPath())
    assertThrows(IOException::class.java) {
      EncryptedPrivateFile.writeEnvelope(link, byteArrayOf(1, 2, 3))
    }
    assertEquals("keep unrelated file", original.readText())
  }

  @Test
  fun refusesSymlinkPrivateParent() {
    val directory = temporary.newFolder()
    val link = File(temporary.root, "private-directory")
    Files.createSymbolicLink(link.toPath(), directory.toPath())
    assertThrows(IOException::class.java) {
      EncryptedPrivateFile.writeEnvelope(File(link, "private.enc"), byteArrayOf(1, 2, 3))
    }
  }

  @Test
  fun boundsReadsAndRejectsSymlinkSources() {
    val original = temporary.newFile().apply { writeBytes(ByteArray(100)) }
    assertThrows(IOException::class.java) { EncryptedPrivateFile.readBounded(original, 99) }
    val link = File(temporary.newFolder(), "private.enc")
    Files.createSymbolicLink(link.toPath(), original.toPath())
    assertThrows(IOException::class.java) { EncryptedPrivateFile.readBounded(link, 100) }
  }
}
