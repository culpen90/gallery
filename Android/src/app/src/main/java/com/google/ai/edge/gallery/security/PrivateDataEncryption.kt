package com.google.ai.edge.gallery.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated private storage. Key material stays in the device's secure hardware. */
object PrivateDataEncryption {
  private const val KEY_ALIAS = "gallery_private_storage_v1"
  private val encryption = EnvelopeEncryption { hardwareKey() }

  fun encrypt(plaintext: ByteArray, purpose: String): ByteArray = encryption.encrypt(plaintext, purpose)

  fun decrypt(envelope: ByteArray, purpose: String): ByteArray = encryption.decrypt(envelope, purpose)

  @Synchronized
  private fun hardwareKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
    val key = existing ?: try {
      generateKey(strongBox = true)
    } catch (_: StrongBoxUnavailableException) {
      generateKey(strongBox = false)
    }
    val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
      .getKeySpec(key, KeyInfo::class.java) as KeyInfo
    if (info.securityLevel != KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT &&
      info.securityLevel != KeyProperties.SECURITY_LEVEL_STRONGBOX) {
      throw GeneralSecurityException("Secure hardware storage is unavailable")
    }
    return key
  }

  private fun generateKey(strongBox: Boolean): SecretKey {
    val spec = KeyGenParameterSpec.Builder(
      KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    ).setKeySize(256)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setRandomizedEncryptionRequired(true)
      .setIsStrongBoxBacked(strongBox)
    // Android 12-14 have documented key-loss bugs for this flag. Android 15 fixes them.
    if (Build.VERSION.SDK_INT >= 35) spec.setUnlockedDeviceRequired(true)
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
      init(spec.build())
      generateKey()
    }
  }
}

/** A fresh AES-256 data key per record keeps bulk media work outside resource-limited StrongBox. */
internal class EnvelopeEncryption(private val wrappingKey: () -> SecretKey) {
  private val magic = byteArrayOf(0x47, 0x41, 0x4c, 0x56, 0x01)
  private val ivSize = 12
  private val wrappedKeySize = 32 + 16
  private val headerSize = magic.size + ivSize + wrappedKeySize + ivSize

  fun encrypt(plaintext: ByteArray, purpose: String): ByteArray {
    require(purpose.isNotBlank())
    val dataKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().encoded
    try {
      val keyCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, wrappingKey())
        updateAAD(keyContext(purpose))
      }
      val wrappedKey = keyCipher.doFinal(dataKey)
      check(keyCipher.iv.size == ivSize && wrappedKey.size == wrappedKeySize)
      val dataCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"))
      }
      check(dataCipher.iv.size == ivSize)
      val header = magic + keyCipher.iv + wrappedKey + dataCipher.iv
      dataCipher.updateAAD(dataContext(purpose, header))
      return header + dataCipher.doFinal(plaintext)
    } finally {
      dataKey.fill(0)
    }
  }

  fun decrypt(envelope: ByteArray, purpose: String): ByteArray {
    require(purpose.isNotBlank())
    if (envelope.size < headerSize + 16 || !envelope.take(magic.size).toByteArray().contentEquals(magic)) {
      throw GeneralSecurityException("Invalid encrypted private data")
    }
    val keyIvEnd = magic.size + ivSize
    val wrappedKeyEnd = keyIvEnd + wrappedKeySize
    val keyCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, envelope.copyOfRange(magic.size, keyIvEnd)))
      updateAAD(keyContext(purpose))
    }
    val dataKey = keyCipher.doFinal(envelope.copyOfRange(keyIvEnd, wrappedKeyEnd))
    try {
      if (dataKey.size != 32) throw GeneralSecurityException("Invalid private data key")
      return Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, envelope.copyOfRange(wrappedKeyEnd, headerSize)))
        updateAAD(dataContext(purpose, envelope.copyOfRange(0, headerSize)))
        doFinal(envelope, headerSize, envelope.size - headerSize)
      }
    } finally {
      dataKey.fill(0)
    }
  }

  private fun keyContext(purpose: String) = "gallery/wrapped-key/v1/$purpose".toByteArray(Charsets.UTF_8)
  private fun dataContext(purpose: String, header: ByteArray) =
    "gallery/private-data/v1/$purpose".toByteArray(Charsets.UTF_8) + byteArrayOf(0) + header
}
