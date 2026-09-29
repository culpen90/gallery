package com.google.ai.edge.gallery.security

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.FileNotFoundException

/** Sends explicitly shared images through a pipe, with no decrypted file on disk. */
class PrivateImageProvider : FileProvider() {
  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (uri.lastPathSegment?.endsWith(".png.enc") != true) return super.openFile(uri, mode) ?: throw FileNotFoundException("Image is unavailable")
    if (mode != "r") throw FileNotFoundException("Shared private images are read only")
    if (!readPermits.tryAcquire()) throw FileNotFoundException("Private image sharing is busy")
    try {
      val envelope = ParcelFileDescriptor.AutoCloseInputStream(super.openFile(uri, "r") ?: throw FileNotFoundException("Image is unavailable")).use { input ->
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          if (result.size().toLong() + count > 64L * 1024 * 1024) {
            throw FileNotFoundException("Shared image exceeds size limit")
          }
          result.write(buffer, 0, count)
        }
        result.toByteArray()
      }
      val name = uri.lastPathSegment!!.removeSuffix(".enc")
      val plaintext = try { PrivateDataEncryption.decrypt(envelope, "shared-image/$name") }
      catch (_: Exception) { throw FileNotFoundException("Private image is unavailable") }
      val pipe = try { ParcelFileDescriptor.createReliablePipe() }
      catch (failure: Exception) { plaintext.fill(0); throw failure }
      try {
        val flags = Os.fcntlInt(pipe[1].fileDescriptor, OsConstants.F_GETFL, 0)
        Os.fcntlInt(pipe[1].fileDescriptor, OsConstants.F_SETFL, flags or OsConstants.O_NONBLOCK)
        writers.execute {
          try {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            var offset = 0
            while (offset < plaintext.size) {
              if (SystemClock.elapsedRealtime() >= deadline) throw java.io.IOException("Image read timed out")
              try {
                offset += Os.write(pipe[1].fileDescriptor, plaintext, offset, minOf(8192, plaintext.size - offset))
              } catch (busy: ErrnoException) {
                if (busy.errno != OsConstants.EAGAIN) throw busy
                Thread.sleep(10)
              }
            }
          } catch (_: Exception) {
            runCatching { pipe[1].closeWithError("Private image read ended") }
          } finally {
            plaintext.fill(0)
            runCatching { pipe[1].close() }
            readPermits.release()
          }
        }
      } catch (failure: Exception) {
        plaintext.fill(0)
        runCatching { pipe[0].close() }
        runCatching { pipe[1].close() }
        throw failure
      }
      return pipe[0]
    } catch (failure: Exception) {
      readPermits.release()
      throw failure
    }
  }

  companion object {
    private val readPermits = Semaphore(2)
    private val writers = Executors.newFixedThreadPool(2) { task ->
      Thread(task, "GalleryImageShare").apply { isDaemon = true }
    }
  }

  override fun getType(uri: Uri): String? =
    if (uri.lastPathSegment?.endsWith(".png.enc") == true) "image/png" else super.getType(uri)

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
    if (uri.lastPathSegment?.endsWith(".png.enc") != true) {
      return super.query(uri, projection, selection, selectionArgs, sortOrder)
    }
    // Validate the URI against FileProvider's scoped path map before returning metadata.
    super.query(uri, projection, selection, selectionArgs, sortOrder).close()
    val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
      .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }.toTypedArray()
    return MatrixCursor(columns, 1).apply {
      addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) uri.lastPathSegment!!.removeSuffix(".enc") else null })
    }
  }
}
