package com.google.ai.edge.gallery.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Test

class PrivateImageProviderTest {
  @Test fun sharesPngBytesWithoutWritingPlaintextImage() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    val uri = ProtectedImageSharing.write(context, bitmap, "synthetic-provider-test.png")
    val file = File(File(context.cacheDir, "images"), uri.lastPathSegment!!)
    try {
      assertEquals("image/png", context.contentResolver.getType(uri))
      val png = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
      assertEquals(2, BitmapFactory.decodeByteArray(png, 0, png.size).width)
      assertFalse(file.readBytes().contentEquals(png))
      try {
        context.contentResolver.openFileDescriptor(uri, "rw")?.close()
        fail("Encrypted shared image accepted a write handle")
      } catch (_: FileNotFoundException) { }
    } finally { file.delete(); bitmap.recycle() }
  }
}
