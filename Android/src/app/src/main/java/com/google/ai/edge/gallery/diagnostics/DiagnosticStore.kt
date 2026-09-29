// Modified for the Gallery Android fork (Beta 5).
/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import com.google.ai.edge.gallery.security.EncryptedPrivateFile
import com.google.ai.edge.gallery.security.PrivateDataEncryption
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.Locale

/** Bounded, synchronized diagnostics. Only authenticated ciphertext is ever written to disk. */
class DiagnosticStore(
  private val directory: File,
  private val maxFileBytes: Long = 64L * 1024,
  private val maxFiles: Int = 320,
  private val encrypt: (ByteArray, String) -> ByteArray = PrivateDataEncryption::encrypt,
  private val decrypt: (ByteArray, String) -> ByteArray = PrivateDataEncryption::decrypt,
) {
  private val ownedName = Regex("diagnostic-([0-9]{20})\\.log\\.enc")
  private val legacyName = Regex("diagnostic-([0-9]{20})\\.log")
  private var nextSequence = 0L

  init {
    require(maxFileBytes in 4..Int.MAX_VALUE.toLong() - 4096) { "A log file must fit a UTF-8 character" }
    require(maxFiles > 0) { "At least one log file is required" }
    ensureDirectory()
    val largest = directory.listFiles().orEmpty().mapNotNull { file ->
      (ownedName.matchEntire(file.name) ?: legacyName.matchEntire(file.name))
        ?.groupValues?.get(1)?.toLongOrNull()
    }.maxOrNull()
    nextSequence = if (largest == null) 0 else Math.addExact(largest, 1)
  }

  /** Runs after app authentication; old plaintext is deleted only after verified atomic migration. */
  @Synchronized
  fun migrateLegacyLogs() {
    ensureDirectory()
    for (legacy in directory.listFiles().orEmpty().filter { legacyName.matches(it.name) }.sortedBy { it.name }) {
      if (!Files.isRegularFile(legacy.toPath(), LinkOption.NOFOLLOW_LINKS)) continue
      val destination = File(directory, "${legacy.name}.enc")
      if (destination.exists()) {
        read(destination).fill(0)
      } else {
        val plaintext = EncryptedPrivateFile.readBounded(legacy, LEGACY_MAX_BYTES)
        try {
          write(destination, plaintext)
          val verified = read(destination)
          try {
            if (!java.security.MessageDigest.isEqual(plaintext, verified)) {
              throw IOException("Diagnostics migration verification failed")
            }
          } finally { verified.fill(0) }
        } finally { plaintext.fill(0) }
      }
      if (!legacy.delete()) throw IOException("Cannot remove legacy diagnostics")
    }
    prune()
  }

  @Synchronized
  fun append(channel: String, text: String) {
    val bytes = encode(channel, text)
    try { appendBytes(bytes) } finally { bytes.fill(0) }
  }

  /** One encrypted write per bounded batch avoids a hardware key operation for every log line. */
  @Synchronized
  fun appendAll(records: List<Pair<String, String>>) {
    val buffer = ByteArray(minOf(maxFileBytes, 64L * 1024).toInt())
    var count = 0
    fun flush() {
      if (count == 0) return
      val bytes = buffer.copyOf(count)
      try { appendBytes(bytes) } finally {
        bytes.fill(0)
        buffer.fill(0)
        count = 0
      }
    }
    try {
      for ((channel, text) in records) {
        val bytes = encode(channel, text)
        try {
          if (bytes.size >= buffer.size) {
            flush()
            // appendBytes preserves UTF-8 boundaries for a single oversized record.
            appendBytes(bytes)
          } else {
            if (count + bytes.size > buffer.size) flush()
            bytes.copyInto(buffer, count)
            count += bytes.size
          }
        } finally { bytes.fill(0) }
      }
      flush()
    } finally { buffer.fill(0) }
  }

  private fun encode(channel: String, text: String): ByteArray {
    val safeChannel = channel.take(40).replace(Regex("[^A-Za-z0-9_.-]"), "_")
    return "[$safeChannel] ${DiagnosticRedactor.redact(text)}\n".toByteArray(Charsets.UTF_8)
  }

  private fun appendBytes(bytes: ByteArray) {
    ensureDirectory()
    var offset = 0
    var current = prune().lastOrNull()
    var retained = current?.let(::read) ?: ByteArray(0)
    try {
      while (offset < bytes.size) {
        if (current == null || retained.size >= maxFileBytes) {
          retained.fill(0)
          retained = ByteArray(0)
          current = newFile()
        }
        val available = (maxFileBytes - retained.size).toInt()
        var count = minOf(available, bytes.size - offset)
        while (count > 0 && offset + count < bytes.size && isContinuation(bytes[offset + count])) count--
        if (count == 0) {
          retained.fill(0)
          retained = ByteArray(0)
          current = newFile()
          continue
        }
        val updated = ByteArray(retained.size + count)
        retained.copyInto(updated)
        bytes.copyInto(updated, retained.size, offset, offset + count)
        retained.fill(0)
        retained = updated
        write(current, retained)
        offset += count
      }
      prune()
    } finally { retained.fill(0) }
  }

  /** Decryption/authentication failures abort the snapshot; no legacy plaintext fallback exists. */
  @Synchronized
  fun snapshot(): Map<String, ByteArray> {
    ensureDirectory()
    return prune().associateTo(linkedMapOf()) { file -> file.name.removeSuffix(".enc") to read(file) }
  }

  @Synchronized
  fun clear() {
    ensureDirectory()
    for (file in directory.listFiles().orEmpty()) {
      if ((ownedName.matches(file.name) || legacyName.matches(file.name)) &&
        !Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        Files.deleteIfExists(file.toPath())
      }
    }
  }

  @Synchronized
  fun sizeBytes(): Long = prune().sumOf { it.length() }

  @Synchronized
  fun sync() {
    for (file in entries()) {
      FileChannel.open(file.toPath(), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { it.force(true) }
    }
  }

  private fun read(file: File): ByteArray {
    val envelope = EncryptedPrivateFile.readBounded(file, maxOf(maxFileBytes, LEGACY_MAX_BYTES) + 4096)
    val bytes = decrypt(envelope, "diagnostics/${file.name}")
    if (bytes.size > maxOf(maxFileBytes, LEGACY_MAX_BYTES)) {
      bytes.fill(0)
      throw IOException("Diagnostics file exceeds its size limit")
    }
    return bytes
  }

  private fun write(file: File, bytes: ByteArray) {
    EncryptedPrivateFile.writeEnvelope(file, encrypt(bytes, "diagnostics/${file.name}"))
  }

  private fun ensureDirectory() {
    if (Files.isSymbolicLink(directory.toPath())) throw IOException("Diagnostics directory must not be a symlink")
    Files.createDirectories(directory.toPath())
    if (!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
      throw IOException("Diagnostics path is not a directory")
    }
  }

  private fun entries(): List<File> =
    directory.listFiles()?.filter {
      ownedName.matches(it.name) && Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS)
    }?.sortedBy { it.name } ?: throw IOException("Unable to list diagnostics directory")

  private fun prune(): List<File> {
    ensureDirectory()
    val files = entries()
    var bytes = 0L
    val retained = mutableListOf<File>()
    // Whole-file envelopes authenticate truncation as well as bytes. Tiny chunks also keep
    // repeated encryptions bounded while an opt-in native logger is busy.
    val byteLimit = Math.multiplyExact(maxFileBytes + ENVELOPE_OVERHEAD, maxFiles.toLong())
    for (file in files.asReversed()) {
      val size = file.length()
      if (size < ENVELOPE_OVERHEAD) throw IOException("Invalid diagnostics envelope")
      if (retained.size >= maxFiles || bytes + size > byteLimit) Files.delete(file.toPath())
      else { retained.add(file); bytes += size }
    }
    return retained.asReversed()
  }

  private fun newFile(): File {
    val file = File(directory, String.format(Locale.ROOT, "diagnostic-%020d.log.enc", nextSequence))
    nextSequence = Math.addExact(nextSequence, 1)
    return file
  }

  private fun isContinuation(byte: Byte): Boolean = byte.toInt() and 0xc0 == 0x80

  companion object {
    private const val LEGACY_MAX_BYTES = 2L * 1024 * 1024
    // v1 envelope: magic/version (5), two IVs (24), wrapped AES-256 key (48), data tag (16).
    private const val ENVELOPE_OVERHEAD = 93L
  }
}
