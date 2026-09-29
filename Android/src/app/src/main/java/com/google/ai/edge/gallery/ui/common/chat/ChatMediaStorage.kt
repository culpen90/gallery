/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import android.content.Context
import com.google.ai.edge.gallery.security.EncryptedPrivateFile
import java.io.File
import java.io.IOException

/** Keeps saved chat media out of cache eviction and Android backup. Plaintext is memory-only. */
object ChatMediaStorage {
  private val ownedName = Regex("(?:img|audio)_[A-Za-z0-9_-]+\\.(?:png|pcm)")

  fun directory(context: Context): File = File(context.noBackupFilesDir, "chat-media")

  fun migrateLegacyFiles(context: Context) {
    for (legacy in context.cacheDir.listFiles().orEmpty()) {
      if (ownedName.matches(legacy.name)) {
        EncryptedPrivateFile.migrate(
          legacy, File(directory(context), "${legacy.name}.enc"), purpose(legacy.name),
        )
      }
    }
  }

  fun write(context: Context, name: String, bytes: ByteArray): File {
    require(ownedName.matches(name)) { "Invalid chat media name" }
    val file = File(directory(context), "$name.enc")
    EncryptedPrivateFile.write(file, bytes, purpose(name))
    return file
  }

  /** Only app-owned historical cache paths are accepted during plaintext migration. */
  fun resolve(context: Context, path: String, audio: Boolean): File {
    val referenced = File(path)
    val name = referenced.name.removeSuffix(".enc")
    if (!ownedName.matches(name) || (audio != name.startsWith("audio_"))) {
      throw IOException("Invalid saved chat media path")
    }
    val encrypted = File(directory(context), "$name.enc")
    val legacy = File(context.cacheDir, name)
    val referencePath = referenced.canonicalPath
    if (referencePath != encrypted.canonicalPath && referencePath != legacy.canonicalPath) {
      throw IOException("Saved chat media is outside private app storage")
    }
    return EncryptedPrivateFile.migrate(legacy, encrypted, purpose(name))
  }

  fun read(context: Context, path: String, audio: Boolean): Pair<File, ByteArray> {
    val file = resolve(context, path, audio)
    return file to EncryptedPrivateFile.read(file, purpose(file.name.removeSuffix(".enc")))
  }

  private fun purpose(name: String): String = "chat-media/$name"
}
