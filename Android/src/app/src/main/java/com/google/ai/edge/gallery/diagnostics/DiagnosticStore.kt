/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.Locale

/** A process-local synchronized, rotating store. Callers include timestamps in their log text. */
class DiagnosticStore(
  private val directory: File,
  private val maxFileBytes: Long = 2L * 1024 * 1024,
  private val maxFiles: Int = 10,
) {
  private val ownedName = Regex("diagnostic-([0-9]{20})\\.log")
  private var nextSequence = 0L

  init {
    require(maxFileBytes in 4..Int.MAX_VALUE.toLong()) { "A log file must fit a UTF-8 character" }
    require(maxFiles > 0) { "At least one log file is required" }
    ensureDirectory()
    val largest = entries().mapNotNull { sequence(it) }.maxOrNull()
    nextSequence = if (largest == null) 0 else Math.addExact(largest, 1)
    prune()
  }

  /** Records are redacted before disk writes. Long records may span multiple UTF-8 log files. */
  @Synchronized
  fun append(channel: String, text: String) {
    ensureDirectory()
    val safeChannel = channel.take(40).replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val bytes = "[$safeChannel] ${DiagnosticRedactor.redact(text)}\n".toByteArray(Charsets.UTF_8)
    var offset = 0
    var current = prune().lastOrNull()
    while (offset < bytes.size) {
      if (current == null || current.length() >= maxFileBytes) current = createFile()
      val available = (maxFileBytes - current.length()).toInt()
      var count = minOf(available, bytes.size - offset)
      // Never cut a UTF-8 code point between files, including when only 1-3 bytes remain.
      while (count > 0 && offset + count < bytes.size && isContinuation(bytes[offset + count])) {
        count--
      }
      if (count == 0) {
        current = createFile()
        continue
      }
      Files.newOutputStream(
          current.toPath(),
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND,
          LinkOption.NOFOLLOW_LINKS,
        )
        .use { it.write(bytes, offset, count) }
      offset += count
    }
  }

  /** Returns an ordered, bounded copy while excluding non-log files and symbolic links. */
  @Synchronized
  fun snapshot(): Map<String, ByteArray> {
    ensureDirectory()
    val snapshot = linkedMapOf<String, ByteArray>()
    for (file in prune()) {
      Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
        val output = ByteArrayOutputStream(minOf(file.length(), 8192L).toInt())
        val buffer = ByteArray(8192)
        var remaining = maxFileBytes
        while (remaining > 0) {
          val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
          if (count < 0) break
          output.write(buffer, 0, count)
          remaining -= count
        }
        snapshot[file.name] = output.toByteArray()
      }
    }
    return snapshot
  }

  @Synchronized
  fun clear() {
    ensureDirectory()
    for (file in entries()) {
      if (!Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        Files.deleteIfExists(file.toPath())
      }
    }
  }

  @Synchronized
  fun sizeBytes(): Long {
    ensureDirectory()
    return prune().sumOf { it.length() }
  }

  /** Forces retained logs to disk after a fatal event, without requiring the capture worker. */
  @Synchronized
  fun sync() {
    ensureDirectory()
    for (file in prune()) {
      FileChannel.open(file.toPath(), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use {
        it.force(true)
      }
    }
  }

  private fun ensureDirectory() {
    val path = directory.toPath()
    if (Files.isSymbolicLink(path)) throw IOException("Diagnostics directory must not be a symlink")
    Files.createDirectories(path)
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw IOException("Diagnostics path is not a directory")
    }
  }

  private fun entries(): List<File> =
    directory.listFiles()?.filter { ownedName.matches(it.name) }?.sortedBy { it.name }
      ?: throw IOException("Unable to list diagnostics directory")

  private fun sequence(file: File): Long? =
    ownedName.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()

  private fun prune(): List<File> {
    val files =
      entries()
        .filter {
          Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) && sequence(it) != null
        }
        .filter { file ->
          // A reduced retention setting must also bound files from a previous app process.
          if (file.length() > maxFileBytes) {
            Files.delete(file.toPath())
            false
          } else true
        }
    for (file in files.take((files.size - maxFiles).coerceAtLeast(0))) {
      Files.delete(file.toPath())
    }
    return files.takeLast(maxFiles)
  }

  private fun createFile(): File {
    val file = File(directory, String.format(Locale.ROOT, "diagnostic-%020d.log", nextSequence))
    nextSequence = Math.addExact(nextSequence, 1)
    Files.createFile(file.toPath())
    prune()
    return file
  }

  private fun isContinuation(byte: Byte): Boolean = byte.toInt() and 0xc0 == 0x80
}
