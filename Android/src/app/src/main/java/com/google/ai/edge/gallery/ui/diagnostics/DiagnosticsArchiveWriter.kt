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

import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Returns only after the entire archive and the destination stream have successfully closed. */
internal fun writeDiagnosticsArchive(archive: File, openOutput: () -> OutputStream?) {
  if (!archive.isFile || archive.length() == 0L) {
    throw IOException("Diagnostics archive is empty or missing")
  }
  val expectedBytes = archive.length()
  val output = openOutput() ?: throw IOException("Document provider did not open an output stream")
  output.use { destination ->
    archive.inputStream().use { source ->
      if (source.copyTo(destination) != expectedBytes) {
        throw IOException("Diagnostics archive copy was incomplete")
      }
    }
    destination.flush()
  }
}
