package com.google.ai.edge.gallery.security

import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class PrivateDataEncryptionTest {
  private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
  private val cipher = EnvelopeEncryption { key }

  @Test fun roundTripsPrivateDataAndEmptyRecords() {
    for (plaintext in listOf(byteArrayOf(), "private chat and hf_token".toByteArray(), ByteArray(1024 * 1024) { (it % 256).toByte() })) {
      val envelope = cipher.encrypt(plaintext, "user-data")
      assertArrayEquals(plaintext, cipher.decrypt(envelope, "user-data"))
      assertFalse(envelope.contentEquals(plaintext))
    }
  }

  @Test fun encryptingSameContentUsesFreshKeysAndNonces() {
    val plaintext = "same private text".toByteArray()
    assertFalse(cipher.encrypt(plaintext, "chat").contentEquals(cipher.encrypt(plaintext, "chat")))
  }

  @Test fun everyByteOfEnvelopeIsAuthenticated() {
    val envelope = cipher.encrypt("secret".toByteArray(), "chat")
    for (index in envelope.indices) {
      val tampered = envelope.copyOf()
      tampered[index] = (tampered[index].toInt() xor 1).toByte()
      rejects { cipher.decrypt(tampered, "chat") }
    }
  }

  @Test fun rejectsOtherPurposeAndOtherDeviceKey() {
    val envelope = cipher.encrypt("secret".toByteArray(), "chat")
    rejects { cipher.decrypt(envelope, "credentials") }
    val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    rejects { EnvelopeEncryption { otherKey }.decrypt(envelope, "chat") }
  }

  @Test fun rejectsPlaintextTruncationAndAppendedBytes() {
    rejects { cipher.decrypt("secret plaintext".toByteArray(), "chat") }
    val envelope = cipher.encrypt("secret".toByteArray(), "chat")
    for (length in 0 until envelope.size) rejects { cipher.decrypt(envelope.copyOf(length), "chat") }
    rejects { cipher.decrypt(envelope + byteArrayOf(0), "chat") }
  }

  private fun rejects(operation: () -> Unit) {
    try { operation(); fail("Unauthenticated data was accepted") }
    catch (_: GeneralSecurityException) { }
  }
}
