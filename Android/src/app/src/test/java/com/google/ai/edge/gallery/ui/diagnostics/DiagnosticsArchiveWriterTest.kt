/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.diagnostics

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsArchiveWriterTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  private fun archive(): File =
    temporaryFolder.newFile().apply { writeBytes(ByteArray(100_000) { (it % 251).toByte() }) }

  @Test
  fun copiesEntireArchiveAndClosesDestination() {
    val archive = archive()
    var closed = false
    val output =
      object : ByteArrayOutputStream() {
        override fun close() {
          closed = true
          super.close()
        }
      }
    writeDiagnosticsArchive(archive) { output }
    assertArrayEquals(archive.readBytes(), output.toByteArray())
    assertTrue(closed)
  }

  @Test
  fun emptyArchiveDoesNotOpenDestination() {
    var opened = false
    assertThrows(IOException::class.java) {
      writeDiagnosticsArchive(temporaryFolder.newFile()) {
        opened = true
        ByteArrayOutputStream()
      }
    }
    assertFalse(opened)
  }

  @Test
  fun unavailableDestinationFails() {
    assertThrows(IOException::class.java) { writeDiagnosticsArchive(archive()) { null } }
  }

  @Test
  fun writeFailureIsReportedAndDestinationClosed() {
    var closed = false
    val output =
      object : OutputStream() {
        override fun write(value: Int) {
          throw IOException("Storage full")
        }

        override fun close() {
          closed = true
        }
      }
    assertThrows(IOException::class.java) { writeDiagnosticsArchive(archive()) { output } }
    assertTrue(closed)
  }

  @Test
  fun closeFailureIsReportedInsteadOfSuccess() {
    val output =
      object : ByteArrayOutputStream() {
        override fun close() {
          throw IOException("Provider could not finish document")
        }
      }
    assertThrows(IOException::class.java) { writeDiagnosticsArchive(archive()) { output } }
  }
}
