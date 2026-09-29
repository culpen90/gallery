package com.google.ai.edge.gallery.security

import androidx.datastore.core.Serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EncryptedSerializerTest {
  private val cipher = EnvelopeEncryption { key }
  private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
  private var delegateReads = 0
  private val delegate = object : Serializer<String> {
    override val defaultValue = ""
    override suspend fun readFrom(input: InputStream): String {
      delegateReads++
      return input.readBytes().toString(Charsets.UTF_8)
    }
    override suspend fun writeTo(t: String, output: OutputStream) { output.write(t.toByteArray()) }
  }
  private val serializer = EncryptedSerializer(delegate, "test-store", cipher::encrypt, cipher::decrypt)

  @Test fun persistsCiphertextAndReadsAuthenticatedContent() = runBlocking {
    val output = ByteArrayOutputStream()
    serializer.writeTo("sensitive history", output)
    assertFalse(output.toByteArray().toString(Charsets.UTF_8).contains("sensitive history"))
    assertEquals("sensitive history", serializer.readFrom(ByteArrayInputStream(output.toByteArray())))
  }

  @Test fun plaintextAndTamperedStorageNeverReachProtoParser() = runBlocking {
    val envelope = cipher.encrypt("sensitive history".toByteArray(), "test-store")
    envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
    for (bytes in listOf("plaintext".toByteArray(), envelope)) {
      try { serializer.readFrom(ByteArrayInputStream(bytes)); fail("Corrupt storage accepted") }
      catch (_: IOException) { }
    }
    assertEquals(0, delegateReads)
  }
}
