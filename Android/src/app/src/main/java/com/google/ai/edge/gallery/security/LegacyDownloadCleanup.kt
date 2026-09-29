package com.google.ai.edge.gallery.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

/** Retired WorkManager inputs contained plaintext OAuth tokens. Remove them on upgrade. */
object LegacyDownloadCleanup {
  @Synchronized
  fun migrate(context: Context) {
    val marker = AtomicFile(File(context.noBackupFilesDir, "download_credentials_migrated.enc"))
    val purpose = "migration/download-credentials/v1"
    if (marker.baseFile.exists()) {
      check(PrivateDataEncryption.decrypt(marker.readFully(), purpose).contentEquals(byteArrayOf(1)))
      return
    }
    // Downloads are the app's only WorkManager jobs. Interrupted downloads can be started again.
    // Use the supported scheduler APIs; do not rewrite its internal serialized input schema.
    val manager = WorkManager.getInstance(context)
    manager.cancelAllWork().result.get(30, TimeUnit.SECONDS)
    manager.pruneWork().result.get(30, TimeUnit.SECONDS)
    for (database in listOf(
      File(context.noBackupFilesDir, "androidx.work.workdb"),
      context.getDatabasePath("androidx.work.workdb"),
    )) {
      if (!database.exists()) continue
      SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
        db.execSQL("VACUUM")
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
          check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Credential cleanup is busy; retry" }
        }
      }
    }
    val output = marker.startWrite()
    try {
      output.write(PrivateDataEncryption.encrypt(byteArrayOf(1), purpose))
      marker.finishWrite(output)
    } catch (failure: Throwable) {
      marker.failWrite(output)
      throw failure
    }
  }
}
