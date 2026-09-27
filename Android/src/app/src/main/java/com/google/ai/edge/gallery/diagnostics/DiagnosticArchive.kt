/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticArchive {
  /** Publishes the completed ZIP atomically when supported; failed exports leave no partial ZIP. */
  fun write(output: File, entries: Map<String, ByteArray>) {
    for (name in entries.keys) {
      require(
        name.isNotEmpty() &&
          '\\' !in name &&
          '\u0000' !in name &&
          ':' !in name &&
          name.split('/').none { it.isEmpty() || it == "." || it == ".." }
      ) {
        "Archive entries must use safe relative paths"
      }
    }
    if (Files.isSymbolicLink(output.toPath())) throw IOException("Archive must not be a symlink")
    val parent = output.absoluteFile.parentFile
    Files.createDirectories(parent.toPath())
    val temporary = File.createTempFile("diagnostic-archive-", ".zip.tmp", parent)
    try {
      ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
        for ((name, bytes) in entries) {
          zip.putNextEntry(ZipEntry(name))
          zip.write(bytes)
          zip.closeEntry()
        }
      }
      try {
        Files.move(
          temporary.toPath(),
          output.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING,
        )
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      Files.deleteIfExists(temporary.toPath())
    }
  }
}
