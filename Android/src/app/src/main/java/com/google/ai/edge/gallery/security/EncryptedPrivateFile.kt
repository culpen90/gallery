/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.security

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Authenticated private files. A failed key lookup or authentication never selects plaintext. */
object EncryptedPrivateFile {
  private const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024
  private val migrationLock = Any()

  fun write(file: File, plaintext: ByteArray, purpose: String) {
    if (plaintext.size > DEFAULT_MAX_BYTES) throw IOException("Private file exceeds its size limit")
    writeEnvelope(file, PrivateDataEncryption.encrypt(plaintext, purpose))
  }

  fun read(file: File, purpose: String, maxBytes: Long = DEFAULT_MAX_BYTES): ByteArray {
    val envelope = readBounded(file, maxBytes + 4096)
    val plaintext = PrivateDataEncryption.decrypt(envelope, purpose)
    if (plaintext.size > maxBytes) {
      plaintext.fill(0)
      throw IOException("Private file exceeds its size limit")
    }
    return plaintext
  }

  /** Validates the encrypted replacement before deleting a legacy app-owned plaintext file. */
  fun migrate(
    legacy: File,
    encrypted: File,
    purpose: String,
    maxBytes: Long = DEFAULT_MAX_BYTES,
  ): File = synchronized(migrationLock) {
    require(legacy.absolutePath != encrypted.absolutePath)
    if (encrypted.exists()) {
      // An existing envelope is authoritative, including when it cannot be authenticated.
      read(encrypted, purpose, maxBytes).fill(0)
    } else if (legacy.exists()) {
      val plaintext = readBounded(legacy, maxBytes)
      try {
        write(encrypted, plaintext, purpose)
        val verified = read(encrypted, purpose, maxBytes)
        try {
          if (!MessageDigest.isEqual(plaintext, verified)) {
            throw IOException("Private file migration verification failed")
          }
        } finally {
          verified.fill(0)
        }
      } finally {
        plaintext.fill(0)
      }
    }
    if (legacy.exists()) {
      checkRegularFile(legacy)
      if (!legacy.delete()) throw IOException("Cannot remove legacy private file")
    }
    encrypted
  }

  /** Atomically publishes ciphertext; interrupted writes never leave a plaintext temporary file. */
  internal fun writeEnvelope(file: File, envelope: ByteArray) {
    checkNoSymlinks(file)
    val parent = file.absoluteFile.parentFile ?: throw IOException("Private file has no directory")
    Files.createDirectories(parent.toPath())
    if (!Files.isDirectory(parent.toPath(), LinkOption.NOFOLLOW_LINKS)) {
      throw IOException("Private file parent is not a directory")
    }
    val temporary = File.createTempFile(".encrypted-", ".tmp", parent)
    try {
      java.io.FileOutputStream(temporary).use { output ->
        output.write(envelope)
        output.fd.sync()
      }
      try {
        Files.move(
          temporary.toPath(), file.toPath(),
          StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      temporary.delete()
    }
  }

  internal fun readBounded(file: File, maxBytes: Long): ByteArray {
    require(maxBytes in 0..Int.MAX_VALUE.toLong())
    checkRegularFile(file)
    if (file.length() > maxBytes) throw IOException("Private file exceeds its size limit")
    return Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
      val bytes = java.io.ByteArrayOutputStream(minOf(file.length(), 8192L).toInt())
      val buffer = ByteArray(8192)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (bytes.size().toLong() + count > maxBytes) {
          throw IOException("Private file exceeds its size limit")
        }
        bytes.write(buffer, 0, count)
      }
      bytes.toByteArray()
    }
  }

  private fun checkRegularFile(file: File) {
    checkNoSymlinks(file)
    if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
      throw IOException("Private file is missing or is not a regular file")
    }
  }

  private fun checkNoSymlinks(file: File) {
    // Android's OS-owned /data/user/0 and macOS's /var can themselves be symlinks. The
    // callers constrain files to the app's named private directory; reject its leaf and parent.
    for (current in listOfNotNull(file.absoluteFile, file.absoluteFile.parentFile)) {
      if (Files.isSymbolicLink(current.toPath())) throw IOException("Private file cannot be a symlink")
    }
  }
}
