package com.google.ai.edge.gallery.security

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** The selected recipient gets image bytes; our temporary sharing cache keeps ciphertext. */
object ProtectedImageSharing {
  fun migrateLegacyCache(context: Context) {
    val directory = File(context.cacheDir, "images")
    // Camera staging uses temp_image.jpg and needs the native writable file descriptor.
    for (legacy in directory.listFiles().orEmpty()) {
      if (legacy.isFile && legacy.name.endsWith(".png")) {
        EncryptedPrivateFile.migrate(legacy, File(directory, "${legacy.name}.enc"), "shared-image/${legacy.name}")
      }
    }
  }

  fun write(context: Context, bitmap: Bitmap, fileName: String): Uri {
    val safeName = fileName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
    val name = "${safeName}_${UUID.randomUUID()}.png"
    val file = File(File(context.cacheDir, "images"), "$name.enc")
    val output = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Cannot encode shared image" }
    val plaintext = output.toByteArray()
    try { EncryptedPrivateFile.write(file, plaintext, "shared-image/$name") }
    finally { plaintext.fill(0) }
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
  }
}
