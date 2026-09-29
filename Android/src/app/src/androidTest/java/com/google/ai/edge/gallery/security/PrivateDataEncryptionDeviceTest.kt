package com.google.ai.edge.gallery.security

import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.datastore.core.Serializer
import androidx.test.platform.app.InstrumentationRegistry
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Uses synthetic content only; never reads the owner's saved conversations or credentials. */
class PrivateDataEncryptionDeviceTest {
  @Test fun hardwareKeyRoundTripAndTamperRejection() {
    val plaintext = "synthetic-security-test".toByteArray()
    val envelope = PrivateDataEncryption.encrypt(plaintext, "instrumentation-test")
    assertArrayEquals(plaintext, PrivateDataEncryption.decrypt(envelope, "instrumentation-test"))
    val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      .getKey("gallery_private_storage_v1", null) as SecretKey
    val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
      .getKeySpec(key, KeyInfo::class.java) as KeyInfo
    assertTrue(info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX ||
      info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT)
    envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
    try { PrivateDataEncryption.decrypt(envelope, "instrumentation-test"); fail("Tampering accepted") }
    catch (_: GeneralSecurityException) { }
  }

  @Test fun migratesSyntheticLegacyDataAndRemovesPlaintext() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val name = "security_test_${System.nanoTime()}.pb"
    val legacy = java.io.File(context.cacheDir, name)
    val synthetic = "synthetic-legacy-private-record"
    legacy.writeText(synthetic)
    val serializer = object : Serializer<String> {
      override val defaultValue = ""
      override suspend fun readFrom(input: InputStream) = input.readBytes().toString(Charsets.UTF_8)
      override suspend fun writeTo(t: String, output: OutputStream) { output.write(t.toByteArray()) }
    }
    val store = EncryptedDataStore.create(context, legacy, serializer)
    assertEquals(synthetic, store.data.first())
    assertFalse(legacy.exists())
    val encrypted = java.io.File(context.noBackupFilesDir, "private_datastore/$name.enc")
    assertTrue(encrypted.exists())
    assertFalse(encrypted.readBytes().toString(Charsets.UTF_8).contains(synthetic))
    assertTrue(encrypted.delete())
  }
}
