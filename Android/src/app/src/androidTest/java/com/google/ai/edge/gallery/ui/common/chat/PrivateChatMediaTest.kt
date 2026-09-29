/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.customtasks.scrapbook.CutoutMediaStorage
import com.google.ai.edge.gallery.customtasks.scrapbook.CUTOUT_COLLECTION_BASE_DIR
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic media only: never enumerates, reads, or deletes the user's existing recordings. */
@RunWith(AndroidJUnit4::class)
class PrivateChatMediaTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun encryptedRecordingRoundTripKeepsAudioAndIntent() = runBlocking {
    val pcm = "synthetic private recording ${UUID.randomUUID()}".toByteArray()
    val session = "test-${UUID.randomUUID()}"
    val files = mutableListOf<File>()
    try {
      for (mode in AudioInputMode.entries) {
        val request = AudioInputRequest(mode, "Answer in Spanish.")
        val original = ChatMessageAudioClip(pcm, 16000, ChatSide.USER, audioInputRequest = request)
        val saved = checkNotNull(ChatMessageMapper.serializeMessage(original, session, context))
        val file = File(saved.audioClipsList.single().filePath)
        files.add(file)
        assertTrue(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        assertTrue(file.name.endsWith(".enc"))
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(pcm.toString(Charsets.UTF_8)))
        val restored = ChatMessageMapper.deserializeProtoMessages(listOf(saved), context = context)
          .single() as ChatMessageAudioClip
        assertArrayEquals(pcm, restored.audioData)
        assertEquals(16000, restored.sampleRate)
        assertEquals(request, restored.audioInputRequest)
      }
    } finally { files.forEach { it.delete() } }
  }

  @Test
  fun legacyRecordingMigratesAndTamperingNeverSelectsPlaintext() {
    val name = "audio_test-${UUID.randomUUID()}.pcm"
    val legacy = File(context.cacheDir, name)
    val encrypted = File(ChatMediaStorage.directory(context), "$name.enc")
    val pcm = "synthetic legacy recording ${UUID.randomUUID()}".toByteArray()
    try {
      legacy.writeBytes(pcm)
      val (file, restored) = ChatMediaStorage.read(context, legacy.absolutePath, audio = true)
      assertEquals(encrypted.canonicalPath, file.canonicalPath)
      assertArrayEquals(pcm, restored)
      assertFalse(legacy.exists())
      val envelope = encrypted.readBytes()
      envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
      encrypted.writeBytes(envelope)
      legacy.writeText("must never be selected")
      assertThrows(java.security.GeneralSecurityException::class.java) {
        ChatMediaStorage.read(context, legacy.absolutePath, audio = true)
      }
      assertTrue(legacy.exists())
    } finally { legacy.delete(); encrypted.delete() }
  }

  @Test
  fun savedMediaCannotReferenceArbitraryFiles() {
    val external = File(context.filesDir, "audio_test-${UUID.randomUUID()}.pcm")
    try {
      external.writeText("synthetic unrelated file")
      assertThrows(java.io.IOException::class.java) {
        ChatMediaStorage.read(context, external.absolutePath, audio = true)
      }
      assertTrue(external.exists())
    } finally { external.delete() }
  }

  @Test
  fun legacyCutoutMovesFromExternalStorageToEncryptedInternalStorage() {
    val id = "test-${UUID.randomUUID()}"
    val encrypted = CutoutMediaStorage.file(context, id, original = false)
    val externalRoot = checkNotNull(context.getExternalFilesDir(null))
    val legacyDirectory = File(externalRoot, CUTOUT_COLLECTION_BASE_DIR).apply { mkdirs() }
    val legacy = File(legacyDirectory, "$id.png")
    val bytes = "synthetic private cutout ${UUID.randomUUID()}".toByteArray()
    try {
      legacy.writeBytes(bytes)
      assertArrayEquals(bytes, CutoutMediaStorage.read(context, encrypted))
      assertFalse(legacy.exists())
      assertTrue(encrypted.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
      assertFalse(encrypted.readBytes().toString(Charsets.UTF_8).contains(bytes.toString(Charsets.UTF_8)))
    } finally { legacy.delete(); encrypted.delete() }
  }
}
