/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.customtasks.scrapbook

import android.content.Context
import com.google.ai.edge.gallery.security.EncryptedPrivateFile
import java.io.File
import java.io.IOException

/** User cutouts are encrypted in internal, non-backed-up storage rather than external files. */
object CutoutMediaStorage {
  private val ownedName = Regex("[A-Za-z0-9_-]+\\.png")

  fun directory(context: Context): File = File(context.noBackupFilesDir, "cutout-media")

  fun file(context: Context, id: String, original: Boolean): File {
    require(Regex("[A-Za-z0-9_-]+").matches(id)) { "Invalid cutout identifier" }
    val name = if (original) "${id}_original.png" else "$id.png"
    return File(directory(context), "$name.enc")
  }

  fun migrateLegacyFiles(context: Context) {
    val parent = context.getExternalFilesDir(null) ?: return
    val legacyDirectory = File(parent, CUTOUT_COLLECTION_BASE_DIR)
    for (legacy in legacyDirectory.listFiles().orEmpty()) {
      if (ownedName.matches(legacy.name)) {
        EncryptedPrivateFile.migrate(
          legacy, File(directory(context), "${legacy.name}.enc"), purpose(legacy.name),
        )
      }
    }
  }

  fun read(context: Context, encrypted: File): ByteArray {
    val name = validate(context, encrypted)
    legacy(context, name)?.let { EncryptedPrivateFile.migrate(it, encrypted, purpose(name)) }
    return EncryptedPrivateFile.read(encrypted, purpose(name))
  }

  fun write(context: Context, encrypted: File, bytes: ByteArray) {
    val name = validate(context, encrypted)
    EncryptedPrivateFile.write(encrypted, bytes, purpose(name))
    // Also remove any obsolete plaintext version after authenticating the new envelope.
    legacy(context, name)?.let { EncryptedPrivateFile.migrate(it, encrypted, purpose(name)) }
  }

  fun delete(context: Context, encrypted: File) {
    val name = validate(context, encrypted)
    for (file in listOfNotNull(encrypted, legacy(context, name))) {
      if (file.exists() && !file.delete()) throw IOException("Cannot remove private cutout")
    }
  }

  private fun legacy(context: Context, name: String): File? =
    context.getExternalFilesDir(null)?.let { File(File(it, CUTOUT_COLLECTION_BASE_DIR), name) }

  private fun validate(context: Context, file: File): String {
    val name = file.name.removeSuffix(".enc")
    if (!ownedName.matches(name) || file.canonicalPath != File(directory(context), "$name.enc").canonicalPath) {
      throw IOException("Invalid private cutout path")
    }
    return name
  }

  private fun purpose(name: String): String = "cutout-media/$name"
}
